package com.mahin.hytune

import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun package_name_is_correct() {
        assertEquals("com.mahin.hytune", HyTuneApp::class.java.`package`?.name)
    }
}
