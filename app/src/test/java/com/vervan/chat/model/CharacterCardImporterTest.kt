package com.vervan.chat.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.CRC32

/** Exercises the hand-rolled PNG chunk walker directly (see readCharaChunkForTest) against
 * synthetic PNGs — no real image needed, only a well-formed chunk stream. */
class CharacterCardImporterTest {

    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

    private fun chunk(type: String, data: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        val length = data.size
        out.write(intArrayOf(length ushr 24, length ushr 16, length ushr 8, length).map { it and 0xFF }.toIntArray().map { it.toByte() }.toByteArray())
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        out.write(typeBytes)
        out.write(data)
        val crc = CRC32().apply { update(typeBytes); update(data) }.value.toInt()
        out.write(intArrayOf(crc ushr 24, crc ushr 16, crc ushr 8, crc).map { it and 0xFF }.toIntArray().map { it.toByte() }.toByteArray())
        return out.toByteArray()
    }

    private fun buildPng(vararg extraChunks: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(pngSignature)
        // Minimal IHDR so a "real" PNG-shaped stream precedes the tEXt chunk, same ordering a
        // real character card PNG has (width/height/bit depth/color type/etc — content doesn't
        // matter to the chunk walker, only that it has a valid length so offsets stay correct).
        out.write(chunk("IHDR", ByteArray(13)))
        extraChunks.forEach { out.write(it) }
        out.write(chunk("IEND", ByteArray(0)))
        return out.toByteArray()
    }

    @Test
    fun `finds chara chunk among other chunks`() {
        val payload = Base64.getEncoder().encodeToString("{\"name\":\"Test\"}".toByteArray())
        val charaChunk = chunk("tEXt", "chara".toByteArray(Charsets.US_ASCII) + byteArrayOf(0) + payload.toByteArray(Charsets.US_ASCII))
        val otherTextChunk = chunk("tEXt", "Comment".toByteArray(Charsets.US_ASCII) + byteArrayOf(0) + "hello".toByteArray())
        val png = buildPng(otherTextChunk, charaChunk)

        assertEquals(payload, CharacterCardImporter.readCharaChunkForTest(png))
    }

    @Test
    fun `returns null when no chara chunk present`() {
        val png = buildPng(chunk("tEXt", "Comment".toByteArray(Charsets.US_ASCII) + byteArrayOf(0) + "hello".toByteArray()))
        assertNull(CharacterCardImporter.readCharaChunkForTest(png))
    }

    @Test
    fun `stops at IEND without reading past it`() {
        // A chara-keyword chunk placed AFTER IEND (malformed/truncated stream) must not be found —
        // the walker should stop as soon as it hits IEND, exactly like a real PNG decoder would.
        val out = ByteArrayOutputStream()
        out.write(buildPng())
        val payload = Base64.getEncoder().encodeToString("{}".toByteArray())
        out.write(chunk("tEXt", "chara".toByteArray(Charsets.US_ASCII) + byteArrayOf(0) + payload.toByteArray(Charsets.US_ASCII)))
        assertNull(CharacterCardImporter.readCharaChunkForTest(out.toByteArray()))
    }
}
