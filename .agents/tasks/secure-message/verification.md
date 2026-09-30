# Verification — iteration 1

## Environment
- JDK: Homebrew OpenJDK 17.0.20
- Android SDK: `ANDROID_HOME` unset and `~/Library/Android/sdk` absent, but a Homebrew SDK exists at `/opt/homebrew/share/android-commandlinetools` (platforms android-35, build-tools 35.0.0, licenses accepted). `local.properties` (gitignored) points `sdk.dir` there.
- Gradle wrapper 8.11.1, generated with the official distribution (`gradle wrapper --gradle-version 8.11.1`); `./gradlew --version` → Gradle 8.11.1.
- Versions: all locked versions from design §2 resolved; no bumps.

## Commands and outcomes
1. `./gradlew clean assembleDebug testDebugUnitTest --console=plain` → **BUILD SUCCESSFUL** (1m 3s).
   - APK: `app/build/outputs/apk/debug/app-debug.apk` (14.8 MB).
   - Unit tests: 28 tests, 0 failures/errors (`app/build/test-results/testDebugUnitTest/*.xml`). Suites: ChatIdsTest, ValidationTest, AuthViewModelTest, ConversationsViewModelTest, UserSearchViewModelTest, ChatViewModelTest.
2. `firebase deploy --only firestore --project secure-message-app-0602 --non-interactive` → exit 0, "Deploy complete!".
   - Rules compiled and released (hardened `users` create/update=false, new `usernames` block, `chats` unchanged).
   - Indexes: `firestore.indexes.json` (empty) deployed. CLI reported 7 pre-existing indexes in the project that aren't in the file; they were **not** deleted (no `--force`).
   - Note: my first attempt without `--non-interactive` hung for 300 s and was killed; the retry succeeded.

## Not verified (need a device/emulator + live project)
- Manual ACs 3, 5–12, 14, 15 (sign-up doc shape, username-taken flow, realtime delivery, rules denials, cold-start routing).
- `createProfile` PERMISSION_DENIED disambiguation (Firestore-bound, per design §9).

## Left for the user
- Enable the Email/Password provider in the Firebase console (Authentication → Sign-in method). Until then, sign up/in shows the Firebase error inline.
- Decide whether to remove the 7 extra indexes (`firebase deploy --only firestore:indexes --force`).
