# Architecture

This document describes the overall architecture of JSoftSIP, its module boundaries, design principles, and runtime characteristics.

## Module Responsibilities

### `core`

The heart of the application. Contains:

- Domain models: `SipAccount`, `CallLeg`, `CallSession`, `CallHistoryEntry`
- Service contracts: `AccountService`, `CallService`, `RegistrationService`, `SettingsService`, `HistoryService`
- SIP abstractions: `SipClient`, `SipCallListener`, `SipEventListener`
- Call state machine: `CallStateMachine` with immutable transition table
- Persistence layer: SQLite repositories for accounts, settings, and call history
- Encryption utilities: `AesGcmEncryptionService`, `MasterKeyManager`
- Video primitives: `VideoFrame`, `FramePipe`, `VideoQuality`

### `native-bridge`

Implements the concrete SIP backend using Baresip:

- `BaresipSipClient`: TCP control-channel client that translates high-level SIP operations into Baresip commands. `sendSimpleCommand` returns `false` instead of throwing when a command cannot reach a dead `ctrl_tcp` connection, so callers degrade gracefully
- `BaresipLauncher`: Manages the Baresip subprocess lifecycle
- `BaresipConfigService`: Reads, patches, and writes Baresip configuration files
- `BaresipSessionRestart`: Full session-restart operation wired into `BaresipConfigService` by `JSoftSipContext`. When a config apply fails, it restarts the process and reconnects ctrl_tcp, completes pending dial futures exceptionally (orphaned calls surface as `TERMINATED`), and re-provisions the previously registered accounts. The same single instance is shared with `BaresipSupervisor`, making it the one recovery primitive of the application
- `BaresipSupervisor`: Automatic crash recovery for the backend. Armed by `JSoftSipContext` after the initial launch; when `BaresipProcessManager` reports an unexpected process death (no preceding intentional stop), it runs a coalesced, bounded recovery cycle (3 attempts with exponential backoff) using `BaresipSessionRestart`. Intentional stops (settings apply, app shutdown) are suppressed by the intent flag in `BaresipProcessManager`; if all attempts fail, an ERROR is logged stating that the backend stays down until settings are applied manually
- `PactlDeviceLister`: Discovers audio sinks and sources via `pactl`
- `NativeFrameTransport` / `FramePipeAdapter`: Receives raw video frames from the custom `jvidisp` module and routes them into per-account `FramePipe` instances

### `ui`

JavaFX-based user interface:

- FXML controllers: `AccountsPaneController`, `ActiveCallsPaneController`, `HistoryPaneController`, `SettingsDialogController`, etc. The root `MainView.fxml` has no controller; sub-panes are wired independently via `fx:include`.
- Dialogs: `AccountDialog`, `DialerDialog`, `SettingsDialog`, plus the video call dialog built from `VideoCallDialogFactory`, `VideoCallDialogController`, and `VideoCallDialog.fxml`
- Custom modal dialogs: all alerts and confirmations use the custom system in `com.jsoftsip.ui.dialog` (`DialogType`, `DialogAction`, `DialogSpec` built via `DialogBuilder`, created by `DialogFactory`, shown by `DialogService`) instead of `javafx.scene.control.Alert`; dialogs are application-modal by default, with window helpers `StageHandle`, `WindowHandle`, and `DialerWindowManager`
- Internationalization: `I18n` resolves keys against `Language` (EN/ES/PT) using the bundles at `ui/src/main/resources/i18n/messages{,_es,_pt}.properties`; the persisted language preference is restored at startup (`JSoftSipApplication.showMainStage`) and changed from a selector in the Settings General tab; `LanguageCoverageTest` / `I18nTest` keep the bundles in sync
- Window management: `WindowGeometry`, `ShutdownCleanup`, `ExitConfirmationPolicy`, `ModalWindowTracker`
- Theming: `ThemeManager`, `ThemeType`, integration with AtlantaFX

### `launcher`

Application entry point and composition root:

- `JSoftSipApplication`: JavaFX `Application` subclass; handles startup, theme restoration, window geometry, and clean shutdown. Installs `FxExceptionHandler` as the global uncaught-exception handler (`Thread.setDefaultUncaughtExceptionHandler`) before any UI code runs, so exceptions escaping button handlers are logged and surfaced instead of silently killing the FX thread
- Startup splash: a `StartupSplash` is shown while `JSoftSipContext` builds asynchronously on a virtual thread; if startup fails, an error dialog is shown and the application exits
- `JSoftSipContext`: Builds and wires every service; resolves the SIP backend; starts Baresip and the video transport before the UI is shown
- `SipBackend`: Enum that selects `BARESIP` (production) or `MOCK` (testing) via the `jsoftsip.sip.backend` system property

### `packager`

Packaging-only module (`pom` packaging). Defines the `linux` Maven profile that:

1. Stages runtime dependencies into `target/mods`
2. Invokes `jpackage` to produce an app image with a trimmed JVM
3. Tars the app image into a `.tar.gz` distribution

## Design Principles

### Clean Architecture

The `core` module knows nothing about JavaFX or Baresip. All dependencies point inward:

- `ui` depends on `core`
- `native-bridge` depends on `core`
- `launcher` depends on `ui`, `core`, and `native-bridge`

### Dependency Inversion

High-level services depend on interfaces defined in `core` (`SipClient`, `AccountRepository`, `SettingsService`, etc.). Concrete implementations live in `native-bridge` or `core/infrastructure`.

### Service-Oriented Design

Each major subsystem exposes a narrow service interface. The `launcher` module acts as a manual composition root, instantiating and wiring services in `JSoftSipContext`.

### Repository Pattern

Data access is abstracted behind repository interfaces (`AccountRepository`, `CallHistoryRepository`, `SettingRepository`). The SQLite implementations are swappable without touching domain logic.

### Event-Driven Communication

UI updates are driven by events:

- `SipEvent` / `SipRegistrationEvent` for registration state changes
- `CallEvent` for call lifecycle updates
- `AccountStatusListener` for account status changes
- `CallListener` for active call list changes

Listeners use `CopyOnWriteArrayList` so mutations during iteration are safe.

### Event-Driven Call State Updates

All call commands (`endCall`, `answerCall`, `rejectCall`, `holdCall` and `resumeCall`) are sent to the backend as fire-and-forget requests. The local `CallLeg` state is only updated when the backend emits a `CallEvent` that is accepted by `CallStateMachine`. This means there is a brief window where the UI may reflect the user action before the service state has changed.

This design is intentional: the backend is the single source of truth for the call lifecycle, which avoids state divergence when commands are delayed, retried, or fail silently.

### Hold/Resume Policy

Hold and resume deserve a special note because they once used optimistic local transitions. They do not anymore:

- `holdCall` and `resumeCall` are pure command passthroughs in both `DefaultCallService` and `MockCallService`: no local transition, no listener notification from the command itself.
- There is **no timeout or reconciliation machinery**, intentionally. An optimistic HOLD with rollback-by-timeout was the root cause of legs stranded in eternal HOLD whenever baresip dropped the action without emitting an event. Driving the state exclusively from events makes a dropped command self-consistent: the leg simply stays where it was instead of entering a state the backend never confirmed.
- The hold confirmation is event-driven. After our own hold re-INVITE, baresip answers with an `ESTABLISHED` event whose remote audio direction is sendonly. `CallStateMachine.toTransition(currentState, eventState, remoteSendonly)` maps that event to the `HOLD` transition while the leg is active, so the same rule covers three scenarios: our own hold confirmation, a repeated hold answer on an already held leg (idempotent, no CONNECTED rebound), and the remote party holding us while we are connected. A sendrecv `ESTABLISHED` on a held leg keeps the plain `CONNECT` mapping, which is exactly the resume confirmation. Events on pre-connect legs keep the plain mapping too, because early-media establishments legitimately carry sendonly audio.
- UI gating is a product decision: the Hold button stays disabled for peer-local calls (intra-app hold flip-flops the two local legs in baresip) and enabled for app-to-external calls. The core policy does not depend on this gating; it behaves identically whichever client issues the command.

## Backend Resolution

At startup, `SipBackend.resolve()` reads the system property `jsoftsip.sip.backend`:

| Value    | Backend Used     | Use Case                                    |
|----------|------------------|---------------------------------------------|
| `baresip`| `BaresipSipClient`| Production; requires Baresip on the system  |
| `mock`   | `MockSipClient`   | Testing and UI development without Baresip  |

The default is `baresip`. A system property is used because the backend must be known before any service (including `SettingsService`) is instantiated.

## Thread Safety

The concurrency model is layered by module responsibility:

- **UI thread** — All JavaFX state and UI updates live on the JavaFX thread. Controllers use `Platform.runLater` when a backend listener or background task needs to touch the UI.
- **SIP event thread** — The backend `ctrl_tcp` reader thread (in `BaresipTcpConnection`) and the mock simulation thread (`MockCallService`) deliver call events to the services. Services protect their state with thread-safe collections and `volatile` fields instead of relying on callers to synchronize.
- **Thread-safe collections** — `CopyOnWriteArrayList` is used for listener collections in `DefaultAccountService`, `DefaultCallService`, and `DefaultRegistrationService`. `ConcurrentHashMap` backs the active call map (`callsByBackendId`) in `DefaultCallService`.
- **Virtual threads** — `KeyedSerialExecutor` uses `Executors.newVirtualThreadPerTaskExecutor()` to run per-key ordered tasks for `DefaultRegistrationService` and `DefaultHistoryService`. The `SettingsDialogController` also offloads the baresip settings apply to a virtual thread, because the synchronous restart blocks for the `ctrl_tcp` wait window. `BaresipTcpConnection` and `BaresipOutputReader` run their reader loops on virtual threads.
- **Dedicated platform threads** — `NativeFrameTransport` uses dedicated platform threads for its video accept/read loops. `DefaultRegistrationService` owns a single platform-thread scheduled executor for registration timeouts, and `MockCallService` owns one for its simulation.
- **Synchronized methods** — `BaresipConfigService.apply`, `BaresipProcessManager`, `BaresipOutputReader`, and `BaresipTcpConnection` use `synchronized` methods for mutual exclusion.
- **Volatile fields** — `CallLeg`, `SipAccount`, `MockSipClient`, `BaresipSipClient`, `NativeFrameTransport`, `BaresipTcpConnection`, and `BaresipOutputReader` use `volatile` fields for cross-thread visibility.

### Exceptions and notes

- `BaresipConfigService.apply` is synchronous and `synchronized`; it runs on whatever thread calls it. The UI currently calls it from a virtual thread, but the service itself does not spawn or manage that thread.
- `ShutdownCleanup` uses a single-thread executor to avoid blocking the JavaFX thread while hanging up calls and unregistering accounts.
- `FramePipe` uses a `synchronized` ring buffer to coordinate single-producer/single-consumer access.

## Call State Machine

The call lifecycle is governed by `CallStateMachine`, a pure immutable transition table.

### States

- `IDLE` — Initial / terminal state
- `DIALING` — Outgoing call in progress
- `INCOMING` — Call received, not yet answered
- `RINGING` — Remote side is ringing
- `CONNECTED` — Call established
- `HOLD` — Call on hold
- `ENDED` — Call finished

### Events (Transitions)

- `START_DIAL`
- `INCOMING_CALL`
- `RINGING`
- `CONNECT`
- `RESUME`
- `HOLD`
- `FAIL`
- `TERMINATE`

### Table Size

The transition table contains **24 entries** covering the `(state, event)` pairs that the services can legitimately exercise. Creation events (`START_DIAL` and `INCOMING_CALL`) only originate from `IDLE`, `HOLD` is only accepted from `CONNECTED` and `HOLD` itself, and a few idempotent re-asserts (for example `RINGING` while already in `RINGING`) are kept to handle repeated backend events. Invalid transitions return `Optional.empty()`, which callers treat as a no-op.

The machine also provides `toTransition(SipCallState)` to map backend events onto the internal transition vocabulary, keeping backend-specific state semantics out of the service layer.

## Related Documents

- `docs/call-pipeline.md` — End-to-end runtime data flow: accounts, registration, call setup/termination, event ingestion, logging, and the normalization policy.
