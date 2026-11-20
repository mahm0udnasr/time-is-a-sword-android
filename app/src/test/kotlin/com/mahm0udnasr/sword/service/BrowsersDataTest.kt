package com.mahm0udnasr.sword.service

import com.mahm0udnasr.sword.data.StoreJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrowsersDataTest {
    private val browsers: List<BrowserSpec> = StoreJson.decodeFromString(File("src/main/assets/browsers.json").readText())

    @Test
    fun `browsers json parses and each package is listed once`() {
        val pkgs = browsers.flatMap { it.packages }
        assertEquals(pkgs.size, pkgs.toSet().size)
        assertTrue(browsers.all { it.packages.isNotEmpty() })
    }

    @Test
    fun `Chrome url_bar is the verified entry (Spike A, S24 Ultra)`() {
        val chrome = browsers.first { "com.android.chrome" in it.packages }
        assertTrue(chrome.verified)
        assertEquals(listOf("url_bar"), chrome.urlBarIds)
    }
}
