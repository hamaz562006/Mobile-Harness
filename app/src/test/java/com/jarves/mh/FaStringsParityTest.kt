package com.jarves.mh

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FaStringsParityTest {
    @Test
    fun faStringNamesMatchEnglish() {
        val root = File("src/main/res")
        val enDir = File(root, "values")
        val faDir = File(root, "values-fa")
        assertTrue("values-fa missing", faDir.isDirectory)
        val nameRe = Regex("""<string\s+[^>]*name="([^"]+)"""")
        for (enFile in enDir.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" } ?: emptyArray()) {
            val faFile = File(faDir, enFile.name)
            assertTrue("missing FA file ${enFile.name}", faFile.isFile)
            val enNames = nameRe.findAll(enFile.readText()).map { it.groupValues[1] }.toSet()
            val faNames = nameRe.findAll(faFile.readText()).map { it.groupValues[1] }.toSet()
            val missing = enNames - faNames
            assertTrue("${enFile.name} missing FA keys: $missing", missing.isEmpty())
        }
    }
}
