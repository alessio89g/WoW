> [!NOTE]
> **The entire project was created through vibe coding. This repository contains no human-written code.**

[Leggi in italiano](README.it.md)

# WoW

The interface starts in English. Open the left-side menu on the home screen and select **Italiano** or **English** to switch languages immediately. **Third-party licenses** shows the bundled license texts in the selected language. WoW remembers your choice the next time you open the app.

WoW is an Android app (`net.alessio.wow`) for Android 8 or later. It uses Material 3 Expressive and displays saved profiles on the home screen.

Enter the SSH port when creating a new profile. Existing profiles keep their saved ports.

## Wake via SSH on your router

An SSH profile connects to the router using a device key or a username and password. Under **Wake-on-LAN command**, choose a built-in preset.

| Preset | Command |
| --- | --- |
| AsusWRT / Merlin | `ether-wake -i {interface} {broadcast} {mac}` |
| OpenWrt | `etherwake -i {interface} {broadcast} {mac}` |
| MikroTik RouterOS | `/tool wol mac={mac} interface={interface}` |
| Advantech CLI | `ether-wake -i {interface} {broadcast} {mac}` |

The syntax appears below each preset name. The OpenWrt preset requires the `etherwake` package on the router.

**New preset** opens an empty form for the name and command. Enter a single-line command with simple arguments. `{mac}` uses the PC’s MAC address, `{interface}` uses the LAN interface from the profile, and `{broadcast}` expands to `-b` when enabled. You can create multiple presets and remove them individually. Each profile keeps its own copy of the selected preset, so its command remains available if you remove the preset from the library.

The router firmware must allow the SSH user to run the selected command. Check the router’s LAN interface name. A zero exit code confirms that the command completed successfully. Check separately whether the PC woke up.

On the first connection, WoW stops before authentication and displays the SHA-256 fingerprint of the router’s SSH key. Compare it with a known fingerprint obtained through a trusted connection. After saving the key, tap **Wake** again. If the router presents a different key, WoW blocks the connection. To pair it again, open **Edit**, select **Forget key**, and verify the new fingerprint. Checking the key in the editor reads the router’s key before authentication and leaves the wake command unsent.

The target device’s network interface must support Wake-on-LAN and be configured for it. If the router allows SSH on the LAN only, remote access requires a VPN into the home network or an explicitly configured remote SSH connection. DDNS supplies a hostname for the router.

## Credentials and profiles

WoW encrypts profiles with AES-GCM using a non-exportable key in Android Keystore. It keeps the password out of the Activity’s saved state and logs. Uninstalling the app, clearing its data, or losing the key makes the encrypted profiles unrecoverable.

**Device key** uses an ECDSA P-256 key generated in Android Keystore. A JSch adapter signs through the provider while the private key stays in the Keystore. On the home screen you can generate the key once and copy its OpenSSH public key. This mode uses public-key authentication exclusively and stores no SSH password. Hardware protection depends on the device. WoW neither requires nor attests StrongBox. You can open the app and view the public key directly, even when profiles use SSH keys. Installing an update preserves existing keys and profiles.

## UDP mode

UDP mode supports local and remote IPv4 addresses and DNS or DDNS names. Each tap sends one standard 102-byte magic packet. The port is configurable and defaults to 9. WoW runs on demand, without polling, a persistent service, or automatic power-on checks.

## Updates

WoW checks [GitHub Releases](https://github.com/alessio89g/WoW/releases) on launch. To check manually, use **Menu → Check for updates** in the left-side panel. A newer public release can offer a download. WoW checks the APK’s package name, signing certificate, and version code before handing it to Android for installation. Android asks you to approve installation and may ask you to allow WoW as an install source.

For the updater, publish a stable release tagged `vX.Y.Z` or `X.Y.Z` with an asset named `WoW-X.Y.Z.apk` or `WoW-X.Y.Z-debug.apk`, signed with the same key and using a higher Android version code. The app reads public release metadata without credentials.

## Build and test

Use JDK 17, Android SDK 35, Gradle 9.7.1, and AGP 9.1.1. The main dependencies are Material3 1.5.0-alpha03, JSch 2.28.8 (mwiede fork), and Bouncy Castle 1.86 for Ed25519 support on Android.

```sh
ANDROID_HOME=/path/to/android-sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

The JVM tests cover the UDP payload, local send and receive, input validation, legacy profile migration, credentials containing special characters, and command injection rejection. The SSH integration test requires the local test server and the `WOW_SSH_TEST_EVENTS` environment variable. Without the fixture, the test is explicitly skipped.

Run the custom `SshSmokeInstrumentation` only on a development emulator with the local fixture. It tests Android Keystore, SSH authentication and commands, reading the server key before login, generating a non-exportable key, and authenticating with that key. The JVM signature tests check the SSH format against an independent Java verifier. The fixture rejects unauthorized client keys.
