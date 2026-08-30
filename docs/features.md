# Features

This document describes the major features of JSoftSIP as they exist in the current codebase.

## Accounts

### CRUD Operations

- Create, edit, and delete SIP accounts through a dedicated dialog
- Each account stores: display name, username, domain, transport (UDP/TCP/TLS/WSS), and optional password
- Accounts are persisted to SQLite via `SQLiteAccountRepository`

### Encrypted Passwords

Account passwords are encrypted at rest using AES-256-GCM. The encryption key is managed by `MasterKeyManager`, which initializes a master key on first launch.

### Manual Registration

Accounts start in `OFFLINE` status on every application launch. The user triggers registration manually per account via the account list context menu. This design prevents automatic connections and gives the user explicit control.

### Status Indicators

Each account displays a colored status dot:

- Green — Registered and online
- Gray — Offline, unregistered, or registration failed (the human-readable failure reason is shown separately)

There is no red/yellow status dot; a failed registration is rendered as gray.

### Registration Failure Handling

When registration fails, the UI presents a human-readable reason (e.g., authentication failure, unreachable server, transport error) so the user knows what to correct.

### Re-provisioning on Edit

Editing an account triggers re-provisioning only when a SIP-relevant field actually changed: the pre-edit state is snapshotted via `SipAccountDiff.snapshotSipFields` and `reprovisionAccount` runs only if `SipAccountDiff.hasSipFieldChanges` detects changes (covered by `SipAccountDiffTest`). Cosmetic edits such as the display name take effect without re-registration.

## Calls

### Outgoing and Incoming Calls

- Outgoing: select an account, open the dialer, enter a destination URI or number, and call
- Incoming: the UI shows an incoming call dialog with answer and reject actions

### Hold and Resume

Active calls can be placed on hold and resumed. The commands are forwarded to the backend as-is, and the leg state changes only when the backend confirms through a call event: a sendonly SDP answer lands the leg in `HOLD` (our own hold or a remote hold), a sendrecv answer on a held leg resumes it. See the Hold/Resume Policy section in `docs/architecture.md`.

### Hangup

Any active call can be terminated. The service notifies listeners, moves the call to `ENDED`, and persists a history entry.

### Mute

Microphone mute is implemented on the audio stream level: `BaresipVolumeController` runs `pactl set-source-output-mute` against Baresip's PipeWire/PulseAudio source outputs — the same mechanism as volume control. Nothing SIP-level is sent to the backend; the UI reflects the current mute state.

### Duration Tracking

Call duration is recorded from the moment the call reaches `ESTABLISHED` until it terminates, for both incoming and outgoing calls. Duration is shown in the active calls panel and stored in history.

### Simultaneous Calls

The call service maintains a `CopyOnWriteArrayList` of active calls, allowing multiple calls to exist at the same time. Each call is tracked by its backend call ID in a `ConcurrentHashMap` for O(1) lookup.

### Call State Machine

Every state change is validated by `CallStateMachine`. Invalid transitions are silently ignored, preventing the UI from entering inconsistent states.

## Call History

### Persistent Storage

All finished calls are stored in SQLite via `SQLiteCallHistoryRepository`. History survives application restarts.

### Direction and Duration Tracking

Each history entry records:

- Direction (`OUTGOING` or `INCOMING`)
- Start and end timestamps
- Duration in seconds
- Associated account and destination

### Results

A call result is assigned when the call ends:

| Result      | Meaning                                      |
|-------------|----------------------------------------------|
| `ANSWERED`  | Call was established and later terminated    |
| `REJECTED`  | Incoming call was explicitly rejected        |
| `MISSED`    | Incoming call was not answered               |
| `FAILED`    | Call failed at the SIP level                 |
| `CANCELLED` | Outgoing call was cancelled before answer    |

The result is derived automatically from the final state and direction.

## Audio Engine

### Baresip Subprocess

The audio and SIP signaling stack runs inside a Baresip subprocess managed by `BaresipLauncher`. The subprocess is started before the UI is shown and is terminated on application shutdown.

### Full-Duplex Audio

Baresip handles capture and playback. JSoftSIP does not process audio samples directly; it controls Baresip via the `ctrl_tcp` module.

### Config Pipeline

`BaresipConfigService` reads the existing Baresip configuration, applies patches from the settings UI, writes a new config, and restarts Baresip. If the new config fails to start, the previous working config is restored automatically.

### Settings

Audio settings managed through the UI include:

- Audio player, source, and alert devices
- Jitter buffer type and size
- Audio buffer mode and silence threshold

## Settings

The settings dialog provides five tabs:

### General

- Theme selection (seven AtlantaFX themes: Primer Light/Dark, Nord Light/Dark, Cupertino Light/Dark, and Dracula)
- Language selection (English, Spanish, Portuguese); the persisted preference is restored at startup
- Remember window geometry
- Confirm exit when calls are active
- Save Baresip log to file

### Audio

- Audio device selection (populated from `pactl`)
- Buffer and jitter parameters

### Baresip

- Local call timeout and max concurrent calls
- Registration timeout
- RTP timeout
- SIP listen address
- Auto-answer toggle

### Video

- Video codec selection (H.264, VP8, H.265)
- Resolution and bitrate
- Frames per second

### Baresip Config

A live preview of the patched Baresip configuration file. Every edit in the Audio, Video, or Baresip tabs refreshes the preview in real time so the user sees the exact output before applying.

> On the `MOCK` backend, the Baresip-specific tabs (Audio, Video, Baresip, Preview) are hidden because no native process exists to configure.

## Volume Control

Volume sliders in the main toolbar control:

- Output volume (speaker)
- Microphone volume (capture)

These are applied per-application via `pactl` (PulseAudio) against Baresip's sink-input/source-output streams — nothing is sent to Baresip over `ctrl_tcp`. The values are recorded in the client state and persisted so the UI stays consistent across restarts.

## Application Shutdown

### Clean Shutdown Flow

When the user closes the main window:

1. If the preference is enabled and calls are active, a confirmation dialog appears
2. `JSoftSipApplication.onCloseRequest()` closes all modal dialogs and dialer windows
3. On confirmation, `ShutdownCleanup` runs:
   - Hangs up every active call
   - Unregisters all accounts
4. `JSoftSipApplication.stop()` tears down the context:
   - Shuts down the video frame transport
   - Shuts down the frame pipe adapter
   - Terminates the Baresip process

### Window Geometry Persistence

If enabled, the window position and size are saved on close and restored on the next launch. Malformed stored values are ignored gracefully.

## Additional Features

The following capabilities are present in the code but were not covered in the original documentation:

### Custom Modal Dialogs

All alerts, confirmations, and prompts use the custom application-modal dialog system in `com.jsoftsip.ui.dialog` (`DialogService` / `DialogBuilder`) instead of `javafx.scene.control.Alert`.

### Live Call Duration Timeline

The active calls panel shows a live duration counter on each call card, updated by a JavaFX `Timeline` while the call is in `ESTABLISHED`.

### Video Transmission Toggle

During a video call, the user can enable or disable outgoing video transmission. The UI sends the `videodir` command to Baresip to toggle the direction.

### Clear Call History

A button in the Settings dialog (General tab) allows the user to delete all persisted call history entries. The history panel itself only displays the call list; the destructive action lives under Settings to avoid accidental activation while browsing history.

### Hold Restriction for Peer-Local Calls

Hold is disabled for calls where the peer is another local account, with a tooltip explaining the restriction.

### Volume Persistence

Output and microphone volume values are persisted across application restarts and restored on launch.

### Native Video Loopback Transport

The `NativeFrameTransport` receives raw video frames from the custom `jvidisp` Baresip module over a TCP loopback socket and routes them into per-account `FramePipe` instances for display in the JavaFX UI.
