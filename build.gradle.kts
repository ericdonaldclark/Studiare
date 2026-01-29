// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.2" apply false
    //id("org.jetbrains.kotlin.android") version "1.9.22" apply false
    //id("com.google.devtools.ksp") version "1.9.22-1.0.17" apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    // Add Google Services plugin for Firebase
    id("com.google.gms.google-services") version "4.4.1" apply false
    id("com.google.firebase.crashlytics") version "3.0.6" apply false
}