# NuScan

Developer: NudroidLabs

NuScan is an Android document utility focused on private, on-device processing.

## Milestone M1

Working in this milestone:

- Home, Documents, Tools and Settings navigation
- Multi-image picker
- Page reordering
- Image to PDF conversion using Android `PdfDocument`
- EXIF rotation handling
- Local PDF library
- Open, share and delete created PDFs
- GitHub Actions debug APK build
- Diagnostic ZIP on every CI run

Planned next:

- M2: scanner, crop and filters
- M3: merge, split and PDF to image
- M4: compression and OCR
- M5: signatures, PDF protection and QR tools
- M6: monetisation and release polish
- M7: Play Store preparation

## Build

```bash
./gradlew assembleDebug
```

The app package is `com.nudroidlabs.nuscan` and the minimum Android version is Android 8.0, API 26.
