# ── Stack-trace readability ──────────────────────────────────────────────────
# Preserve file names and line numbers so crash reports are actionable.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin metadata ──────────────────────────────────────────────────────────
# Required for reflection-based APIs (e.g. DataStore, Hilt) that read class metadata.
-keep class kotlin.Metadata { *; }

# ── Kotlin Serialization (kotlinx-serialization-json 1.11.0) ─────────────────
# The compiler plugin generates $serializer inner objects and companion serializer()
# methods at compile time. R8 sees these as unreferenced generated code and strips
# them without explicit protection.
-keepattributes InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    static ** $serializer;
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
# Keep any generated serializer classes themselves (named *$$serializer).
-keep class **$$serializer { *; }

# ── Room (2.8.4) ──────────────────────────────────────────────────────────────
# Room ships consumer rules, but the generated *_Impl database class is looked
# up by class name via Room.databaseBuilder — keep it and all entity classes
# whose field names map to SQLite column names.
-keep @androidx.room.Database class ** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class ** { *; }
-keep @androidx.room.Fts4 class ** { *; }
-keep @androidx.room.Dao interface ** { *; }

# ── WorkManager (2.11.2) + Hilt Work (1.3.0) ─────────────────────────────────
# WorkManager resolves worker classes by name at runtime. Hilt's WorkerFactory
# uses Dagger-generated code, but the worker class itself must still be findable.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# ── OkHttp 5 / Okio ──────────────────────────────────────────────────────────
# OkHttp 5 ships its own consumer rules. Suppress warnings from internal APIs
# that are conditionally present (e.g. animal-sniffer, JSR-305 annotations).
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**

# ── Timber (5.0.1) ───────────────────────────────────────────────────────────
-keep class timber.log.Timber { *; }
-keep class timber.log.Timber$Tree { *; }

# ── ML Kit Text Recognition (16.0.1) ─────────────────────────────────────────
# ML Kit ships consumer rules; suppress any residual build warnings.
-dontwarn com.google.mlkit.**
