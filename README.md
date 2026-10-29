# CrazeKeyboard

**CrazeKeyboard is a modified version of FUTO Keyboard, rebranded and maintained by CrazeStudio.** This is a community derivative, not an official FUTO release and not endorsed by FUTO. The FUTO Source First License, copyright notices, attribution, and payment-related functionality are retained.

CrazeKeyboard includes a CrazeStudio-branded settings dashboard and launcher mark, Craze Night and Craze Day keyboard palettes with different key-corner shapes, and direct shortcuts to the existing theme, keyboard-size, typing, and language customization screens. The upstream theme editor and other keyboard features remain available.

## Source and attribution

This project is based on the official [FUTO Keyboard source repository](https://github.com/futo-org/android-keyboard), snapshot commit `31e720ddcc2df6259069b2ae7cf0f1defaf2cb74`. Its checked-out source dependencies are included as regular files in this repository:

- `java/assets/futo-swipe`: `93931877c0083d0de7f51fad82325dcbe10421bf`
- `java/assets/layouts`: `fb4dad270790d980c32417b60359104bd0c32c1c`
- `java/assets/themes`: `4799ad5a597522d42d4cdfe34cdaf064aa0c434c`
- `java/res-large`: `d87d9dbdf3966bbe18413be375dab2f6c7bbdfdd`
- `libs`: `be4362cf82022e613cd34ff95d579c8502eaad8e`
- `translations`: `8cd47ae043e8d9fe23878ac7e473f4817b9e2681`
- `voiceinput-shared/src/main/ml`: `7692bc6096c51c44beba716a3c429ff09dbfffd9`

Upstream copyright, third-party, and licensing notices are preserved in the source tree. The FUTO license text is in [`LICENSE.md`](LICENSE.md); review it before modifying or redistributing this project. In particular, the license limits modifications and redistribution to non-commercial purposes, requires preserving notices and payment-related functionality, and requires modified copies to carry a prominent modification notice. CrazeStudio is not claiming ownership of FUTO’s original code or marks.

For compatibility with the upstream payment flow, the Android namespace/application ID and the FUTO payment screens/strings remain unchanged. Consequently, the stable build uses the upstream package ID and cannot be installed alongside another app with that same ID; the development build uses the upstream `.unstable` suffix.

## Build

Use JDK 17 or newer and install Android SDK Platform 35, then open the repository root in Android Studio or use the included Gradle wrapper:

```bash
./gradlew assembleUnstableDebug
```

A signed release build requires a local `keystore.properties` file and a signing key. Do not commit private signing credentials. The wrapper and all imported source assets are already present in this repository snapshot; no recursive submodule checkout is needed.

## Exporting APKs

The **Build and export APKs** GitHub Actions workflow runs when changes are pushed to `main`, or can be started manually from the repository’s **Actions** tab. Each successful run builds stable and unstable debug APKs and uploads them as a downloadable artifact for 14 days. Download the `CrazeKeyboard-APKs` artifact from the workflow run page.

These are debug-signed test builds, not release-signed distribution packages. The stable flavor uses the upstream application ID; the unstable flavor adds `.unstable` and can be installed alongside the stable app.

## FUTO project information

- Official upstream: <https://github.com/futo-org/android-keyboard>
- Upstream website: <https://keyboard.futo.tech/>
- License and notice files: [`LICENSE.md`](LICENSE.md) and the other retained notices in this repository
