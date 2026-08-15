# NuScan 1.0 RC1

M8 turns the stable M7.1 baseline into a simpler, free-first release candidate.

## Product direction

- All document tools are free. There is no Pro plan or Play Billing dependency.
- The stable Google Play services document scanner from M7.1 is retained.
- The experimental CameraX/OpenCV scanner from M7.2 is not included.
- Ads remain light and consent-aware. Development builds use Google test IDs.

## M8 UX changes

- Safer edge-to-edge layout using system drawing insets.
- Onboarding now respects both status and navigation bars.
- Onboarding spacing is compact and the Next/Start action stays above system navigation.
- Home is simplified around Scan plus four common quick tools.
- Scan Document is compact and puts Start scanning immediately below the PDF name.
- Tools and Settings copy is simplified and all premium language is removed.
- Bottom navigation uses Material 3 NavigationBar.

## Included tools

Document scanner, Image to PDF, Merge PDF, Split PDF, PDF to Image, Compress PDF, OCR, Sign PDF, Protect PDF and QR tools.

## Build

Version: `1.0.0-rc1`
Version code: `11`
Package: `com.nudroidlabs.nuscan`
Target SDK: `36`


## M9 Scan Quality Engine v1

The stable Google ML Kit scanner remains responsible for capture, auto crop, perspective correction, rotation, filters and multi-page scanning. NuScan now consumes the JPEG page results and builds its own PDF after a conservative on-device enhancement pass for white balance, uneven lighting, soft shadows, local contrast and controlled sharpening. If enhancement fails, NuScan falls back to the original scanner PDF.
