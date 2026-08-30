# Call Pipeline: From SQLite to Baresip and Back

This document traces the complete lifecycle of account and call data through JSoftSIP: how accounts are read from the database, registered with the PBX via Baresip, how calls are placed and received, how termination works, and where username normalization applies. Read this before touching call correlation, the dialer, or the Baresip bridge.

## At a Glance

| Fact | Implication |
|------|-------------|
| There is **no JNI**. Baresip runs as a child process controlled over **ctrl_tcp** (JSON netstrings over TCP). | All SIP behavior is mediated by `BaresipSipClient`; debugging starts at the wire protocol. |
| Account usernames flow **verbatim** from SQLite to registration (e.g. `2_1003`, tenant prefix included). | The PBX always sees the full AOR. Nothing strips prefixes on the wire. |
| The dial destination is sent **bare** (`dial 1002`); Baresip appends the account domain. | The app never constructs SIP URIs for outgoing calls. |
| `backendCallId` is **minted by Baresip**, not by the app. | Call lookup keys (`callsByBackendId`) are Baresip identifiers. |
| `peeruri` in call events arrives **verbatim** from Baresip (From/To header value). | Whatever the PBX puts in the header is what the app correlates on. |
| `SipPeer` normalization is **in-memory and correlation-only**. | It never touches the database, the wire, or log output. |

## 1. Account Pipeline (SQLite to SipAccount)

1. `SQLiteAccountRepository.findAll()` runs `SELECT * FROM accounts ORDER BY id` and maps each row verbatim: `username` is copied untransformed, the password is AES-GCM-decrypted into memory, `id` is a boxed `Long`.
2. `DefaultAccountService` exposes the accounts without further transformation.
3. Account status (`ONLINE`/`OFFLINE`/`UNAVAILABLE`) is driven by registration events (`REGISTERED` → `ONLINE`, etc.) and persisted asynchronously. Startup forces every account to `OFFLINE`.

There is **no username transformation anywhere between the database and the domain model**.

## 2. Registration (App to Baresip to PBX)

1. `AccountsPaneController` → `DefaultRegistrationService.registerAccount` → `BaresipSipClient.registerAccount`.
2. The bridge builds the AOR as `"sip:" + username + "@" + domain` — for example `sip:2_1003@pbx.example`, tenant prefix preserved.
3. It sends `uanew <aor>;transport=udp;auth_pass=<secret>` over ctrl_tcp and records a bidirectional `aorToAccountId` mapping keyed by that exact AOR string.
4. Registration results return as `ua`-class events. `normalizeAor` strips `;params`, leftover userinfo, and default ports before lookup — but it does **not** strip tenant prefixes, so it matches the stored key.
5. On `UNREGISTERED`, the AOR mappings are removed.

## 3. Outgoing Call

1. `DialerDialogController.startCall` reads the raw destination text, re-checks the account is `ONLINE` with a fresh DB read, and delegates to `CallService.startCall` on the UI background executor (`UiTaskExecutor`), keeping blocking work off the JavaFX thread.
2. `DefaultCallService.startCall` calls `BaresipSipClient.startCall`, which sends the literal command `dial <destination>` — dialing `1002` sends `dial 1002`.
3. The bridge correlates the command with a random UUID future and extracts `call id:` from Baresip's response (`CallStartResponseParser`). That Baresip-minted id becomes the leg's `backendCallId`.
4. The new `CallLeg` is flagged for peer-locality (`SipPeer.isLocalAccount` on the dialed destination), attached to its `CallSession` via `CallSession.sessionKey`, registered in `callsByBackendId`, and listeners are notified.

## 4. Incoming Events (Baresip to App)

1. `BaresipTcpConnection.readLoop` reads netstrings; `CtrlTcpMessageDispatcher` splits `response` vs `event` frames.
2. `BaresipCallEventParser` accepts `event=true, class=call` frames and reads `id`, `accountaor`, `peeruri`, `direction`, `type`, `param`, and `remoteaudiodir`. **`peeruri` is passed through verbatim** — the bridge performs no transformation on it.
3. `BaresipCallStateMapper` maps Baresip states; `CALL_CLOSED` becomes `FAILED` when `param` carries a failure cause (404/486/408/connection reset), otherwise `TERMINATED`.
4. The account is resolved through the same normalized lookup as `ua` events: `normalizeAor` strips `;params`, leftover userinfo, and default ports from the event's AOR before the `aorToAccountId` lookup (`BaresipSipClient`). Call events and registration events are therefore symmetric — both resolve only because the AOR was registered exactly as stored, minus addr-params and default ports that Baresip may append when reporting it back.
5. A `CallEvent` reaches `DefaultCallService.onCallEvent`: known `callId`s drive the state machine; an unknown `INCOMING` id passes through fork deduplication (`isForkedIncomingCall`, which collapses INVITEs forked to multiple contacts into one leg via the session key) and then `createIncomingCall` builds the leg, attaches it to its session, and notifies listeners.

## 5. Call Termination Paths

Call commands are **fire-and-forget**: `endCall`/`answerCall`/`rejectCall` send the Baresip command, and the local state only changes when the backend's confirming event arrives (see `docs/architecture.md`, "Event-Driven Call State Updates").

Two UI entry points converge on the same service path:

- **Active Calls card**: `CallCardController` holds the live `CallLeg` (bound by `CallListCell`) and calls `endCall(call.getBackendCallId())` directly.
- **Dialer**: `DialerDialogController.hangup` resolves the leg through the observed `CallSession`, skipping `ENDED` legs (prefer the live leg of this account; otherwise the first non-ended leg). This guard exists because ended legs previously lingered in sessions and a redial would target a dead `backendCallId`, producing a silent no-op.

`AbstractCallService.endCall` resolves the id in `callsByBackendId` and sends `hangup <id>` to Baresip. When the terminating event arrives, `finishCall` performs terminal bookkeeping: resolves the call result, transitions to `ENDED`, removes the leg from `activeCalls`/`callsByBackendId`, records history, removes the leg from its session, **evicts the session when it has no legs left**, and ends the partner leg of a peer-local call (session-first search, then an identity-correlated fallback via `arePeersOfSameCall` so an unrelated call on another account can never be terminated by accident).

## 6. Logging

| Logger | Writes | Notes |
|--------|--------|-------|
| `jsoftsip` (app) | Service-level events (`DefaultCallService`, `AbstractCallService`, controllers) | Secrets sanitized (`auth_pass`/`password`/`secret` → `***`). |
| `baresip` (bridge) | ctrl_tcp commands/responses, event dumps | Same sanitization. |
| Baresip process stdout/stderr | Native rendering of AORs/URIs | Drained by `BaresipOutputReader` into `baresip.log`. |

File output (rolling, 2 MB × 5, async never-blocking) lands in `<configDir>/logs/` when `ui.logging.save_to_file` is enabled. **No logged value ever passes through `SipPeer` normalization** — if a log shows `1003` without a tenant prefix, that is what the PBX/Baresip sent.

## 7. Normalization Policy (SipPeer)

`SipPeer.normalizeUsername` exists for one reason: the lab PBX is multitenant and registers accounts with corporation prefixes (`1_`, `2_`, …), while the peer URIs it reports often carry the bare extension. It strips:

- numeric instance prefixes (`2_1003` → `1003`)
- dash-prefixed hex instance tokens (`-a1b2c3d4_1003` → `1003`)
- dash-suffixed hex instance tokens (`1003-a1b2c3d4` → `1003`)

`SipPeer.matches(account)` additionally requires the peer host to equal the account domain (a bare dial target matches any host).

Normalization is applied **only** to in-memory correlation:

- `CallSession.sessionKey` (grouping the two legs of one logical call)
- `SipPeer.isLocalAccount` (peer-local flag on legs)
- `isForkedIncomingCall` (fork deduplication)
- `arePeersOfSameCall` (partner-leg fallback guard)

Known limitations:

- **Non-hex dash tokens are not stripped** (`1003-0x1a2b3c` stays as-is, because the token/number boundary is ambiguous). Such URIs land the leg in a sibling session; the identity-correlated partner fallback exists precisely for this case.
- **Multitenant collision hazard**: keys are username-only and host-agnostic, so accounts `1_1003` and `2_1003` on the same PBX domain both normalize to `1003`. Registering accounts from two corporations simultaneously could merge their sessions. If that scenario becomes real, correlate peer-local sessions by resolved account-id pairs instead of normalized strings.

## 8. Operational Notes

- **Running from the repository**: `mvn -pl launcher javafx:run` resolves `core`/`ui`/`native-bridge` from `~/.m2`, not from module `target/classes`. After changing any module, run `mvn install` (or add `-am`) or the app silently executes stale jars. See `docs/build-and-package.md`.
- **Running tests for `ui`**: always `mvn -pl ui -am test`; without `-am` the module resolves a stale core jar and fails with `NoClassDefFoundError` for recently added core classes.

## Checklist

- [ ] I can explain why no JNI is involved and which class owns the ctrl_tcp channel.
- [ ] I know who mints `backendCallId` and where `peeruri` comes from.
- [ ] I can name every place `SipPeer` normalization applies — and confirm it never reaches DB, wire, or logs.
- [ ] I know why the dialer hangup must skip `ENDED` legs and why empty sessions are evicted.
- [ ] I know the multitenant collision hazard of username-only session keys.

## Next Step

- Module boundaries and threading model: `docs/architecture.md`
- Building, packaging, and stale-artifact pitfalls: `docs/build-and-package.md`
- Test strategy: `docs/testing.md`
