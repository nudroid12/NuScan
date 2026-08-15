# NuScan update system

NuScan checks `update.json` from the main branch of the official repository.

A published update entry contains:

- `versionCode`: must be greater than the installed build.
- `versionName`: user-visible release version.
- `apkUrl`: HTTPS URL of the signed APK asset in GitHub Releases.
- `sha256`: SHA-256 checksum of that APK.
- `changelog`: text shown in NuScan before download.
- `mandatory`: reserved for future use. M10 never forces an update.

Before Android opens the installer, NuScan verifies the downloaded file's SHA-256, package name, version code and signing certificate.

## Permanent signing requirements

The first APK distributed publicly must be signed with the permanent NuScan release key. Every later public APK must use the same signing key and the same application ID: `com.nudroidlabs.nuscan`.

The GitHub workflow expects these repository Actions secrets:

- `NUSCAN_KEYSTORE_BASE64`
- `NUSCAN_KEYSTORE_PASSWORD`
- `NUSCAN_KEY_ALIAS`
- `NUSCAN_KEY_PASSWORD`

Never commit the keystore or passwords to the repository.

Once these secrets exist, run the M10 workflow with `publish_release=true`. It builds the signed APK, verifies it, uploads it to GitHub Releases, calculates SHA-256, and updates `update.json` automatically.

Permanent release certificate SHA-256 fingerprint for this project:

`F7:FD:ED:70:B6:38:E8:49:C2:95:9E:B6:1E:EF:45:3A:82:E6:DB:1E:F5:E4:93:D3:B8:37:CF:B6:EA:F0:81:F8`

The release workflow verifies the signed APK against this fingerprint to catch accidental signing with a different key.
