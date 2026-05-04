# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ---- Keep line numbers for crash reports ----
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Gson / Retrofit serialization ----
# Keep all data model classes used by Gson for JSON deserialization.
# R8 would rename/remove fields that Gson needs to map via @SerializedName.
-keep class com.myapp.familycode.data.model.** { *; }

# Keep Gson annotations
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- Retrofit ----
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**

# ---- Koin ----
-keep class org.koin.** { *; }

# ---- Room ----
# Room uses generated code; keep DAO and Entity classes.
-keep class com.myapp.familycode.data.db.** { *; }

# ---- BroadcastReceivers (referenced from AndroidManifest) ----
-keep class com.myapp.familycode.receiver.** { *; }

# ---- Kotlin ----
-dontwarn kotlin.**
-dontwarn kotlinx.**