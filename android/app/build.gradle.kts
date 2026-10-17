plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Step 7: Firebase. The plugin reads app/google-services.json; without that file the app still builds (sign-in just says "not configured").
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

android {
    namespace = "com.intelliq.rifatplex"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.intelliq.rifatplex"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    // One fixed key (not the CI runner's random debug key), so the SHA-1 registered in Firebase never changes.
    signingConfigs {
        create("shared") {
            storeFile = file("intelliq-shared.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Release build = much faster than debug (R8 shrinking + optimised Compose). Signed with the fixed shared key so it installs directly.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("shared")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        // Strong skipping: a question card is not redrawn on every keystroke unless its own data changed.
        freeCompilerArgs += listOf("-P", "plugin:androidx.compose.compiler.plugins.kotlin:experimentalStrongSkipping=true")
        // HorizontalPager / PagerState are @ExperimentalFoundationApi (IntelliQApp.kt). Opt in once for the whole module.
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
    }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-ktx:1.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    // Step 7: Google sign-in (Credential Manager) + Firestore
    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.2.2")
    implementation("androidx.credentials:credentials-play-services-auth:1.2.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Step 4.2: QR code. ZXing core encodes the code on the sheet now and reads it from the photo in step 4.5.
    // Pure Java, works offline, no Google Play services.
    implementation("com.google.zxing:core:3.5.3")
    // Live camera guidance (OMR scan)
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    // CameraX returns ListenableFuture (ProcessCameraProvider.getInstance). Kotlin needs the class on the classpath.
    implementation("com.google.guava:guava:33.2.1-android")

    // Step 4.1: JVM tests for the OMR geometry (run with: gradle :app:testDebugUnitTest). Not part of the APK.
    testImplementation("junit:junit:4.13.2")
    // Step 4.3: the answer snapshot is saved as JSON. Android's own org.json only exists on a phone, so the JVM tests get the real one.
    testImplementation("org.json:json:20231013")
}
