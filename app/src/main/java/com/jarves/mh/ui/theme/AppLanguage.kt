package com.jarves.mh.ui.theme

enum class AppLanguage(
    val code: String,
    val displayName: String,
    val nativeName: String,
) {
    SYSTEM("system", "System Default", "Default"),
    ENGLISH("en", "English", "English"),
    FARSI("fa", "Farsi", "فارسی");

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: SYSTEM
    }
}
