package com.vervan.chat.llm

import com.vervan.chat.data.db.entities.ModelRole
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteModelCatalogTest {

    /** Every embedding id from a real LM Studio `/models` payload. */
    @Test
    fun detectsEmbeddingModelsFromTheirIds() {
        listOf(
            "qwen3-embedding-0.6b-mxl",
            "text-embedding-qwen3-embedding-0.6b",
            "text-embedding-nomic-embed-text-v1.5",
            "text-embedding-3-large",
            "bge-m3"
        ).forEach { assertEquals(it, ModelRole.EMBEDDING, RemoteModelCatalog.inferRole(it)) }
    }

    /** The chat models from the same payload must not be swept up by the markers above — several
     *  contain substrings ("mlx", "-1.0-") that a looser rule would misread. */
    @Test
    fun leavesChatModelsAsGeneration() {
        listOf(
            "google/gemma-4-12b-qat",
            "prism-ml/bonsai-27b",
            "pavantippannagari/ornith-1.0-9b-mlx",
            "qwythos-9b-claude-mythos-5-1m-mlx",
            "ornith-1.0-9b",
            "mlx-community/gemma-4-12b-it-qat",
            "lfm2.5-8b-a1b-mlx",
            "nuextract3",
            "hy-mt2-1.8b",
            "qwen2.5-0.5b-instruct-mlx",
            "qwen3.5-4b-uncensored-hauhaucs-aggressive",
            "titan-qwen2.5-0.5b"
        ).forEach { assertEquals(it, ModelRole.GENERATION, RemoteModelCatalog.inferRole(it)) }
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(ModelRole.EMBEDDING, RemoteModelCatalog.inferRole("Text-Embedding-3-Small"))
    }

    @Test
    fun guessesVisionFromKnownFamiliesAndExplicitMarkers() {
        listOf(
            "google/gemma-4-12b-qat",
            "mlx-community/gemma-4-12b-it-qat",
            "qwen2.5-vl-7b-instruct",
            "meta-llama-3.2-11b-vision",
            "gpt-4o-mini",
            "claude-3-opus",
            "llava-1.6-mistral-7b"
        ).forEach { assertEquals(it, true, RemoteModelCatalog.inferVision(it)) }
    }

    @Test
    fun leavesUnmarkedModelsAsTextOnly() {
        listOf(
            "prism-ml/bonsai-27b",
            "nuextract3",
            "hy-mt2-1.8b",
            "qwen3.5-4b-uncensored-hauhaucs-aggressive",
            "titan-qwen2.5-0.5b"
        ).forEach { assertEquals(it, false, RemoteModelCatalog.inferVision(it)) }
    }

    @Test
    fun embeddingModelsAreNeverGuessedAsVision() {
        assertEquals(false, RemoteModelCatalog.inferVision("text-embedding-nomic-embed-text-v1.5"))
    }
}
