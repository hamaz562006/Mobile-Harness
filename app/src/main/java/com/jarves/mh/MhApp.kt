package com.jarves.mh

import android.app.Application
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

/**
 * Loads Vazirmatn once at process start.
 * Assets are verified with Typeface.createFromAsset; Compose FontFamily uses res/font
 * (same TTF files) with Blocking so weights do not fall back to the system font.
 */
class MhApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VazirmatnFonts.init(this)
    }
}

object VazirmatnFonts {
    @Volatile
    private var ready: FontFamily? = null

    val family: FontFamily
        get() = ready ?: FontFamily.Default

    fun init(app: Application) {
        if (ready != null) return
        val assets = app.assets
        // Fail fast if asset TTFs are missing from the APK
        Typeface.createFromAsset(assets, "fonts/Vazirmatn-Regular.ttf")
        Typeface.createFromAsset(assets, "fonts/Vazirmatn-Medium.ttf")
        Typeface.createFromAsset(assets, "fonts/Vazirmatn-SemiBold.ttf")
        Typeface.createFromAsset(assets, "fonts/Vazirmatn-Bold.ttf")
        // res/font copies of the same files — Font(resId) is the supported Compose API in our BOM
        ready = FontFamily(
            Font(R.font.vazirmatn_regular, FontWeight.Thin, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_regular, FontWeight.ExtraLight, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_regular, FontWeight.Light, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_regular, FontWeight.Normal, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_regular, FontWeight.W400, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_medium, FontWeight.Medium, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_medium, FontWeight.W500, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_semibold, FontWeight.SemiBold, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_semibold, FontWeight.W600, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_bold, FontWeight.Bold, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_bold, FontWeight.W700, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_bold, FontWeight.ExtraBold, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_bold, FontWeight.W800, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_bold, FontWeight.Black, FontStyle.Normal, FontLoadingStrategy.Blocking),
            Font(R.font.vazirmatn_bold, FontWeight.W900, FontStyle.Normal, FontLoadingStrategy.Blocking),
        )
    }
}
