# NuScan 0.7.2 Scan Fix

This patch only changes the document-scanning pipeline.

- Replaces the Google document-scanner viewfinder with NuScan's CameraX capture flow.
- Live OpenCV four-corner detection.
- Auto capture requires a sufficiently large document, stable corners and a sharp frame.
- Full-quality JPEG capture uses CameraX MAXIMIZE_QUALITY and JPEG quality 100.
- After capture, NuScan detects corners again on the full image and applies a perspective transform.
- Mild sharpening is applied only after perspective correction.
- If automatic corners are not reliable, the review screen opens four-corner manual adjustment.
- Multi-page PDF creation remains supported.
- All unrelated M7.1 features are unchanged.
