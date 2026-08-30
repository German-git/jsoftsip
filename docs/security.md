# Security Model

This document describes the current security model of JSoftSIP, the threats the application considers, and the trade-offs made when integrating with the Baresip SIP backend.

## Threat Model

JSoftSIP is designed to run on a single-user desktop or workstation. The primary threat assumptions are:

1. **Local user access:** An attacker who can run code as the same OS user can read the application's configuration directory and any memory the process owns. This is the baseline threat for any desktop application.
2. **Other users on the same machine:** Configuration files are written with restrictive permissions (`0600`) so that other OS users cannot read persisted accounts, settings, or the master key.
3. **Process memory:** Decrypted passwords and the master key exist in memory while the application is running. A debugger or memory dump can extract them.
4. **Network traffic:** SIP signaling and RTP media travel over the network in the same way Baresip sends them. JSoftSIP does not add or remove encryption from the actual SIP/RTP traffic; it relies on the SIP provider and Baresip for TLS/SRTP when configured.

## Data at Rest

- Accounts, settings, and call history are stored in an SQLite database under the application configuration directory.
- Sensitive settings (such as SIP passwords) are encrypted with AES-256-GCM using a master key.
- The master key is stored in `master.key` under the configuration directory. The file is created with `0600` permissions.
- The master key is a 32-byte random value. It is validated on load and, if missing, automatically restored from `master.key.bak` if a backup exists.

## Master Key Backup and Rotation

Rotation is implemented end-to-end and can be triggered from the UI at any time:

1. In the Settings dialog (General tab), the rotate action (`SettingsDialogController.rotateMasterKey`) asks for explicit confirmation before touching anything.
2. On confirmation, `DefaultAccountService.rotateMasterKey()` stages a fresh key via `MasterKeyManager.prepareRotation()` into `master.key.staged` without touching the active key, then rekeys every stored credential through `SQLiteAccountRepository.rekeyCredentials()`.
3. If the storage rekey fails, the staged key is discarded (`MasterKeyManager.abortRotation()`); the active key was never modified, so encrypted data remains readable and the backup survives: rotation either fully completes or leaves the original state intact.
4. Only after a successful rekey does `MasterKeyManager.commitRotation()` atomically promote `master.key.staged` over `master.key` and delete the old key's backup copy (`master.key.bak`), so a successfully retired key can no longer be recovered.

Outside rotation, `master.key.bak` remains the recovery mechanism: if `master.key` is deleted or lost while no rotation is in progress, the application restores the key from the backup on startup. This prevents accidental data loss but also means that deleting `master.key` alone does not reset the encryption key. A leftover `master.key.staged` file is discarded on startup.

## Baresip Credentials and the Control Channel

- Baresip receives account credentials through its `/uanew` control command, which includes the SIP password (`auth_pass`) in plain text over the local TCP control channel (`ctrl_tcp`).
- The SIP password therefore travels in plain text from JSoftSIP to the Baresip process over the local control socket. Any local process able to observe the control channel (or to read Baresip's command input) can capture the password. This is an inherent limitation of driving Baresip over `ctrl_tcp`: there is no encrypted or out-of-band channel for the password on that interface.
- Baresip's own configuration directory (`~/.baresip`) may contain a `config` file with the same credentials. JSoftSIP copies that file into the application directory when writing Baresip configuration. The copy is written with `0600`, but the original `~/.baresip/config` permissions are outside the application's control.
- Users who run Baresip directly should ensure that `~/.baresip/config` is readable only by their own user.

### Mitigation evaluated: `ui_password_prompt` (not viable on Baresip 4.6.0)

Supplying the password through Baresip's `ui_password_prompt()` API was evaluated as a way to register an account without embedding `auth_pass` inside `/uanew`. This approach does not work and would not improve security:

- On Baresip 4.6.0, `ui_password_prompt()` is a blocking read from `stdin` (`src/ui.c`), not a control-channel or UI-module callback. It is not serviced by `ctrl_tcp`, which exposes no password command.
- If JSoftSIP omitted `auth_pass` from `/uanew`, Baresip would block waiting for input on `stdin` (`modules/account/account.c` triggers the prompt when `auth_user` is set and `auth_pass` is absent). JSoftSIP has no channel to satisfy that prompt, because it only communicates with Baresip over `ctrl_tcp`.
- Even if a `ctrl_tcp` command were added to supply the password, the password would still cross the local control socket in plain text, so the threat would be unchanged.
- There is no file- or reference-based mechanism (`auth_pass_file`, `auth_pass_ref`) in Baresip 4.6.0.

Conclusion: the plain-text credential on the control channel is a backend limitation of Baresip, not of JSoftSIP. Removing the password from `/uanew` is not supported without patching Baresip. The accepted mitigations are to constrain the configuration directory permissions (above) and to use TLS on the SIP account so the password is not exposed on the network.

## Recommendations

1. Keep the application configuration directory (`~/.config/jsoftsip` on Linux, `%APPDATA%\JSoftSip` on Windows) readable only by the owner.
2. If Baresip is run independently, ensure that `~/.baresip/config` is also owner-readable only.
3. Use a strong OS user password and full-disk encryption to mitigate the local-user threat.
4. Enable TLS on the SIP account whenever the provider supports it, so credentials are not sent over the network in plain text.
