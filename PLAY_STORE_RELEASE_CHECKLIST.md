# NuScan Play Store Release Checklist

## Build and signing

- [ ] Enrol the app in Play App Signing.
- [ ] Create and securely store an upload key.
- [ ] Sign the release Android App Bundle with the upload key.
- [ ] Confirm package name is `com.nudroidlabs.nuscan`.
- [ ] Confirm target SDK is 36 or higher.
- [ ] Test the release candidate through Play internal testing before production.

## Monetisation

- [ ] Create the one-time product `nuscan_pro_lifetime` in Play Console.
- [ ] Set the product price and activate its purchase option.
- [ ] Replace Google sample AdMob IDs with NudroidLabs production IDs.
- [ ] Create the required Privacy and messaging consent messages in AdMob.
- [ ] Test purchase, restore, pending purchase and acknowledgement flows with Play licence testers.
- [ ] Confirm Pro users do not see banner ads.

## Privacy and policy

- [ ] Publish a public privacy policy URL.
- [ ] Complete the Play Console Data safety form based on the final SDK behaviour.
- [ ] Disclose Google Mobile Ads and Google Play Billing data practices accurately.
- [ ] Confirm scanned documents and OCR content are processed locally by NuScan app code.
- [ ] Verify all third-party SDK disclosures against their current documentation before submission.

## Store listing

- [ ] Finalise app icon, feature graphic and screenshots.
- [ ] Finalise short and full descriptions.
- [ ] Add support contact details.
- [ ] Complete content rating and app access declarations.
- [ ] Test on multiple Android versions and screen sizes.
