plugins {
    id("com.android.application")
    kotlin("android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.pocketpilot.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pocketpilot.app"
        minSdk = 27
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(project(":core"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")

    // On-device OCR for screens the accessibility tree cannot see (WebView, canvas).
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // Encrypted local database for memory (Room + SQLCipher).
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    implementation("net.zetetic:android-database-sqlcipher:4.5.4")
    ksp("androidx.room:room-compiler:2.6.1")

    // --- Experimental 2026 integrations (add when building with the SDK) ---
    // Google ADK for Android (agent loop, v0.1.0, May 2026) — see docs/FEATURES.md.
    // implementation("com.google.adk:google-adk-kotlin-core-android:0.1.0")
    // ML Kit GenAI on-device speech (ASR/TTS via AICore, Nov 2026).
    // implementation("com.google.mlkit:genai-agent:1.0.0-alpha01")
    // LiteRT-LM on-device SLM runtime (Gemma .litertlm).
    // implementation("com.google.ai.edge.litert:litert-lm:0.1.0")
}
