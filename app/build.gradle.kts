plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
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
        versionName = "0.1.0 Alpha-2026.07.05"

        // Automatically add the build timestamp to the BuildConfig file
        //android.buildFeatures.buildConfig = true
        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            // 64-bit only keeps the debug APK small; every native lib (mostly
            // libonnxruntime.so) is packed once per ABI.
            ndk {
                abiFilters += listOf("arm64-v8a", "x86_64")
            }
        }
        release {
            ndk {
                abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            }
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
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi"
        freeCompilerArgs += "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"

    }
    buildFeatures {
        buildConfig = true
        compose = true
        prefab = true
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

// The Sherpa-ONNX AAR bundles Windows/macOS libraries as Java resources that
// Android never loads; drop them from debug builds.
androidComponents {
    onVariants(selector().withBuildType("debug")) { variant ->
        variant.packaging.resources.excludes.add("sherpa-onnx/native/**")
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
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.window)
    implementation(libs.androidx.compose.material3.adaptive)
    implementation("androidx.compose.material3.adaptive:adaptive-layout:1.2.0")
    implementation("androidx.compose.material3.adaptive:adaptive-navigation:1.2.0")
    implementation("androidx.compose.material:material-icons-extended-android:1.6.8")
    implementation("androidx.compose.material3:material3-window-size-class")

    // Add the splash screen dependency
    implementation("androidx.core:core-splashscreen:1.0.1")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.0")

    // ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.0")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.0")
    implementation(libs.androidx.runtime.livedata)


    // --- Firebase Dependencies ---
    // Import the BoM for the Firebase platform
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    // Add the dependencies for Firebase products you want to use
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")

    // Google Sign In
    implementation("com.google.android.gms:play-services-auth:21.0.0")


    // OpenCSV dependency for CSV import/export
    implementation("com.opencsv:opencsv:5.9")
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.material3)
    implementation(libs.androidx.compose.remote.creation.core)

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.05.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // https://mvnrepository.com/artifact/com.bihe0832.android/lib-sherpa-onnx
    implementation(libs.sherpa.onnx) {
        // The published AAR's POM also pulls in the JVM-target artifact, which
        // duplicates every class already in the AAR itself.
        exclude(group = "com.github.k2-fsa.sherpa-onnx", module = "sherpa-onnx-jvm")
    }

    // --- Ktor Client (For downloading models) ---
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-android:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")

    // --- Coroutines (Ensure these are present) ---
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    implementation("org.apache.commons:commons-compress:1.26.0")

    implementation("com.google.firebase:firebase-crashlytics")
    //implementation("com.materialkolor:material-kolor:4.1.0")

    // Room DB
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler) // KSP uses the plugin you uncommented above

    // Gson (For TypeConverters)
    implementation(libs.gson)
    implementation(libs.androidx.lifecycle.process)

    // Rich Text Editor for Compose
    implementation("com.mohamedrejeb.richeditor:richeditor-compose:1.0.0-rc08")

    implementation("io.coil-kt:coil-compose:2.6.0")

    // Decompression for Anki
    implementation("com.github.luben:zstd-jni:1.5.7-8@aar")

    // AndroidX Security for EncryptedSharedPreferences
    implementation("androidx.security:security-crypto-ktx:1.1.0-alpha06")

    // Skeleton loader shimmer
    implementation("com.valentinilk.shimmer:compose-shimmer:1.2.0")

    // Installs the generated baseline profile at app install/update time
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
}
// Mode-option defaults: src/main/defaults/mode-options.json -> generated ModeOptionDefaults.kt.
// Generated constants are compile-time values, so reading them costs the same as the old literals.
val modeOptionDefaultsJson = file("src/main/defaults/mode-options.json")
val generatedModeOptionsDir = layout.buildDirectory.dir("generated/modeoptions/kotlin")

val generateModeOptionDefaults by tasks.registering {
    val sourceJson = modeOptionDefaultsJson
    val outputDir = generatedModeOptionsDir.get().asFile
    inputs.file(sourceJson)
    outputs.dir(outputDir)
    doLast {
        val root = groovy.json.JsonSlurper().parse(sourceJson) as Map<*, *>
        fun constName(key: String) = key.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").uppercase()
        fun literal(type: String, value: Any?): String = when (type) {
            "Boolean" -> value.toString()
            "Int" -> (value as Number).toInt().toString()
            "Float" -> "${(value as Number).toDouble()}f"
            "Double" -> (value as Number).toDouble().toString()
            "String" -> "\"$value\""
            "IntList" -> (value as List<*>).joinToString(", ", "listOf(", ")") { (it as Number).toInt().toString() }
            "FloatList" -> (value as List<*>).joinToString(", ", "listOf(", ")") { "${(it as Number).toDouble()}f" }
            else -> error("Unsupported type $type")
        }
        fun kotlinType(t: String): String = when (t) {
            "IntList" -> "List<Int>"
            "FloatList" -> "List<Float>"
            else -> t
        }

        // _options: every option's definition (name, type, default, optional min/max/step).
        val optionDefs = (root["_options"] as List<*>).map { it as Map<*, *> }
        val typeByName = optionDefs.associate { (it["name"] as String) to (it["type"] as String) }
        val body = StringBuilder()
        optionDefs.forEach { entry ->
            val key = entry["name"] as String
            val type = entry["type"] as String
            val name = constName(key)
            val kt = kotlinType(type)
            val kw = if (type.endsWith("List")) "val" else "const val"
            body.append("    $kw $name: $kt = ${literal(type, entry["default"])}\n")
            entry["min"]?.let { body.append("    const val ${name}_MIN: $kt = ${literal(type, it)}\n") }
            entry["max"]?.let { body.append("    const val ${name}_MAX: $kt = ${literal(type, it)}\n") }
            entry["step"]?.let { body.append("    const val ${name}_STEP: $kt = ${literal(type, it)}\n") }
        }

        // _modes: for each CATEGORY_MODE pair, the option ids it shows (plain strings), in display order.
        val modeLists = (root["_modes"] as Map<*, *>).mapValues { it.value as List<*> }
        val categoryNames = listOf("GAMES", "LEARN", "PRACTICE", "QUIZ", "GUIDED")
        fun categoryAndModeOf(key: String): Pair<String, String> {
            val categoryName = categoryNames.first { key.startsWith("${it}_") }
            return categoryName to key.removePrefix("${categoryName}_")
        }

        val layout = StringBuilder()
        layout.append("object ModeOptionLayout {\n")
        layout.append("    /** For each (category, mode) pair, the option ids it shows, in display order (the `_modes` section of mode-options.json). */\n")
        layout.append("    val byCategoryMode: Map<Pair<StudyCategory, SessionMode>, List<String>> = mapOf(\n")
        modeLists.forEach { (rawKey, items) ->
            val (categoryName, modeName) = categoryAndModeOf(rawKey as String)
            val idList = items.joinToString(", ") { "\"$it\"" }
            layout.append("        (StudyCategory.$categoryName to SessionMode.$modeName) to listOf($idList),\n")
        }
        layout.append("    )\n\n")
        layout.append("    /** The option ids for [mode] under [category], in display order; empty for a pair with none. */\n")
        layout.append("    fun optionIdsFor(category: StudyCategory, mode: SessionMode): List<String> = byCategoryMode[category to mode].orEmpty()\n}\n")

        // _overrides: for an _options field whose default genuinely differs by (category, mode) —
        // keyed by field name, then by CATEGORY_MODE key, to the overriding value. One lookup
        // function is generated per overridden field, into ModeOptionDefaults alongside its base
        // default, e.g. `fun requireConfirmTapFor(category: StudyCategory, mode: SessionMode): Boolean`.
        val overridesByField = (root["_overrides"] as Map<*, *>?).orEmpty()
        overridesByField.forEach { (rawFieldName, rawByKey) ->
            val fieldName = rawFieldName as String
            val type = typeByName[fieldName] ?: error("_overrides overrides an unknown option \"$fieldName\" — check _options")
            val constNameForField = constName(fieldName)
            body.append("\n    fun ${fieldName}For(category: StudyCategory, mode: SessionMode): $type = when (category to mode) {\n")
            (rawByKey as Map<*, *>).forEach { (rawKey, value) ->
                val (categoryName, modeName) = categoryAndModeOf(rawKey as String)
                body.append("        (StudyCategory.$categoryName to SessionMode.$modeName) -> ${literal(type, value)}\n")
            }
            body.append("        else -> $constNameForField\n    }\n")
        }

        val outFile = File(outputDir, "net/ericclark/studiare/data/ModeOptionDefaults.kt")
        outFile.parentFile.mkdirs()

        outFile.writeText(
            "// GENERATED from app/src/main/defaults/mode-options.json. Edit the JSON, not this file.\n" +
                "package net.ericclark.studiare.data\n\n" +
                "object ModeOptionDefaults {\n" + body + "}\n\n" + layout
        )
    }
}

android.sourceSets.getByName("main").java.srcDir(generatedModeOptionsDir.get().asFile)

tasks.matching { it.name.startsWith("ksp") || (it.name.startsWith("compile") && it.name.endsWith("Kotlin")) }
    .configureEach { dependsOn(generateModeOptionDefaults) }
