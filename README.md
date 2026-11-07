# CrazeKeyboard

CrazeKeyboard is an offline Android keyboard (`studio.craze.keyboard`) with a learned, on-device dictionary. It targets Android 6.0+ (minSdk 23), uses Java 17, and requests no Internet permission.

## Features

- English and Bengali layouts, suggestions, next-word predictions, cautious autocorrect, and glide typing.
- Personal dictionary and text shortcuts, stored in private app files and managed from **Typing and appearance settings**.
- Learning and suggestions are disabled for password fields and editors that request no personalized learning.
- Appearance controls for dark keys, borders, and keyboard height.
- Setup screen reports whether the keyboard is enabled and selected.

The dictionary starts empty. Suggestions and glide results improve as the user types; no bundled word list or network service is used.

## Build and test

Run the dependency-free dictionary/core tests with:

```sh
sh run-core-tests.sh
```

The Android app is built by GitHub Actions with Gradle 8.9 and Android SDK 35. The `Android CI` workflow also uploads a debug APK artifact. To build locally, use a compatible Android SDK and run `gradle --no-daemon assembleDebug`.

## Project layout

- `app/` — Android application, input method service, setup/settings screens, and resources.
- `app/src/main/java/studio/craze/keyboard/core/` — platform-independent dictionary, suggestions, correction, and glide logic.
- `core-tests/` — plain-Java regression and performance sanity checks.
- `replica/` — project architecture, feature inventory, and design references.

## Privacy and limitations

Learned words and shortcuts stay in the app's private storage on the device. The keyboard does not transmit typed text. Glide and autocorrect rely on learned words, so results are intentionally limited on a fresh install. The Android build and real-device behavior should be verified in CI and on a phone; the core test suite alone does not validate Android framework behavior.
