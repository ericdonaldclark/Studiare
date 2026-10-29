# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- Gson (data/Converters.kt's Room TypeConverters use Gson to (de)serialize these model
# classes, and generic `object : TypeToken<List<X>>() {}.type` calls, which need generic
# signature info preserved to resolve correctly under R8) ---
-keepattributes Signature
-keepattributes *Annotation*
-keep class net.ericclark.studiare.data.NoteField { *; }
-keep class net.ericclark.studiare.data.ReviewLog { *; }
-keep class net.ericclark.studiare.data.MediaType { *; }
-dontwarn com.google.gson.**
# -keepattributes Signature alone isn't enough on its own: confirmed the hard way with a real
# runtime crash ("TypeToken must be created with a type argument... make sure that generic
# signatures are preserved") deobfuscated via app/build/outputs/mapping/release/mapping.txt,
# which traced straight into com.google.gson.reflect.TypeToken's own constructor, called from a
# Room performBlocking/RoomDatabase call chain — a @TypeConverter's TypeToken use. R8 still
# strips the generic signature off each `object : TypeToken<List<X>>() {}` call site's own
# (synthetic, anonymous) subclass of TypeToken unless TypeToken itself and its subclasses are
# separately protected from that optimization. This is Gson's own documented R8 rule, not project-
# specific: "Retain generic signatures of TypeToken and its subclasses with R8 version 3.0+."
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# --- Firestore (components/AuthAndSyncManager.kt pushes/pulls these via .set()/.toObjects(),
# both of which use Firestore's own reflection-based field mapping, not a generated adapter) ---
-keep class net.ericclark.studiare.data.Firestore** { *; }
-keep class net.ericclark.studiare.data.TagDefinition { *; }
-keep class net.ericclark.studiare.data.TemplateSet { *; }

# --- JNI (zstd-jni for Anki .apkg decompression, sherpa-onnx for offline speech) — native code
# resolves Java methods by exact name/signature, which R8's usage analysis can't see through, so
# any class with a native method needs its name preserved regardless of which library it's in.
-keepclasseswithmembers class * {
    native <methods>;
}

# --- SLF4J (pulled in transitively, likely by Ktor or commons-compress) looks for an optional
# binding implementation via reflection and falls back to a no-op logger when none is present —
# that's expected, not a real missing dependency. R8-generated suggestion from a clean
# assembleRelease run (app/build/outputs/mapping/release/missing_rules.txt).
-dontwarn org.slf4j.impl.StaticLoggerBinder