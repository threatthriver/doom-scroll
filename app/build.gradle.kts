import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
}

// --- Firebase config preflight -------------------------------------------------------------
// google-services.json is intentionally NOT committed (it carries project-specific identifiers
// and the Android API key). Fail early with actionable guidance instead of a cryptic plugin
// error if someone checks out the repo without providing it. In CI the file can be materialised
// from a base64 secret via scripts/decode-google-services.sh before the build runs.
val googleServicesFile = file("google-services.json")
if (!googleServicesFile.exists()) {
    throw GradleException(
        """
        Missing app/google-services.json.

        This file is git-ignored on purpose. To build:
          1. Firebase console -> Project settings -> Your apps -> Android app (com.securemessage.app)
          2. Download google-services.json into the app/ directory.
        See app/FIREBASE_SETUP.md. A placeholder shape is in app/google-services.json.template.
        """.trimIndent(),
    )
}
// -------------------------------------------------------------------------------------------

// --- Release signing inputs -----------------------------------------------------------------
val hushKeystoreProps = Properties().apply {
    listOfNotNull(
        System.getenv("HUSH_KEYSTORE_PROPERTIES")?.let { File(it) },
        rootProject.file("keystore.properties"),
        File(System.getProperty("user.home"), ".hush-signing/keystore.properties"),
    ).firstOrNull { it.isFile }?.inputStream()?.use { load(it) }
}
val useHushReleaseKey: Boolean =
    providers.gradleProperty("hushReleaseSigning").orNull == "true" ||
        System.getenv("HUSH_RELEASE_SIGNING") == "true"
// -------------------------------------------------------------------------------------------

android {
    namespace = "com.securemessage.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.securemessage.app"
        minSdk = 24
        targetSdk = 35
        // Keep this in sync with the GitHub release tag (v1.5.0). The updater compares
        // this version with the latest release tag, so a mismatch makes it offer the
        // same update again and again.
        versionCode = 16
        versionName = "1.5.0"

        // Public URL of the free Cloudflare Worker that sends push alerts (not a secret: it only
        // acts for callers holding a valid Firebase ID token). Blank disables push. Override with
        // -PhushPushEndpoint=... for a test relay.
        val pushEndpoint = providers.gradleProperty("hushPushEndpoint").orNull
            ?: "https://hush-push.igitgamerz38.workers.dev/v1/notify"
        buildConfigField("String", "PUSH_ENDPOINT", "\"$pushEndpoint\"")
    }

    signingConfigs {
        // The real release key lives OUTSIDE the repo (default ~/.hush-signing/keystore.properties,
        // or the file named by $HUSH_KEYSTORE_PROPERTIES, or ./keystore.properties which is
        // git-ignored). It is only used when you opt in with -PhushReleaseSigning=true, see below.
        create("hushRelease") {
            val props = hushKeystoreProps
            val storePath = props.getProperty("storeFile")
            if (storePath != null) {
                storeFile = file(storePath)
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Which key signs the release APK:
            //  * default: the debug key. Phones that already have a debug-signed install can keep
            //    updating in place (Android only allows updates signed with the same key).
            //  * -PhushReleaseSigning=true (or HUSH_RELEASE_SIGNING=true): the dedicated Hush
            //    release key. Switching to it is a ONE-WAY MIGRATION: existing installs must be
            //    uninstalled first (signature mismatch), which also deletes their local E2EE keys.
            signingConfig = if (useHushReleaseKey) {
                check(hushKeystoreProps.getProperty("storeFile") != null) {
                    "Release signing requested but no keystore.properties was found. " +
                        "Expected ~/.hush-signing/keystore.properties (or HUSH_KEYSTORE_PROPERTIES)."
                }
                signingConfigs.getByName("hushRelease")
            } else {
                signingConfigs.getByName("debug")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.messaging)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.kotlinx.coroutines.play.services)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.bouncy.castle)
    implementation(libs.androidx.biometric)
    // Pinned so lintVitalRelease (InvalidFragmentVersionForActivityResult) is happy
    // with ComponentActivity's registerForActivityResult on all build types.
    implementation(libs.androidx.fragment.ktx)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
