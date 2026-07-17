package com.vervan.chat.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechTextFormatterTest {

    @Test
    fun `strips bold and italic markers`() {
        assertEquals("This is bold and this is italic.", markdownToSpeechText("This is **bold** and this is *italic*."))
    }

    @Test
    fun `strips headings and list markers`() {
        assertEquals("Title First Second", markdownToSpeechText("# Title\n- First\n- Second"))
    }

    @Test
    fun `keeps link text and drops the url`() {
        assertEquals("See the docs for more.", markdownToSpeechText("See [the docs](https://example.com) for more."))
    }

    @Test
    fun `replaces fenced code blocks with a short spoken placeholder`() {
        assertEquals("Run this: code block then check the output.", markdownToSpeechText("Run this: ```kotlin\nval x = 1\n``` then check the output."))
    }

    @Test
    fun `keeps inline code content without backticks`() {
        assertEquals("Call foo() to start.", markdownToSpeechText("Call `foo()` to start."))
    }

    @Test
    fun `plain text with no markdown is unchanged`() {
        assertEquals("Nothing special here.", markdownToSpeechText("Nothing special here."))
    }
}
