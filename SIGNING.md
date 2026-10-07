# Shattered Anki Dungeon signing

User-installable Android APKs must use the project's persistent signing key.

## Required GitHub Actions secrets

- `SAD_SIGNING_KEYSTORE_B64`
- `SAD_SIGNING_STORE_PASSWORD`
- `SAD_SIGNING_KEY_ALIAS`
- `SAD_SIGNING_KEY_PASSWORD`

When these secrets are available, the Android workflow reconstructs the keystore in the runner's temporary directory and Gradle signs both debug and release variants with the same persistent key.

The installable artifact is named:

`shattered-anki-dungeon-stable-signed-debug`

If the secrets are unavailable, CI still compiles with Android's generated debug key for validation, but the artifact is deliberately named:

`shattered-anki-dungeon-ephemeral-debug-DO-NOT-INSTALL`

Do not distribute or install that ephemeral artifact as an update.

## Permanent certificate

Expected SHA-256 signing-certificate fingerprint:

`2F:C1:5B:58:1E:6C:AD:5A:95:0A:3D:6A:D0:17:4C:40:7F:30:BF:A9:3C:06:C1:DB:F2:69:DA:92:DA:93:C0:5E`

Alias:

`shattered-anki-dungeon`

Back up the private keystore and its passwords securely. Android package updates require the same application ID and signing key; if this key is lost, existing installations signed with it cannot be updated in place.
