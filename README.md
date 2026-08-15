# NuScan 1.0 RC3 / M10

NuScan is a free, simple Android document scanner and PDF toolkit by NudroidLabs.

M10 keeps the M9 Scan Quality Engine v1 and adds the public-distribution foundation:

- no ads or UMP consent SDK
- no Play Billing or premium gates
- built-in update checker in Settings
- optional automatic update check at app launch
- GitHub-hosted update metadata
- signed APK download with progress
- SHA-256, package, version and signing-certificate verification before installation
- Android installer hand-off for in-place updates
- release signing configuration that keeps private keys outside the repository
- GitHub Release publishing support in the M10 workflow

The first public APK must be signed with the permanent NuScan release key. Do not distribute debug APKs as the public build.
