# NuScan M7.1

M7.1 is a release-fix patch on top of M7.

## Changes

- Fixes the release R8 failure caused by PdfBox-Android's optional JP2/JPX dependency.
- Keeps the debug APK artifact available even when the release AAB build fails.
- Uploads the release AAB only when the release build succeeds.
- Keeps diagnose output available on every run.
- Replaces deprecated `CallMerge` icon usage with the AutoMirrored variant.
- Bumps the app to `0.7.1-m7.1` with versionCode `8`.

## Release note

NuScan does not bundle the old optional JP2Android dependency. PdfBox-Android documents JPX support as optional and skips JPX images when the decoder is absent. The targeted R8 `-dontwarn com.gemalto.jp2.**` rule therefore suppresses only that known optional dependency warning.
