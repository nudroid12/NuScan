# NuScan

NuScan is an Android document and PDF toolkit by NudroidLabs.

## M2

M2 keeps the M1 Image to PDF workflow and adds a real document scanner powered by the ML Kit Document Scanner API.

Implemented:

- Home, Documents, Tools and Settings navigation
- Multi-image to PDF
- Reorder pages before PDF creation
- Local PDF library with open, share and delete
- Document scanner with automatic detection and capture
- Crop and perspective correction inside the scanner flow
- Rotate and scan filters
- Multi-page scanning, up to 50 pages
- Gallery import into scanner
- Scanner PDF copied into NuScan Documents
- Kotlin 2.x `compilerOptions` JVM 17 configuration
- GitHub Actions build and diagnostic bundle

The scanner UI and processing are provided by Google Play services. Scanner components may need to download on first use. Document processing runs on-device.

Package: `com.nudroidlabs.nuscan`

Version: `0.2.0-m2`
