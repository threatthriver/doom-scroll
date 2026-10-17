# SenderWire — Release Channel

This repository hosts the published **SenderWire** Android app for direct
(sideload) distribution. It is not the source code — SenderWire's code lives in
its own repository. This repo serves two things:

1. **APK releases** — each version is published as a [GitHub Release](../../releases)
   with the signed `.apk` attached as an asset.
2. **A version manifest** — [`latest.json`](./latest.json) describes the newest
   release so the app can check for updates in-app.

## In-app updates

The SenderWire app periodically reads `latest.json` from this repo. When the
manifest's `versionCode` is higher than the installed build, the app offers an
in-app update: it downloads the APK from the release and launches the system
installer. No app store required.

## `latest.json` format

```json
{
  "versionCode": 13,
  "versionName": "1.0.11",
  "apkUrl": "https://github.com/threatthriver/doom-scroll/releases/download/v1.0.11/senderwire-1.0.11.apk",
  "notes": "What changed in this release.",
  "minSupportedVersionCode": 1,
  "publishedAt": "2026-10-17T00:00:00Z"
}
```

| Field | Meaning |
|-------|---------|
| `versionCode` | Monotonic integer; the app compares this against its installed `versionCode`. |
| `versionName` | Human-readable version shown to users. |
| `apkUrl` | Direct download URL of the signed APK release asset. |
| `notes` | Release notes shown in the update prompt. |
| `minSupportedVersionCode` | Builds below this are forced to update (optional). |
| `publishedAt` | ISO 8601 publish timestamp. |

## Publishing a new version

1. Build and sign the release APK (same keystore every time, or Android will
   refuse the self-install update).
2. Create a GitHub Release tagged `vX.Y.Z` and attach the APK.
3. Update `latest.json` with the new `versionCode`, `versionName`, and `apkUrl`.
