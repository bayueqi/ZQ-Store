# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Preserve line numbers for debugging stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures (for Gson, Retrofit)
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes Exceptions

#======================================
# Retrofit
#======================================
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Keep Retrofit API interfaces
-keep,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

#======================================
# OkHttp
#======================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

#======================================
# Gson - CRITICAL: Preserve TypeToken generics
#======================================
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

# Preserve generic type information for TypeToken
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

# Keep TypeToken and its subclasses with generic signature
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep TypeAdapters
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keep class * extends com.google.gson.TypeAdapter

# Prevent R8 from stripping generic signature (CRITICAL)
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses

# Keep all classes that use Gson for serialization
-keep class com.samyak.repostore.data.** { *; }
-keep class com.samyak.repostore.data.model.** { <fields>; <init>(...); }
-keep class com.samyak.repostore.data.auth.** { <fields>; <init>(...); }

#======================================
# Room
#======================================
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

#======================================
# Glide
#======================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
    *** rewind();
}

#======================================
# Coroutines
#======================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

#======================================
# App Data Models (keep all fields for JSON serialization)
#======================================
-keep class com.samyak.repostore.data.model.** { *; }
-keep class com.samyak.repostore.data.auth.GitHubAuth$** { *; }
-keepclassmembers class com.samyak.repostore.data.model.** { *; }

#======================================
# Security Crypto (EncryptedSharedPreferences)
#======================================
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

#======================================
# Markwon
#======================================
-dontwarn io.noties.markwon.**
-keep class io.noties.markwon.** { *; }

#======================================
# PhotoView
#======================================
-keep class io.getstream.photoview.** { *; }

#======================================
# Android Framework
#======================================
-keep class * extends android.app.Activity
-keep class * extends android.app.Application
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider
-keep class * extends android.app.Fragment
-keep class * extends androidx.fragment.app.Fragment

# Keep ViewBinding classes
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * bind(android.view.View);
    public static * inflate(android.view.LayoutInflater);
    public static * inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
}