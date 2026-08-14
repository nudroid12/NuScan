# NuScan

NuScan is an Android document and PDF toolkit by NudroidLabs.

## M3

M3 keeps the M2 scanner and Image to PDF workflow, then turns on the core PDF toolbox.

Implemented:

- Home, Documents, Tools and Settings navigation
- Multi-image to PDF with page reordering
- Document scanner with auto detection, crop, rotate and filters
- Local PDF library with open, share and delete
- Merge two or more PDFs in a user-defined order
- Split PDF into one PDF per page
- Split PDF with custom groups such as `1-3, 4, 5-8`
- Export every PDF page to PNG or JPEG
- User-selected export folder for PDF to image
- Local PDF processing for merge, split and rendering
- GitHub Actions build and diagnostic bundle

Merge and split use PdfBox-Android. PDF page rendering uses Android PdfRenderer.

Package: `com.nudroidlabs.nuscan`

Version: `0.3.0-m3`
