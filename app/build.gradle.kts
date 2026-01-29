plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    // Apply the Google Services plugin
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "net.ericclark.studiare"
    compileSdk = 35

    defaultConfig {
        applicationId = "net.ericclark.studiare"
        minSdk = 24
        //noinspection OldTargetApi,ExpiredTargetSdkVersion
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0 Alpha-2026.01.24"

        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
            abiFilters.add("x86")
            abiFilters.add("x86_64")
        }

        // Automatically add the build timestamp to the BuildConfig file
        android.buildFeatures.buildConfig = true
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            // Add these excludes for Ktor/Coroutines
            excludes += "META-INF/INDEX.LIST"
            excludes += "META-INF/io.netty.versions.properties"
            excludes += "META-INF/DEPENDENCIES"
        }
    }
}

dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended-android:1.6.8")

    // Add the splash screen dependency
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")
    implementation(libs.androidx.runtime.livedata)


    // --- Firebase Dependencies ---
    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:32.7.2"))
    // Add the dependencies for Firebase products you want to use
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Google Sign In
    implementation("com.google.android.gms:play-services-auth:21.0.0")


    // OpenCSV dependency for CSV import/export
    implementation("com.opencsv:opencsv:5.9")
    implementation(libs.androidx.compose.foundation)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // https://mvnrepository.com/artifact/com.bihe0832.android/lib-sherpa-onnx
    // implementation("com.bihe0832.android:lib-sherpa-onnx:8.0.1")
    //implementation("com.k2fsa.sherpa.onnx:sherpa-onnx")
    implementation("com.github.k2-fsa:sherpa-onnx:master-SNAPSHOT")

    // --- Ktor Client (For downloading models) ---
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")

    // --- Coroutines (Ensure these are present) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("org.apache.commons:commons-compress:1.26.0")

    implementation("com.google.firebase:firebase-crashlytics-ktx")
    implementation("com.google.firebase:firebase-analytics-ktx")
}