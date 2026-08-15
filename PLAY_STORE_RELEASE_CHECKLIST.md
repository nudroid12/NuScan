# NuScan release checklist

- [ ] Test Scan, Image to PDF, Merge, Split, PDF to Image, Compress, OCR, Sign, Protect and QR tools on real devices.
- [ ] Test the M9 Scan Quality Engine on multiple documents and lighting conditions.
- [ ] Test onboarding with gesture navigation and 3-button navigation.
- [ ] Test light and dark themes.
- [ ] Test small-screen and large-screen phones.
- [ ] Test large images and multi-page PDFs for memory pressure and clear failure messages.
- [ ] Test updater check, download verification and Android installer hand-off with two permanently signed builds.
- [ ] Back up the permanent direct-distribution signing key in at least two private locations.
- [ ] Publish and verify the final privacy policy.
- [ ] Confirm package name `com.nudroidlabs.nuscan`, version code and target SDK before every release.
- [ ] If NuScan later moves to Google Play, review Play policy for `REQUEST_INSTALL_PACKAGES` and remove the direct APK updater from the Play-distributed variant if required.
