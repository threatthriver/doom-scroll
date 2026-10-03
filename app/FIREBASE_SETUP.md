# Firebase configuration & secrets

`app/google-services.json` is **not** committed to this repository. It holds project-specific
identifiers and the Android API key, and config files like this should stay out of version
control. The build fails fast (see `app/build.gradle.kts`) if the file is missing, with a
pointer back to these instructions.

## 1. Get `google-services.json` for local builds

1. Open the [Firebase console](https://console.firebase.google.com/) and select the project.
2. Project settings → Your apps → the Android app (`com.securemessage.app`).
3. Download `google-services.json` and place it at `app/google-services.json`.

`app/google-services.json.template` shows the expected shape with placeholder values.

## 2. CI / other machines (no committed file)

Store the config as an encrypted secret instead of committing it:

```bash
# One time, on a machine that has the real file:
./scripts/encode-google-services.sh | pbcopy      # copies base64 to clipboard
# Save it as a CI secret named GOOGLE_SERVICES_JSON_BASE64 (e.g. GitHub Actions repo secret).
```

Then, as the first build step in CI:

```bash
export GOOGLE_SERVICES_JSON_BASE64="${{ secrets.GOOGLE_SERVICES_JSON_BASE64 }}"
./scripts/decode-google-services.sh               # writes app/google-services.json
./gradlew :app:assembleRelease
```

Locally you can keep these in a git-ignored `.env` (see `.env.example`).

## 3. Create / rotate the Android API key (console — do this if the old key leaked)

The old key `AIzaSyB5h-...` was committed to git history, so treat it as compromised and
replace it:

1. **Google Cloud Console → APIs & Services → Credentials.**
2. Create a new **API key** (or edit the existing Android key).
3. **Application restrictions → Android apps:** add package name `com.securemessage.app` and the
   SHA-1 of each signing certificate (debug and release). Get SHA-1 with:
   ```bash
   ./gradlew :app:signingReport
   ```
4. **API restrictions → Restrict key** to only the APIs the app uses:
   *Identity Toolkit API* (Firebase Auth) and *Cloud Firestore API*.
5. Download a fresh `google-services.json` (Firebase console → project settings) so it carries
   the new key, replace `app/google-services.json`, and update the CI secret via
   `./scripts/encode-google-services.sh`.
6. Delete/disable the old unrestricted key once the new one is confirmed working.

## 4. Why the API key is not your main defense

A Firebase Android API key ships inside every APK by design, so it is a client identifier, not a
password. Real access control comes from **Firestore Security Rules** (`firestore.rules`) and,
recommended, **Firebase App Check**. Restricting the key (step 3) limits abuse; it is not what
keeps your data private.

## Never commit these

Service-account keys, keystores (`*.jks`, `*.keystore`), credential JSON, and `.env` files are
real secrets and must never be committed. They are covered by `.gitignore`.
