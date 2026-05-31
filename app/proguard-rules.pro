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
