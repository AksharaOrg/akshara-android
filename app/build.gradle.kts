plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

android {
    namespace = "org.akshara.ime"
    compileSdk = 36

    val uploadKeystore = rootProject.file("akshara-upload.jks")
    val uploadPasswordFile = rootProject.file(".akshara-upload-password")

    signingConfigs {
        create("release") {
            if (uploadKeystore.exists() && uploadPasswordFile.exists()) {
                val uploadPassword = uploadPasswordFile.readText().trim()
                storeFile = uploadKeystore
                storePassword = uploadPassword
                keyAlias = "upload"
                keyPassword = uploadPassword
            }
        }
    }

    defaultConfig {
        applicationId = "lk.org.akshara.keyboard"
        minSdk = 26
        targetSdk = 36
        versionCode = 29
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            // R8 removes unused code and resources (Compose in Settings would otherwise add several MB)
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        // Build types the Baseline Profile plugin uses to profile the app on a device; never published, so
        // the debug key will do on machines without the upload keystore
        for (name in listOf("nonMinifiedRelease", "benchmarkRelease")) {
            maybeCreate(name).signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures { buildConfig = true; compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation("androidx.autofill:autofill:1.3.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    // Settings UI. BOM 2026.01.00 = Material3 1.4.0 + Compose UI 1.10, the newest that runs on AGP 8.7 and
    // compileSdk 36; activity-compose 1.11+ needs AGP 8.9. The keyboard itself never loads Compose.
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    // Installs the bundled Baseline Profile, so the keyboard's hot code is compiled ahead of first use
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":baselineprofile"))
    testImplementation(platform("androidx.compose:compose-bom:2026.01.00"))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}

tasks.withType<org.gradle.api.tasks.testing.Test>().configureEach {
    maxHeapSize = "1g"
}
