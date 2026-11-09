# Roborock Local Server patch for Morphe

> [!WARNING]
> Roborock Local Server add-on **1.2.1** requires additional server-side fixes: [#207](https://github.com/Python-roborock/local_roborock_server/pull/207) (missing product metadata causing device initialization error `-21020`), [#224](https://github.com/Python-roborock/local_roborock_server/pull/224) (product-ID and plugin-catalog resolution), and [#209](https://github.com/Python-roborock/local_roborock_server/pull/209) (the app-agreement endpoint). The Android patch does **not** install these server fixes; login alone does not establish device/plugin compatibility.
>
> **A Home Assistant-managed restart, container recreation, or add-on update can discard manually applied server fixes.** Restarting the existing container with `docker restart` preserves them. When upgrading, check whether the server release already contains the fixes before reapplying a backport.

Experimental patch for use with Morphe, targeting **Roborock 4.76.08 (build 100944), arm64-v8a**.
Adds a Local server button to the bundled login screen, patches the native signing check, and normalizes the saved server URL.

## Get the original app

Download **4.76.08 / 100944 / arm64-v8a / nodpi** from [APKMirror](https://www.apkmirror.com/apk/roborock/roborock/roborock-4-76-08-release/).
Choose the original standalone APK. Verify its SHA-256 before patching:

```text
f26660665dcb39b4688e976d16ff2dc270f31867780c3b1628f0263a6fa03203
```

On macOS or Linux, the included validator checks the complete APK against that hash:

```sh
python3 scripts/apk_profiles.py '/absolute/path/to/original.apk'
```

It prints `4.76.08` on success and exits with an error for other files.
The patch also checks the original login bundle, native library, instruction bytes, and getter structure. Do not bypass a fingerprint failure with force.

## Patch and install on your phone

You need the **`.mpp` bundle** — build it via [Build locally](#build-locally) or get it from whoever built this patch — and an **original, unpatched Roborock 4.76.08 APK (build 100944, arm64-v8a)**. Copy both into your phone's Downloads folder. Use a recent [Morphe Manager](https://github.com/MorpheApp/morphe-manager/releases); these steps use Manager 2.5.0.

1. In Morphe, open **Sources → + → Local** and select `roborock-local-server-0.1.0.mpp` from Downloads. Local sources do not update automatically — replace an imported source when you build a newer bundle.
2. Select **Roborock → 4.76.08 → No, I already have an APK**, then choose the original APK. Do not pick an already-patched APK. If the manual controls are hidden, enable **Expert mode** in Settings under Patching.
3. Select **Local server login**, keep the required dependencies, tap **Patch**, then **Install**.
4. If Android rejects the upgrade, the installed Roborock uses a different signing key: uninstall **Roborock only** and install the patched APK instead. This clears Roborock's data — address, email, and PIN must be re-entered. Future updates must come from Morphe's key to preserve that data.

**Bundle not visible in the picker?** Open the picker from **Sources → + → Local**, not the APK-selection step. Browse to **Internal storage → Download** if the shortcut is stale, reopen the picker after copying files, and media-scan ADB-transferred files.

See Morphe's [local patch source guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md) and [expert patching guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patching-expert-mode.md).

## Connect to your server

1. Open Roborock and reach the login screen. A curved arrow points at the server icon beside the phone, Google, and Facebook buttons.
2. Enter your local HTTPS URL including the port (default `555`), the server's `protocol_login_email`, and its six-digit `protocol_login_pin` — not the admin password or `admin.session_secret`. The server must already be configured with your vacuum and inventory.
3. Tap **Connect**, then check device listing, map loading, and one basic command. Force-stop and reopen the app to confirm the session persists.

The phone must reach the server over LAN or your own remote-access VPN, and the HTTPS certificate must be valid and trusted on the phone.

The form saves only the address and email; it clears the PIN after a successful login or when the dialog closes. The app's existing native account flow stores the session credentials.

## Distribution

Distribute this project's patch source and `.mpp` bundle only. Original and patched Roborock APKs, including APK bundles such as `.apkm`, `.apks`, and `.xapk`, must not be committed, attached to releases, or uploaded as CI artifacts.
Direct users to APKMirror and the original APK SHA-256 above. Generated APKs are for the builder's own local use.

## Build locally

Requires Node.js/npm, a JDK capable of running Gradle 9.8.2, Python 3, and Android SDK build-tools 35.0.0. The Gradle build targets Java 17 for Morphe compatibility.

Download the official `morphe-desktop-2.1.1-all.jar` from
https://github.com/MorpheApp/morphe-desktop/releases/tag/v2.1.1
and save it as `tools/morphe-desktop.jar`.

Build the shareable patch bundle without an APK:

```sh
npm ci --ignore-scripts
./gradlew buildAndroid --console=plain
```

The bundle is `patches/build/libs/roborock-local-server-0.1.0.mpp`.

To patch, sign, and audit your own APK on the desktop:

```sh
bash scripts/build-apk.sh '/absolute/path/to/original.apk'
```

Set `ANDROID_HOME` if the SDK is outside `~/Library/Android/sdk`.
The script verifies the original APK hash, builds and tests the bundle against it, patches it, aligns and signs the result, and audits its contents.
The locally generated APK and audit reports are saved in ignored `dist/` files. Keep `signing/roborock-local.jks` for subsequent updates; it is a development key using the standard `android` password and is excluded from source distribution.

The build compiles against the patcher shipped in the official desktop JAR rather than requiring access to Morphe's GitHub Maven registry. `esbuild` transforms async functions for compatibility with the app's JavaScript engine. No extension DEX is injected into Roborock.

## Verification

The local build runs nine Node tests against the fingerprinted 4.76.08 login bundle, then audits the signed APK.
The audit requires exactly the login JavaScript asset, `classes4.dex`, and native library to change; `classes6.dex` must contain only the relocated saved-URL class. Other APK entry contents remain identical apart from signing metadata.
The native audit verifies the original hash, GNU build ID, and instruction bytes, then requires exactly one NOP replacement.
The build also runs Android SDK signature and ZIP alignment verification.

**Subsequent phone verification:** the signed 4.76.08 build was upgraded and launched on a Pixel 8 Pro, and the user confirmed local-server login worked with the server hotpatches described above. This does not establish compatibility with an unmodified server or every vacuum model. Full MQTT, map, command, and restart-persistence coverage remains unverified. The saved-URL patch normalizes the refactored repository getter; legacy cached-field readers still exist.

Downloaded login-feature updates could supersede the bundled feature. Mocked tests and APK audits do not establish compatibility with every server or vacuum.

## Troubleshooting

- **App exits immediately:** capture the crash with ADB or Android's bug report tools. This differs from a server connection failure.
- **Button missing:** note whether it ever appeared; the app may have loaded a downloaded login feature.
- **HTTPS error:** verify the hostname, port, phone connectivity, and certificate trust.
- **Email/PIN rejected:** confirm local protocol login and new connections are enabled in the server config.
- **Login accepted but opening account fails:** inspect the server logs for unsupported account, agreement, plugin, or inventory requests.
- **Devices work but maps fail:** note the vacuum model and check plugin/MQTT requests separately.

If sharing logs, remove credentials, tokens, MQTT keys, and personal identifiers first. The upstream app's existing account bridge logs account data; the patch adds no credential logging.

## References and license

- [Upstream native patch](https://github.com/Python-roborock/local_roborock_server/blob/main/patcher/patch_librrcodec.py)
- [Local server](https://github.com/Python-roborock/local_roborock_server)
- [Morphe patch API](https://github.com/MorpheApp/morphe-patcher)

Patch source is 0BSD (Zero-Clause BSD); see LICENSE. Morphe branding restrictions remain in NOTICE. Original Roborock app code and assets are not included in the source distribution.
