# =======================================================
# NewPipeExtractor
# =======================================================
# NewPipeExtractor uses reflection to discover and instantiate extractor classes.
# R8 full mode strips all "unused" classes — these rules preserve them.
-keep class org.schabi.newpipe.extractor.** { *; }
-keepnames class org.schabi.newpipe.extractor.** { *; }
-keepclassmembers class org.schabi.newpipe.extractor.** { *; }
-keep class org.schabi.newpipe.extractor.services.** { *; }

# =======================================================
# Jackson (NewPipeExtractor JSON parsing)
# =======================================================
# Jackson uses reflection to read/write fields and invoke getters/setters.
# R8 full mode strips private fields; Jackson then throws at runtime.
-keep class com.fasterxml.jackson.** { *; }
-keepnames class com.fasterxml.jackson.** { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.* *;
}
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class * { *; }
-keep @com.fasterxml.jackson.annotation.JsonCreator class * { *; }
-keep class com.fasterxml.jackson.databind.** { *; }

# =======================================================
# Rhino JavaScript Engine
# =======================================================
# NewPipeExtractor uses Mozilla Rhino to execute YouTube's JS-based signature
# decryption function. Without these rules, stream extraction silently fails
# on release builds because R8 strips Rhino's reflection-accessed classes.
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.mozilla.classfile.**

# =======================================================
# OkHttp + Okio
# =======================================================
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# =======================================================
# Kotlin Coroutines
# =======================================================
-keepclassmembers class kotlinx.coroutines.** { *; }
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# =======================================================
# Hilt (safety net — Hilt's consumer rules cover most cases)
# =======================================================
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# =======================================================
# AndroidX Media3 / ExoPlayer
# =======================================================
# PlaybackService is referenced by name in AndroidManifest — must not be renamed.
-keep class androidx.media3.** { *; }
# Legacy ExoPlayer package still referenced in some Media3 1.x internals
-keep class com.google.android.exoplayer2.** { *; }

# =======================================================
# Coil
# =======================================================
-dontwarn coil.**

# =======================================================
# General Android / Kotlin Rules
# =======================================================
# Preserve stack traces in crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin metadata (required for reflection on data classes, sealed classes)
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions

# Enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Parcelable (used by @Parcelize)
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Serializable
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
