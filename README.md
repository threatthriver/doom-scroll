# SenderWire — Release Channel

This repository hosts the published **SenderWire** Android app (native Kotlin build,
package `com.senderwire.kt`) for direct (sideload) distribution. It is not the source code.

1. **APK releases** — each version is a [GitHub Release](../../releases) with the signed `.apk` attached.
2. **A version manifest** — [`latest-kt.json`](./latest-kt.json) describes the newest release so the
   app can check for updates in-app.

## In-app updates

The app reads `latest-kt.json` when it opens (and when it returns to the foreground, at most every
few hours). When the manifest's `versionCode` is higher than the installed build, it offers to
download the APK from the release and open the system installer. A build below
`minSupportedVersionCode` must update before it can be used.

## `latest-kt.json` format

```json
{
  "versionCode": 1,
  "versionName": "0.1.0",
  "apkUrl": "https://github.com/threatthriver/doom-scroll/releases/download/kt-v0.1.0/SenderWire-Kotlin-0.1.0.apk",
  "notes": "What changed in this release.",
  "minSupportedVersionCode": 1,
  "publishedAt": "2026-10-20T00:00:00Z"
}
```
