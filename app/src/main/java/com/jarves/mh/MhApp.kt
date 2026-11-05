package com.jarves.mh

import android.app.Application
import android.graphics.Typeface
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontLoadingStrategy
import androidx.compose.ui.text.font.FontWeight

/**
 * Loads Vazirmatn once from assets at process start.
 * Asset Typeface load fails loud if files are missing; R.font can fail silent.
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
        // Verify files exist (throws if missing)
        Typeface.createFromAsset(assets, "fonts/Vazirmatn-Regular.ttf")
        Typeface.createFromAsset(assets, "fonts/Vazirmatn-Medium.ttf")
        Typeface.createFromAsset(assets, "fonts/Vazirmatn-SemiBold.ttf")
        Typeface.createFromAsset(assets, "fonts/Vazirmatn-Bold.ttf")
        ready = FontFamily(
            Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.Thin, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.ExtraLight, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.Light, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.Normal, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Regular.ttf", assets, FontWeight.W400, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Medium.ttf", assets, FontWeight.Medium, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Medium.ttf", assets, FontWeight.W500, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-SemiBold.ttf", assets, FontWeight.SemiBold, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-SemiBold.ttf", assets, FontWeight.W600, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.Bold, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.W700, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.ExtraBold, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.W800, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.Black, loadingStrategy = FontLoadingStrategy.Blocking),
            Font("fonts/Vazirmatn-Bold.ttf", assets, FontWeight.W900, loadingStrategy = FontLoadingStrategy.Blocking),
        )
    }
}
