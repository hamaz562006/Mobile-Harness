package com.jarves.mh

import com.jarves.mh.ui.LocaleHelper
import com.jarves.mh.ui.theme.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocaleHelperTest {

    @Test
    fun appLanguageResolutionFromCode() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromCode("en"))
        assertEquals(AppLanguage.FARSI, AppLanguage.fromCode("fa"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("system"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode(null))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("unknown_language"))
        // Removed locales fall back to SYSTEM so old preferences do not crash.
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("es"))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromCode("ar"))
    }

    @Test
    fun localeHelperGetLocale() {
        assertNull(LocaleHelper.getLocale("system"))
        assertNull(LocaleHelper.getLocale(""))

        val enLocale = LocaleHelper.getLocale("en")
        assertNotNull(enLocale)
        assertEquals("en", enLocale?.language)

        val faLocale = LocaleHelper.getLocale("fa")
        assertNotNull(faLocale)
        assertEquals("fa", faLocale?.language)
    }

    @Test
    fun appLanguageProperties() {
        assertEquals(listOf("system", "en", "fa"), AppLanguage.entries.map { it.code })
        for (lang in AppLanguage.entries) {
            assertTrue("Display name should not be blank for ${lang.name}", lang.displayName.isNotBlank())
            assertTrue("Native name should not be blank for ${lang.name}", lang.nativeName.isNotBlank())
            assertTrue("Code should not be blank for ${lang.name}", lang.code.isNotBlank())
        }
    }
}
