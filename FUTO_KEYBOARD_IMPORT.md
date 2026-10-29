# FUTO Keyboard source snapshot

This repository includes a source-tree snapshot of FUTO Keyboard in [`futo-keyboard/`](futo-keyboard/). It is a separate Android Gradle project; the root CrazeKeyboard app remains unchanged and does not build this project as a module.

The snapshot was taken from the official [FUTO Keyboard source repository](https://github.com/futo-org/android-keyboard) at commit `31e720ddcc2df6259069b2ae7cf0f1defaf2cb74`. The following upstream submodule checkouts are included as ordinary files in the snapshot:

- `java/assets/futo-swipe`: `93931877c0083d0de7f51fad82325dcbe10421bf`
- `java/assets/layouts`: `fb4dad270790d980c32417b60359104bd0c32c1c`
- `java/assets/themes`: `4799ad5a597522d42d4cdfe34cdaf064aa0c434c`
- `java/res-large`: `d87d9dbdf3966bbe18413be375dab2f6c7bbdfdd`
- `libs`: `be4362cf82022e613cd34ff95d579c8502eaad8e`
- `translations`: `8cd47ae043e8d9fe23878ac7e473f4817b9e2681`
- `voiceinput-shared/src/main/ml`: `7692bc6096c51c44beba716a3c429ff09dbfffd9`

The upstream Git history is not copied. The source files, checked-out submodule contents, upstream `LICENSE.md`, `NOTICE` files, and other included notices are retained. No FUTO source files were intentionally modified in this import.

FUTO Keyboard is licensed under the FUTO Source First License 1.1-kb; its license text is in [`futo-keyboard/LICENSE.md`](futo-keyboard/LICENSE.md). That license permits use for any purpose, but limits modifications to non-commercial purposes and redistribution to free, non-commercial purposes. It also requires preserving the licensor's notices and payment-related functionality, and requires a prominent notice in modified copies. Review the included license before modifying or redistributing this snapshot.
