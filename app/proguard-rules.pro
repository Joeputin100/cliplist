# R8 / ProGuard rules for the release (minified) build.
#
# Crashlytics: keep source file names + line numbers so release crash reports are
# readable, then rename the source-file attribute to hide the original file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Jetpack Compose, Firebase (Crashlytics), and kotlinx-coroutines ship their own
# consumer R8 rules (applied automatically from their AARs), so no library-specific
# keep rules are needed here for this app. Add targeted keeps only if a release
# smoke-test reveals a stripped class.

# kotlinx.serialization: R8 full mode (AGP default) can strip the generated serializers
# for our @Serializable models (TrackMeta/FolderMetaCache/ResultModel/...), which would
# silently break the per-folder cache and the worker's result hand-off in release builds.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keep,includedescriptorclasses class com.cliplist.scan.**$$serializer { *; }
-keepclassmembers class com.cliplist.scan.** {
    *** Companion;
}
-keepclasseswithmembers class com.cliplist.scan.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager instantiates workers reflectively (its consumer rules keep ListenableWorker
# subclasses; this is explicit insurance for ours).
-keep class com.cliplist.app.work.PlaylistBuildWorker { <init>(...); }

# Room generates <Database>_Impl classes that it instantiates reflectively. R8 full mode strips
# their no-arg constructors, which crashes WorkManager's startup (WorkDatabase is a Room DB) with
# NoSuchMethodException WorkDatabase_Impl.<init> — i.e. the whole release build crashes on launch.
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}
