# Roborock Local Server patch

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

## Patch and install locally

You can patch directly on your Android phone. Use a recent [Morphe Manager](https://github.com/MorpheApp/morphe-manager/releases) supporting the current `patch` API; these instructions use Manager 2.5.0. The `.mpp` bundle contains JVM classes and Android DEX.

1. Build the bundle using [Build locally](#build-locally), or obtain it from the person who built this patch. Copy **`patches/build/libs/roborock-local-server-0.1.0.mpp`** and an **original, unpatched Roborock 4.76.08 APK (build 100944, arm64-v8a)** into your phone's **Downloads** folder. The bundle is the patch source; the APK is the app to patch. Do not select the already-patched APK or extract it from the installed patched app.
2. In Morphe, open **Sources → + → Local** and select `roborock-local-server-0.1.0.mpp` from Downloads. If updating a previously imported local source, replace/update that source with the new bundle; local sources do not update automatically.
3. Select **Roborock**, choose **4.76.08**, and select **No, I already have an APK** when prompted. Choose the original APK you copied. If the manual controls are hidden, enable **Expert mode** in Settings under Patching. Labels may vary between Manager versions.
4. Select **Local server login**. Keep the patch's required dependencies selected, then tap **Patch**.
5. After patching succeeds, tap **Install** and allow Android's install permission for Morphe if prompted. If the existing Roborock app uses a different signing key, Android will reject the upgrade. To switch to Morphe's key, uninstall **Roborock only**, then install the patched result. This clears Roborock's local login/settings; you will enter the server address, email, and PIN again. You do not need to import this project's development signing key for a fresh installation.
6. Open Roborock and follow the Local server login steps above. Keep Morphe's app data and signing key for future updates: updates must use the same key to preserve Roborock's data.

**Bundle not visible in the file picker?** Open the picker from **Sources → + → Local**, rather than the APK-selection step. Browse to **Internal storage → Download** if the Downloads shortcut is stale, and close/reopen the picker after copying files. Files transferred with ADB may need a media scan before Android lists them. On one phone, copying the bundle directly into Downloads and refreshing the index resolved an apparently empty subfolder.

See Morphe's [local patch source guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patch-sources.md) and [expert patching guide](https://github.com/MorpheApp/morphe-manager/blob/main/docs/patching-expert-mode.md).

## Connect to your server

1. Open the patched Roborock app and reach the login screen.
2. Tap the server icon beside the phone, Google, and Facebook login buttons. A **Local server** label and curved arrow point to it.
3. Enter your local HTTPS URL, including the port (default `555`), your server's configured `protocol_login_email`, and its six-digit `protocol_login_pin`.
4. Tap **Connect**. The server must already be configured with your vacuum and inventory.
5. Check device listing, map loading, one basic command, and reopening the app after force-stopping it.

Use the local protocol PIN, not the server's admin password or `admin.session_secret`.
The phone must reach the server over LAN or your own remote-access VPN, and the HTTPS certificate must be valid and trusted on the phone.

The form remembers only the address and email. It clears the PIN after a valid session response or when the form is closed. The app's existing native account flow stores the resulting session credentials.

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

Patch source is GPLv3; see LICENSE and NOTICE. Original Roborock app code and assets are not included in the source distribution.
