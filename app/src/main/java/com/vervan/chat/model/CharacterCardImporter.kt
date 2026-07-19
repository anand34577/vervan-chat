package com.vervan.chat.model

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** Fields pulled from a SillyTavern character card (V1 or V2 spec), already mapped down to what
 * this app's [com.vervan.chat.data.db.entities.Persona] actually has room for. [avatarFile] is
 * the card's own PNG, copied into app-private storage — null if the source file wasn't a
 * decodable PNG image (a malformed/renamed file can still carry a valid `chara` chunk). */
data class ImportedCharacterCard(
    val name: String,
    val description: String,
    val systemInstruction: String,
    val avatarFile: File?
)

/**
 * Imports SillyTavern-style character cards: a PNG whose image data is just the character's
 * portrait, with the actual character JSON hidden in a `tEXt` chunk keyword "chara" as
 * base64-encoded UTF-8 (V2 spec: https://github.com/malfoyslastname/character-card-spec-v2 —
 * V1 cards use the same chunk/encoding with a flatter JSON shape, both handled here). This is
 * the dominant distribution format across SillyTavern/Chub/etc. character-sharing communities,
 * so a persona importer that only reads plain JSON would miss almost everything actually being
 * shared.
 *
 * No PNG/image library needed: only the file's chunk structure has to be walked (signature,
 * then length/type/data/crc chunks in sequence) to find one `tEXt` entry, so it's ~40 lines of
 * manual byte parsing rather than a dependency. The whole original file doubles as the avatar
 * image (portrait cards are portrait images), so it's simply copied rather than re-encoded.
 */
object CharacterCardImporter {
    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    )

    class NotACharacterCardException(message: String) : IOException(message)

    /** Reads [uri] fully into memory (portrait cards are a few hundred KB at most — the same
     * order of magnitude this app already buffers for a captured photo), extracts the card
     * JSON, and copies the source PNG to `filesDir/personas/avatars/<uuid>.png` for use as the
     * persona's avatar. Throws [NotACharacterCardException] for anything that isn't a PNG with
     * an embedded `chara` chunk — callers show that message directly to the user. */
    fun import(context: Context, uri: Uri): ImportedCharacterCard {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw NotACharacterCardException("Could not read the selected file.")
        if (bytes.size < PNG_SIGNATURE.size || !bytes.copyOfRange(0, PNG_SIGNATURE.size).contentEquals(PNG_SIGNATURE)) {
            throw NotACharacterCardException("Not a PNG file — character cards are PNG images with the character data embedded inside.")
        }
        val charaText = readCharaChunk(bytes)
            ?: throw NotACharacterCardException("This PNG has no embedded character data (no \"chara\" chunk found).")
        val json = runCatching { JSONObject(String(java.util.Base64.getDecoder().decode(charaText), Charsets.UTF_8)) }
            .getOrElse { throw NotACharacterCardException("The embedded character data isn't valid — the card may be corrupted.") }

        // V2 cards nest everything actually populated under "data"; V1 cards have the same key
        // names at the top level. Reading through one indirection level covers both without a
        // separate parser per spec version.
        val data = json.optJSONObject("data") ?: json

        val name = data.optString("name").trim().ifBlank { "Imported character" }
        // personality/scenario/first_mes/mes_example are SillyTavern-specific fields with no
        // single Persona equivalent — folded into the free-text systemInstruction (what actually
        // drives model behavior) rather than dropped, since together they're most of what makes
        // an imported character behave like itself.
        val description = data.optString("description").trim()
        val personality = data.optString("personality").trim()
        val scenario = data.optString("scenario").trim()
        val firstMessage = data.optString("first_mes").trim()
        val exampleDialogue = data.optString("mes_example").trim()
        val systemPromptOverride = data.optString("system_prompt").trim()
        val postHistoryInstructions = data.optString("post_history_instructions").trim()

        val systemInstruction = buildString {
            if (systemPromptOverride.isNotBlank()) appendLine(systemPromptOverride) else if (description.isNotBlank()) appendLine(description)
            if (personality.isNotBlank()) { appendLine(); appendLine("Personality: $personality") }
            if (scenario.isNotBlank()) { appendLine(); appendLine("Scenario: $scenario") }
            if (exampleDialogue.isNotBlank()) { appendLine(); appendLine("Example dialogue:"); appendLine(exampleDialogue) }
            if (firstMessage.isNotBlank()) { appendLine(); appendLine("When starting a new conversation, greet the user in character along these lines: $firstMessage") }
            if (postHistoryInstructions.isNotBlank()) { appendLine(); appendLine(postHistoryInstructions) }
        }.trim().ifBlank { "You are $name." }

        val avatarDir = File(context.filesDir, "personas/avatars").apply { mkdirs() }
        val avatarFile = File(avatarDir, "${UUID.randomUUID()}.png").apply { writeBytes(bytes) }

        return ImportedCharacterCard(
            name = name.take(80),
            description = (description.ifBlank { personality }).take(500),
            systemInstruction = systemInstruction.take(8000),
            avatarFile = avatarFile
        )
    }

    /** Walks the PNG chunk stream (8-byte signature, then repeating [4-byte length][4-byte
     * type][data][4-byte CRC]) looking for a `tEXt` chunk whose keyword is "chara". A `tEXt`
     * chunk's data is `keyword + 0x00 + text`, and the spec stores the base64 payload directly
     * as that text (not further chunked/compressed — unlike `zTXt`, which this importer doesn't
     * need to support since no known card exporter uses it for this field). Returns null if no
     * such chunk exists, e.g. an ordinary PNG that just happens to pass the signature check. */
    /** Package/test-visible so [readCharaChunk]'s chunk-walking logic (the fiddliest part of
     * this file — off-by-one errors here silently return null instead of throwing) can be
     * exercised directly against synthetic PNG bytes without a real Context/Uri. */
    internal fun readCharaChunkForTest(bytes: ByteArray): String? = readCharaChunk(bytes)

    private fun readCharaChunk(bytes: ByteArray): String? {
        var offset = PNG_SIGNATURE.size
        while (offset + 8 <= bytes.size) {
            val length = readInt32BE(bytes, offset)
            val type = String(bytes, offset + 4, 4, Charsets.US_ASCII)
            val dataStart = offset + 8
            if (length < 0 || dataStart + length > bytes.size) break
            if (type == "tEXt") {
                val chunk = bytes.copyOfRange(dataStart, dataStart + length)
                val nul = chunk.indexOf(0)
                if (nul >= 0) {
                    val keyword = String(chunk, 0, nul, Charsets.US_ASCII)
                    if (keyword == "chara") {
                        return String(chunk, nul + 1, chunk.size - nul - 1, Charsets.US_ASCII)
                    }
                }
            } else if (type == "IEND") {
                break
            }
            offset = dataStart + length + 4 // skip data + trailing CRC
        }
        return null
    }

    private fun readInt32BE(bytes: ByteArray, at: Int): Int =
        ((bytes[at].toInt() and 0xFF) shl 24) or
            ((bytes[at + 1].toInt() and 0xFF) shl 16) or
            ((bytes[at + 2].toInt() and 0xFF) shl 8) or
            (bytes[at + 3].toInt() and 0xFF)
}
