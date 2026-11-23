plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.alterananya"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.alterananya"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "1.1"
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    // Signing: the keystore path and passwords are passed in from the outside
    // (gradle -P properties or environment). Nothing secret is stored in the project.
    val ksPath = (project.findProperty("ksPath") as String?) ?: System.getenv("ALTER_KS_PATH")
    val ksPass = (project.findProperty("ksPass") as String?) ?: System.getenv("ALTER_KS_PASS")
    val ksAlias = (project.findProperty("ksAlias") as String?) ?: System.getenv("ALTER_KS_ALIAS") ?: "alter"
    signingConfigs {
        if (ksPath != null && ksPass != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = ksPass
                keyAlias = ksAlias
                keyPassword = ksPass
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            if (ksPath != null && ksPass != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.security:security-crypto:1.0.0")
    implementation("com.alphacephei:vosk-android:0.3.75")
    implementation("net.java.dev.jna:jna:5.13.0@aar")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
}
