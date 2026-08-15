# NuScan M4

NuScan is an offline-first Android document toolkit by NudroidLabs.

## M4 features

- Document scanner with crop, rotate and scan filters
- Image to PDF
- Merge PDF
- Split PDF by page or custom groups
- PDF to PNG/JPEG
- PDF compression with High quality, Balanced and Small file presets
- OCR for images and PDFs using the bundled ML Kit Latin-script text model
- Copy, share and save OCR output as TXT
- Local Documents library for PDFs created by NuScan

## Compression note

M4 compression is designed primarily for scanned and photo-based PDFs. It renders each page and rebuilds it with JPEG compression. This can substantially reduce scan size, but the output is flattened, so original searchable text, links, forms, annotations and digital signatures are not preserved.

## Privacy

Document processing is designed to happen on the device. The document scanner is provided through Google Play services and may download scanner components on first use. The M4 OCR Latin model is bundled with the APK.

Package: `com.nudroidlabs.nuscan`

Version: `0.4.0-m4`
