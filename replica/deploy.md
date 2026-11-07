# Deploy checklist: CrazeKeyboard (Android keyboard, rebuild of Gboard's core features)

Date: 2026-10-29  Commit: none (the build in this zip is not committed to any branch; `main` was at bd0d7fc when checked)  Go from user: **not yet**

## Verdict: BLOCKED. Do not publish yet.

The gate has failures that no amount of setup work gets around. Nothing here was bought, signed, uploaded or published.

## Preflight (all must pass)

| check | result | detail |
| --- | --- | --- |
| e2e suite green | FAIL (not run) | `replica-test` has not been run. No UI or device tests exist. What does exist: 88 core checks for the dictionary, passing (`sh run-core-tests.sh`). |
| no open S1 or S2 bugs | FAIL (unknown) | No test pass, so nobody can say. Nothing has been tried on a real phone yet. |
| parity: must-haves done | FAIL | 7 of 20 must-haves done, feature score 29.9 / 100, "Not shippable yet". Missing must-haves: emoji panel and search, add/switch languages, backspace repeat, long-press popups, setup flow, voice typing. Partial: glide, multilingual, QWERTY (no caps lock), settings, toolbar. |
| rebrand sweep clean | PASS for this zip, with a catch | `sweep.py` exit 0 for "Gboard, Google Keyboard, Google LLC", `google.com`, `gboard.app`. One hit (a sentence in `README-BUILD.md`) was reworded. Catch: `replica-brand` has not run, so there is no `brand.json`, app icon, final palette or store name. The sweep only covers this zip, not the repo's other branches. |
| store listing passes | FAIL (not run) | `replica-launch` has not run; there is no `listing.json`. |
| production build passes | FAIL | Not run: no Android SDK in this session. The repo's workflow only builds debug APKs, and `app/build.gradle` targets API 28 (see below). |
| privacy policy and terms live | FAIL | None exist. Google Play requires a privacy policy link in the Play Console and in the app, even for apps that touch no user data. |
| account deletion works | PASS (n/a) | There are no accounts. The Dictionary screen deletes learned words and shortcuts. |
| favicon, titles, OG image, icon are yours | FAIL (unverified) | App icon, name and palette are placeholders until `replica-brand`. |

## Blockers, in the order to fix them

1. **Which codebase is shipping?** The repo now holds two lines of work. `main` has an Android scaffold, and its README describes CrazeKeyboard as a rebranded derivative of FUTO Keyboard under the FUTO Source First License (branches `vendor/futo-keyboard-copy`, `branding/crazekeyboard-root-rebuild` and `ui/modern-visual-refresh` exist too). This zip is the clean-room build. The README says the FUTO derivative keeps FUTO's application ID and payment screens, limits modification and redistribution to non-commercial use, and cannot be installed next to the upstream app. I have not read the license text (it is not on `main`) and I am not a lawyer. Before any store upload: read `LICENSE.md`, and ask FUTO directly whether a Play Store listing under that application ID and with those terms is allowed. Treat that as unknown until they answer. The clean-room build has no such limits, but it is far from finished (blocker 2).
2. **Finish the must-haves** for whichever build ships, then run `replica-test`, `replica-brand` and `replica-launch`, and re-run this gate.
3. **Raise the target SDK.** Google Play requires new apps and updates to target Android 16 (API level 36) or higher; the repo's `app/build.gradle` has `targetSdk 28` and `compileSdk 35`. Expect to bump `compileSdk` and `targetSdk` to 36 and to upgrade the Android Gradle Plugin and Gradle to versions that support API 36 (check the AGP release notes; the pinned Gradle 8.9 is likely too old). Targeting a newer API changes behaviour: edge-to-edge drawing is enforced, so `DictionaryActivity` and `MainActivity` need inset handling, and back handling moves to the predictive-back APIs. Test the keyboard on an Android 16 device after the bump.
4. **Privacy policy page** at a public URL you control (GitHub Pages is enough). It must name the developer, say the dictionary stays on the device, list permissions (none beyond the keyboard service) and explain how to delete learned words. I can draft it.
5. **Release signing and an app bundle.** The workflow builds debug APKs. Play needs a signed Android App Bundle (`bundleRelease`). See "Release signing" below.
6. **Play developer account and tester rule.** See "Play Console steps".

## Production (Android version of the checklist)

| item | status | note |
| --- | --- | --- |
| separate production database, backups | n/a | No server or database. The dictionary is a private file on the device; `allowBackup` is off, so it is not copied to cloud backups. |
| env vars | n/a | Only signing secrets (names below). |
| Stripe live | n/a for the clean-room build | No payments. The FUTO derivative retains upstream payment screens, which is a separate question for FUTO. |
| OAuth redirects | n/a | No sign-in. |
| email sending domain | n/a | No email. |
| permissions | OK as last seen | The manifest has no `INTERNET` permission, which is what lets the privacy policy say nothing leaves the phone. Adding analytics, crash reporting, a GIF provider or cloud voice would change that and the Data safety form. |

## Release signing (you do these steps; I never touch keys)

1. On your computer, create an upload key and keep it plus its password somewhere safe (losing it is painful):
   ```bash
   keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 upload.jks > upload.jks.b64      # macOS: base64 -i upload.jks -o upload.jks.b64
   ```
2. In the GitHub repo, Settings, Secrets and variables, Actions, add these four secrets: `KEYSTORE_BASE64` (contents of `upload.jks.b64`), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Use Play App Signing when Play Console offers it, so Google holds the app signing key and you hold only the upload key.
4. In `app/build.gradle`, inside `android { }`:
   ```groovy
   signingConfigs {
       release {
           storeFile file(System.getenv('KEYSTORE_PATH') ?: 'missing.jks')
           storePassword System.getenv('KEYSTORE_PASSWORD')
           keyAlias System.getenv('KEY_ALIAS')
           keyPassword System.getenv('KEY_PASSWORD')
       }
   }
   buildTypes {
       release {
           signingConfig signingConfigs.release
           minifyEnabled false
       }
   }
   ```
5. Add `.github/workflows/release.yml`:
   ```yaml
   name: Release bundle
   on: workflow_dispatch
   jobs:
     bundle:
       runs-on: ubuntu-latest
       steps:
         - uses: actions/checkout@v4
         - uses: actions/setup-java@v4
           with: { distribution: temurin, java-version: '17' }
         - name: Decode upload keystore
           run: echo "$KEYSTORE_BASE64" | base64 -d > "$RUNNER_TEMP/upload.jks"
           env: { KEYSTORE_BASE64: "${{ secrets.KEYSTORE_BASE64 }}" }
         - name: Run dictionary tests
           run: sh run-core-tests.sh
         - uses: gradle/gradle-build-action@v3
           with:
             gradle-version: '8.9'          # use the version your AGP requires after the API 36 upgrade
             arguments: --no-daemon bundleRelease
           env:
             KEYSTORE_PATH: ${{ runner.temp }}/upload.jks
             KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
             KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
             KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
         - uses: actions/upload-artifact@v4
           with:
             name: CrazeKeyboard-release-aab
             path: app/build/outputs/bundle/release/*.aab
             if-no-files-found: error
             retention-days: 14
   ```
   The `.aab` it produces is what you upload to Play Console.

## Play Console steps (you do these; about three weeks for a new personal account)

- [ ] Create a Google Play developer account (one-time fee, identity verification). Account type matters: for a personal account created after 13 November 2023, Google requires a closed test with at least 12 testers opted in for 14 continuous days before you can apply for production access; the clock does not restart when you upload a new build, but losing testers can break it, so recruit more than 12.
- [ ] Create the app, upload the signed `.aab` to **Internal testing** first, then a **Closed testing** track.
- [ ] App content: privacy policy URL, Data safety form (fill it from what the app really does: on-device dictionary, no network; read Play's definition of "collected" in the form's help text before answering), content rating questionnaire, target audience, ads declaration (none).
- [ ] Store listing from `replica-launch` (name, description, screenshots, icon).
- [ ] After the 14 days: apply for production access, answer the questionnaire, then submit for review.
- [ ] Keyboard-specific: in the first review, expect questions about the privacy of typed text. Keep the privacy policy, the Data safety form and the manifest consistent with each other.

## Domain

Not needed for the app. The only public page you need is the privacy policy (and later, a support contact). If you want your own domain for it: a CNAME for `www` pointing at your host's target and, if the host asks, a TXT verification value. No email DNS (SPF, DKIM, DMARC) is needed because the app sends no email.

- [ ] privacy policy URL live over HTTPS: {{url}}
- [ ] support email address listed in the store listing: {{address}}

## Watch (first week after a release)

- [ ] Play Console **Android vitals**: crash rate and ANRs by device and Android version, especially in editors with unusual input types.
- [ ] Reviews and feedback about autocorrect (too eager or too timid) and glide accuracy: both thresholds are starting values.
- [ ] Typing latency on low-end phones (suggestions run after each key press).
- [ ] Dictionary file size over time (`files/dictionary_<lang>.txt`; capped at 30,000 words and 80,000 word pairs per language).
- [ ] Core flow on a real phone, from scratch: enable the keyboard, type 6 to 8 sentences, check suggestions, autocorrect and its Backspace undo, glide, a shortcut, a password field (nothing learned), Bengali if shipping it, delete all words. Then repeat on a second, different phone.
- Not used on purpose: uptime checks and web analytics (no server), error tracking SDKs (they need the `INTERNET` permission and a Data safety update). Use Play Console vitals, or add opt-in crash reporting later as a deliberate change.

## Decision for you

Tell me which path to take and I will continue from there:
1. **Clean-room build:** finish the must-haves, then run test, brand, launch, then this gate again.
2. **FUTO-derived build:** get FUTO's answer on the license and application ID first; I can draft that message.
3. **Beta APKs to a few testers outside the Play Store:** possible once the build is tried on a device. It is not a public launch, so Play's rules do not apply, but the privacy and licensing points still do.
