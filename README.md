# NuScan 0.7.2 Scan Fix

This patch is based on the verified M7.1 source and changes only the document-scanning pipeline.

## Scan changes

- Replaces the Google document scanner viewfinder with a NuScan CameraX capture flow.
- Live four-corner detection runs on-device with OpenCV.
- Auto capture only fires when a sufficiently large document is detected, the corners remain stable, and the frame passes a sharpness check.
- Full-quality capture uses CameraX `CAPTURE_MODE_MAXIMIZE_QUALITY` and JPEG quality 100.
- After capture, NuScan detects the paper again on the full image and applies a perspective transform before enhancement.
- A mild unsharp mask is applied after perspective correction.
- If automatic edge detection is not reliable, the review screen opens a draggable four-corner manual adjustment.
- Manual shutter, gallery import and multi-page PDF creation remain available.

All unrelated M7.1 features remain unchanged.
