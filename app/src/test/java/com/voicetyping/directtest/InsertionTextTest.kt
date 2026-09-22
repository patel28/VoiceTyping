package com.voicetyping.directtest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class InsertionTextTest {
    @Test
    fun trimsSurroundingWhitespaceAndAddsExactlyOneAsciiSpace() {
        assertEquals("Hello world. ", InsertionText.normalize(" \tHello world. \n"))
        assertEquals("Hello  world ", InsertionText.normalize("Hello  world"))
    }

    @Test
    fun rejectsBlankText() {
        assertNull(InsertionText.normalize(" \t\n"))
    }
}
