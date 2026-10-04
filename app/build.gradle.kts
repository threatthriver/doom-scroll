import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

android {
    namespace = "com.securemessage.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.securemessage.app"
        minSdk = 24
        targetSdk = 35
        // Keep this in sync with the GitHub release tag (v1.2.2). The updater compares
        // this version with the latest release tag, so a mismatch makes it offer the
        // same update again and again.
        versionCode = 5
        versionName = "1.2.2"
    }

    buildTypes {
        release {
            // Signed with the debug key on purpose: the phone already has a debug-signed
            // install, and Android only allows in-place updates when the signing key matches.
            // This keeps the GitHub in-app updater working without an uninstall/reinstall.
            // For a true public release, swap in a dedicated upload/release keystore (which
            // would require users to reinstall once).
            signingConfig = signingConfigs.getByName("debug")
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
    implementation(libs.kotlinx.coroutines.play.services)

    // Security
    implementation(libs.androidx.security.crypto)
    implementation(libs.bouncy.castle)
    implementation(libs.androidx.biometric)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
