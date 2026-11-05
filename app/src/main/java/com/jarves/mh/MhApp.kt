package com.jarves.mh

import android.app.Application
import androidx.core.content.res.ResourcesCompat

/**
 * Preloads Vazirmatn so the first Persian frame never falls back to the system font.
 */
class MhApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { ResourcesCompat.getFont(this, R.font.vazirmatn_regular) }
        runCatching { ResourcesCompat.getFont(this, R.font.vazirmatn_medium) }
        runCatching { ResourcesCompat.getFont(this, R.font.vazirmatn_bold) }
    }
}
