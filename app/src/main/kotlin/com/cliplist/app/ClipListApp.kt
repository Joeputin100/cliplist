package com.cliplist.app

import android.app.Application
import com.google.firebase.crashlytics.FirebaseCrashlytics

class ClipListApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Firebase + Crashlytics auto-initialize via their ContentProviders before onCreate;
        // tag every crash report with the product name for easy filtering in the console.
        FirebaseCrashlytics.getInstance().setCustomKey("app", "My Playlist Creator 2026")
    }
}
