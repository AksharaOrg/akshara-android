plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
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
        versionCode = 5
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
    testImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
