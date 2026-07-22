package com.vervan.chat.data.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Data-preserving migrations for schema versions used by test builds. This — plus the version
 * number on [com.vervan.chat.data.db.AppDatabase]'s `@Database` annotation — is this app's
 * entire schema-versioning strategy: every schema change bumps the version by one and adds a
 * `Migration(old, new)` entry here, in order, never skipped. There's no equivalent mechanism
 * for `SettingsRepository`'s DataStore preferences — its keys are additive-with-defaults, which
 * has been safe so far; a key rename/removal would need its own one-time read-old/write-new
 * migration if that's ever needed, same shape as a Room migration.
 */
val MIGRATIONS = arrayOf(
    object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chats ADD COLUMN deletedAt INTEGER")
            db.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER")
            db.execSQL("ALTER TABLE documents ADD COLUMN deletedAt INTEGER")
            db.execSQL("ALTER TABLE memories ADD COLUMN `key` TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS study_cards (" +
                    "id TEXT NOT NULL PRIMARY KEY, setName TEXT NOT NULL, question TEXT NOT NULL, " +
                    "answer TEXT NOT NULL, timesReviewed INTEGER NOT NULL, timesCorrect INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL)"
            )
        }
    },
    object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN audioPath TEXT")
        }
    },
    object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE models_new (id TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, " +
                    "filePath TEXT NOT NULL, fileSizeBytes INTEGER NOT NULL, sha256 TEXT NOT NULL, " +
                    "role TEXT NOT NULL, lastWorkingBackend TEXT NOT NULL, isActive INTEGER NOT NULL, " +
                    "importedAt INTEGER NOT NULL, licenseAcknowledged INTEGER NOT NULL)"
            )
            db.execSQL(
                "INSERT INTO models_new SELECT id, displayName, filePath, fileSizeBytes, sha256, role, " +
                    "lastWorkingBackend, isActive, importedAt, 0 FROM models"
            )
            db.execSQL("DROP TABLE models")
            db.execSQL("ALTER TABLE models_new RENAME TO models")
        }
    },
    object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE chats_new (id TEXT NOT NULL PRIMARY KEY, title TEXT NOT NULL, personaId TEXT, " +
                    "modelId TEXT, projectId TEXT, draft TEXT NOT NULL, pinned INTEGER NOT NULL, " +
                    "archived INTEGER NOT NULL, sourceGrounded INTEGER NOT NULL, toolsEnabled INTEGER NOT NULL, " +
                    "thinkingMode TEXT NOT NULL, activeLeafId TEXT, knowledgeBaseIds TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, deletedAt INTEGER)"
            )
            db.execSQL(
                "INSERT INTO chats_new SELECT id, title, personaId, modelId, projectId, draft, pinned, archived, " +
                    "sourceGrounded, toolsEnabled, 'OFF', activeLeafId, knowledgeBaseIds, createdAt, updatedAt, " +
                    "deletedAt FROM chats"
            )
            db.execSQL("DROP TABLE chats")
            db.execSQL("ALTER TABLE chats_new RENAME TO chats")
        }
    },
    object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Model profile per chat (spec §11.9)
            db.execSQL("ALTER TABLE chats ADD COLUMN profile TEXT NOT NULL DEFAULT 'BALANCED'")
            // Folder column on chat + note (spec §28.2)
            db.execSQL("ALTER TABLE chats ADD COLUMN folderId TEXT")
            db.execSQL("ALTER TABLE notes ADD COLUMN folderId TEXT")
            // Folders table
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS folders (" +
                    "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, " +
                    "defaultPersonaId TEXT, defaultModelId TEXT, defaultKnowledgeBaseIds TEXT NOT NULL, " +
                    "color TEXT NOT NULL, createdAt INTEGER NOT NULL, deletedAt INTEGER)"
            )
            // Flashcard set metadata (spec §55) — separate from individual cards so a set has
            // a description and last-studied stamp independent of its cards.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS flashcard_sets (" +
                    "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, lastStudiedAt INTEGER)"
            )
            // Memory suggestion inbox (spec §27.3) — proposed memories awaiting review.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS memory_suggestions (" +
                    "id TEXT NOT NULL PRIMARY KEY, text TEXT NOT NULL, `key` TEXT, " +
                    "scope TEXT NOT NULL, scopeRefId TEXT, sourceChatId TEXT, " +
                    "createdAt INTEGER NOT NULL, status TEXT NOT NULL)"
            )
            // Tool execution audit history (spec §16.3 step 13 / §2.6) — every executed tool
            // call gets a row, so users can see what the app actually did on the model's behalf.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS tool_audit (" +
                    "id TEXT NOT NULL PRIMARY KEY, toolName TEXT NOT NULL, paramsJson TEXT NOT NULL, " +
                    "success INTEGER NOT NULL, summary TEXT NOT NULL, risk TEXT NOT NULL, " +
                    "chatId TEXT, createdAt INTEGER NOT NULL)"
            )
            // Indexing / background job records (spec §32.1, §76) — durable job queue.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS jobs (" +
                    "id TEXT NOT NULL PRIMARY KEY, type TEXT NOT NULL, label TEXT NOT NULL, " +
                    "state TEXT NOT NULL, progress INTEGER NOT NULL, detail TEXT NOT NULL, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
            )
        }
    },
    object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Marks OCR-derived document text (spec §40.27) so the UI can flag it as such.
            db.execSQL("ALTER TABLE documents ADD COLUMN ocrApplied INTEGER NOT NULL DEFAULT 0")
        }
    },
    object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Document versioning (Phase 3, spec §20) — content hash to detect a changed
            // same-named re-import.
            db.execSQL("ALTER TABLE documents ADD COLUMN contentHash TEXT")
            // Knowledge base display/default-context customization (Phase 3, spec §17).
            db.execSQL("ALTER TABLE knowledge_bases ADD COLUMN icon TEXT NOT NULL DEFAULT 'MenuBook'")
            db.execSQL("ALTER TABLE knowledge_bases ADD COLUMN color TEXT")
            db.execSQL("ALTER TABLE knowledge_bases ADD COLUMN defaultPersonaId TEXT")
            db.execSQL("ALTER TABLE knowledge_bases ADD COLUMN defaultProjectId TEXT")
            db.execSQL("ALTER TABLE knowledge_bases ADD COLUMN autoIndex INTEGER NOT NULL DEFAULT 1")
        }
    },
    object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Persona behavior dials (Phase 4, spec §25).
            db.execSQL("ALTER TABLE personas ADD COLUMN tone TEXT NOT NULL DEFAULT 'NEUTRAL'")
            db.execSQL("ALTER TABLE personas ADD COLUMN formality TEXT NOT NULL DEFAULT 'NEUTRAL'")
            db.execSQL("ALTER TABLE personas ADD COLUMN conciseness TEXT NOT NULL DEFAULT 'NORMAL'")
            db.execSQL("ALTER TABLE personas ADD COLUMN creativity REAL NOT NULL DEFAULT 0.5")
            db.execSQL("ALTER TABLE personas ADD COLUMN responseLength TEXT NOT NULL DEFAULT 'BALANCED'")
            db.execSQL("ALTER TABLE personas ADD COLUMN language TEXT NOT NULL DEFAULT ''")
        }
    },
    object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Note tags (Phase 4, spec §21).
            db.execSQL("ALTER TABLE notes ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
        }
    },
    object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Recycle bin coverage extended to personas/workflows/templates/projects/
            // memories/saved-outputs (Phase 6, spec §34) — same soft-delete column pattern
            // already used for chats/notes/documents/folders.
            db.execSQL("ALTER TABLE personas ADD COLUMN deletedAt INTEGER")
            db.execSQL("ALTER TABLE workflows ADD COLUMN deletedAt INTEGER")
            db.execSQL("ALTER TABLE prompt_templates ADD COLUMN deletedAt INTEGER")
            db.execSQL("ALTER TABLE projects ADD COLUMN deletedAt INTEGER")
            db.execSQL("ALTER TABLE memories ADD COLUMN deletedAt INTEGER")
            db.execSQL("ALTER TABLE saved_outputs ADD COLUMN deletedAt INTEGER")
        }
    },
    object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE models ADD COLUMN supportsVision INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN supportsAudio INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN supportsTools INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN supportsThinking INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN temperature REAL")
            db.execSQL("ALTER TABLE models ADD COLUMN topP REAL")
            db.execSQL("ALTER TABLE models ADD COLUMN topK INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN maxNumImages INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN contextTokens INTEGER")
        }
    },
    object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Explicit per-model GPU/CPU/NPU choice, enforced strictly (no silent fallback
            // unless AUTO) instead of only a global backend preference.
            db.execSQL("ALTER TABLE models ADD COLUMN preferredBackend TEXT NOT NULL DEFAULT 'AUTO'")
        }
    },
    object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // MTP (speculative decoding) — mtpSupported is detected from the model file itself
            // via LiteRT-LM's Capabilities API, not user-declared.
            db.execSQL("ALTER TABLE models ADD COLUMN mtpSupported INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN mtpEnabled INTEGER NOT NULL DEFAULT 1")
        }
    },
    object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Workspace System spec §1-2: a permanent Default Workspace, and every chat/folder/
            // document now scoped to exactly one workspace. Existing rows all backfill onto the
            // Default Workspace so nothing becomes orphaned by this migration.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS workspaces (" +
                    "id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, description TEXT NOT NULL, " +
                    "personaId TEXT NOT NULL, isDefault INTEGER NOT NULL, archived INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, lastActiveAt INTEGER NOT NULL)"
            )
            val now = System.currentTimeMillis()
            // personaId references "builtin-general", seeded a moment later by VervanApp's
            // cold-start persona seed — Workspace has no @ForeignKey, so ordering is safe.
            db.execSQL(
                "INSERT OR IGNORE INTO workspaces " +
                    "(id, name, description, personaId, isDefault, archived, createdAt, updatedAt, lastActiveAt) " +
                    "VALUES ('default', 'Default Workspace', " +
                    "'General-purpose workspace for conversations and documents', " +
                    "'builtin-general', 1, 0, $now, $now, $now)"
            )
            db.execSQL("ALTER TABLE chats ADD COLUMN workspaceId TEXT NOT NULL DEFAULT 'default'")
            db.execSQL("ALTER TABLE folders ADD COLUMN workspaceId TEXT NOT NULL DEFAULT 'default'")
            db.execSQL("ALTER TABLE documents ADD COLUMN workspaceId TEXT NOT NULL DEFAULT 'default'")
        }
    },
    object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Sampler seed override, and per-model tool approval mode (spec: model config screen).
            db.execSQL("ALTER TABLE models ADD COLUMN seed INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN toolApprovalMode TEXT NOT NULL DEFAULT 'ALWAYS_ASK'")
        }
    },
    object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Per-chat sampler overrides (spec §6/§7) — nullable, null means "inherit".
            db.execSQL("ALTER TABLE chats ADD COLUMN temperature REAL")
            db.execSQL("ALTER TABLE chats ADD COLUMN topP REAL")
            db.execSQL("ALTER TABLE chats ADD COLUMN topK INTEGER")
            // Scroll-position restore (spec §17).
            db.execSQL("ALTER TABLE chats ADD COLUMN scrollAnchorMessageId TEXT")
            db.execSQL("ALTER TABLE chats ADD COLUMN scrollAnchorOffsetPx INTEGER NOT NULL DEFAULT 0")
            // AI title generation (spec §18-20).
            db.execSQL("ALTER TABLE chats ADD COLUMN titleIsCustom INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chats ADD COLUMN previousTitle TEXT")
            db.execSQL("ALTER TABLE workspaces ADD COLUMN autoTitleGeneration INTEGER NOT NULL DEFAULT 0")
        }
    },
    object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Companion SentencePiece tokenizer file for a bare-TFLite-graph embedding model
            // (no bundled tokenizer, unlike a MediaPipe Task Bundle) — see RawTfliteEmbedder.
            db.execSQL("ALTER TABLE models ADD COLUMN tokenizerPath TEXT")
        }
    },
    object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Privacy hardening (Phase A) — per-chat screenshot block and incognito/temporary
            // chats; per-workspace lock and new-chat defaults (Phase A/E).
            db.execSQL("ALTER TABLE chats ADD COLUMN screenshotBlocked INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE chats ADD COLUMN isTemporary INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE workspaces ADD COLUMN lockEnabled INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE workspaces ADD COLUMN defaultProfile TEXT")
            db.execSQL("ALTER TABLE workspaces ADD COLUMN defaultKnowledgeBaseIds TEXT NOT NULL DEFAULT ''")
        }
    },
    object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Per-chat tool overrides — lets a chat turn a globally-disabled tool on (or a
            // globally-enabled one off) just for itself. See Chat.toolOverrideMap().
            db.execSQL("ALTER TABLE chats ADD COLUMN toolOverrides TEXT NOT NULL DEFAULT ''")
        }
    },
    object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Per-message generation stats (tokens/sec, total time), surfaced on tap in the
            // assistant bubble's action row. Null for existing rows — never generated live.
            db.execSQL("ALTER TABLE messages ADD COLUMN generationMs INTEGER")
            db.execSQL("ALTER TABLE messages ADD COLUMN tokenCount INTEGER")
        }
    },
    object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE messages ADD COLUMN documentId TEXT")
        }
    },
    object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Expense ledger — logged manually via the log_expense tool or from the Receipt
            // Scanner's "Log as expense" action.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS expenses (" +
                    "id TEXT NOT NULL PRIMARY KEY, merchant TEXT NOT NULL, amount REAL NOT NULL, " +
                    "currency TEXT NOT NULL DEFAULT '', category TEXT NOT NULL DEFAULT '', " +
                    "paymentMethod TEXT NOT NULL DEFAULT '', note TEXT NOT NULL DEFAULT '', " +
                    "date INTEGER NOT NULL, createdAt INTEGER NOT NULL)"
            )
        }
    },
    object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Downloaded voice files for the realtime voice pipeline's Piper/Kokoro TTS
            // engines (Supertonic manages its own storage via the SDK, no row here).
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS tts_voice_models (" +
                    "id TEXT NOT NULL PRIMARY KEY, engine TEXT NOT NULL, language TEXT NOT NULL, " +
                    "filePath TEXT NOT NULL, fileSizeBytes INTEGER NOT NULL, sha256 TEXT NOT NULL DEFAULT '', " +
                    "downloadedAt INTEGER NOT NULL, isReady INTEGER NOT NULL DEFAULT 1)"
            )
        }
    },
    object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Model downloader (see com.vervan.chat.modeldownload) — origin/catalogue
            // provenance columns on the existing models table, plus new package/file tables
            // tracking a download's own state machine separately from the installed-model row
            // ModelImportManager writes once import actually succeeds.
            db.execSQL("ALTER TABLE models ADD COLUMN origin TEXT NOT NULL DEFAULT 'LOCAL_IMPORT'")
            db.execSQL("ALTER TABLE models ADD COLUMN catalogModelId TEXT")
            db.execSQL("ALTER TABLE models ADD COLUMN catalogVersion TEXT")
            db.execSQL("ALTER TABLE models ADD COLUMN sourceUrl TEXT")
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS download_packages (" +
                    "id TEXT NOT NULL PRIMARY KEY, modelId TEXT NOT NULL, version TEXT NOT NULL, " +
                    "displayName TEXT NOT NULL, role TEXT NOT NULL, status TEXT NOT NULL, " +
                    "stopReason TEXT NOT NULL, totalBytes INTEGER, downloadedBytes INTEGER NOT NULL, " +
                    "currentFileId TEXT, errorCode TEXT, errorMessage TEXT, " +
                    "authRequired INTEGER NOT NULL, licenseAccepted INTEGER NOT NULL, " +
                    "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS download_files (" +
                    "id TEXT NOT NULL PRIMARY KEY, packageId TEXT NOT NULL, fileId TEXT NOT NULL, " +
                    "fileName TEXT NOT NULL, role TEXT NOT NULL, sourceUrl TEXT NOT NULL, " +
                    "resolvedUrl TEXT, tempPath TEXT NOT NULL, finalPath TEXT NOT NULL, " +
                    "expectedBytes INTEGER, downloadedBytes INTEGER NOT NULL, sha256 TEXT, " +
                    "etag TEXT, lastModified TEXT, acceptRanges INTEGER, status TEXT NOT NULL, " +
                    "retryCount INTEGER NOT NULL, errorMessage TEXT)"
            )
        }
    },
    object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ModelLoadCoordinator (spec: "Model Loading Strategy") — tracks the last time a
            // model was actually loaded into its native engine, used to pick a replacement
            // default when the current default is deleted.
            db.execSQL("ALTER TABLE models ADD COLUMN lastLoadedAt INTEGER")
        }
    },
    object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // llama.cpp (GGUF) backend support, alongside the existing LiteRT-LM runtime —
            // existing rows default to LITERT_LM. mmprojPath is only ever set for a
            // vision-capable llama.cpp model (its companion mtmd projector GGUF).
            db.execSQL("ALTER TABLE models ADD COLUMN engine TEXT NOT NULL DEFAULT 'LITERT_LM'")
            db.execSQL("ALTER TABLE models ADD COLUMN mmprojPath TEXT")
        }
    },
    object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Full LLM config exposure — common generation overrides, llama.cpp-only load/gen
            // overrides, and read-only GGUF metadata (all nullable = "inherit global default"
            // or, for the metadata trio, "not read yet").
            db.execSQL("ALTER TABLE models ADD COLUMN minP REAL")
            db.execSQL("ALTER TABLE models ADD COLUMN repetitionPenalty REAL")
            db.execSQL("ALTER TABLE models ADD COLUMN maxOutputTokens INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN stopSequences TEXT")
            db.execSQL("ALTER TABLE models ADD COLUMN gpuLayerCount INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN cpuThreads INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN nBatch INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN nUbatch INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN useMlock INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN flashAttention INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN kvCacheType TEXT")
            db.execSQL("ALTER TABLE models ADD COLUMN vulkanDeviceIndex INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN ropeFreqBase REAL")
            db.execSQL("ALTER TABLE models ADD COLUMN ropeFreqScale REAL")
            db.execSQL("ALTER TABLE models ADD COLUMN chatTemplateOverride TEXT")
            db.execSQL("ALTER TABLE models ADD COLUMN loraPath TEXT")
            db.execSQL("ALTER TABLE models ADD COLUMN loraScale REAL")
            db.execSQL("ALTER TABLE models ADD COLUMN modelDesc TEXT")
            db.execSQL("ALTER TABLE models ADD COLUMN nativeMaxContext INTEGER")
            db.execSQL("ALTER TABLE models ADD COLUMN layerCount INTEGER")
        }
    },
    object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Indices on every FK-shaped lookup column actually hit by a DAO WHERE clause — the
            // schema had none before this, so e.g. the main chat-list query's own
            // "EXISTS (SELECT 1 FROM messages WHERE messages.chatId = chats.id)" was a full scan
            // of messages for every chat, every emission. Names match Room's own auto-generated
            // convention (index_<table>_<column>) so this migration and the @Index annotations
            // in the entity classes describe the exact same schema.
            db.execSQL("CREATE INDEX IF NOT EXISTS index_messages_chatId ON messages(chatId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_workspaceId ON chats(workspaceId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_folderId ON chats(folderId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chats_projectId ON chats(projectId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chunks_documentId ON chunks(documentId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_chunks_knowledgeBaseId ON chunks(knowledgeBaseId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_knowledgeBaseId ON documents(knowledgeBaseId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_documents_workspaceId ON documents(workspaceId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_folders_workspaceId ON folders(workspaceId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_download_files_packageId ON download_files(packageId)")
        }
    },
    object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Character card import (SillyTavern PNG cards) — see Persona.avatarPath.
            db.execSQL("ALTER TABLE personas ADD COLUMN avatarPath TEXT")
            // Long-chat context management — see Chat.contextSummary/summaryCoversUpToMessageId.
            db.execSQL("ALTER TABLE chats ADD COLUMN contextSummary TEXT")
            db.execSQL("ALTER TABLE chats ADD COLUMN summaryCoversUpToMessageId TEXT")
        }
    },
    object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // reconcileCapabilities used to latch supportsAudio/supportsVision = false after ANY
            // degraded load (including transient memory-pressure failures), permanently hiding
            // audio/vision — including voice chat — for models that actually support them. The
            // latch logic is fixed to require proven absence; reset existing latched values so
            // affected LiteRT-LM models re-probe their real capabilities on next load.
            db.execSQL("UPDATE models SET supportsAudio = NULL WHERE engine = 'LITERT_LM' AND supportsAudio = 0")
            db.execSQL("UPDATE models SET supportsVision = NULL WHERE engine = 'LITERT_LM' AND supportsVision = 0")
        }
    },
    object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE memories ADD COLUMN embedding BLOB")
            db.execSQL("ALTER TABLE memories ADD COLUMN embeddingModelId TEXT")
            db.execSQL("ALTER TABLE messages ADD COLUMN memoryActivityJson TEXT")
        }
    },
    object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Model Store install pipeline (com.vervan.chat.store) — see StoreInstall.kt for why
            // these are separate tables rather than an extension of download_packages/files.
            // Purely additive: no existing table is touched, so a failure here cannot damage
            // chats, models, or in-flight downloads from the older pipeline.
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS store_install_sessions (
                    variantId TEXT NOT NULL PRIMARY KEY,
                    modelId TEXT NOT NULL,
                    displayName TEXT NOT NULL,
                    version TEXT NOT NULL,
                    runtime TEXT NOT NULL,
                    state TEXT NOT NULL,
                    totalBytes INTEGER NOT NULL,
                    downloadedBytes INTEGER NOT NULL,
                    currentArtifactId TEXT,
                    errorMessage TEXT,
                    catalogVersion INTEGER NOT NULL,
                    acceptedLicenseHash TEXT,
                    userRequestedStop INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS store_install_artifacts (
                    id TEXT NOT NULL PRIMARY KEY,
                    variantId TEXT NOT NULL,
                    artifactId TEXT NOT NULL,
                    role TEXT NOT NULL,
                    sourceIndex INTEGER NOT NULL,
                    sourceUrl TEXT NOT NULL,
                    resolvedUrl TEXT,
                    tempPath TEXT NOT NULL,
                    expectedBytes INTEGER NOT NULL,
                    downloadedBytes INTEGER NOT NULL,
                    expectedSha256 TEXT NOT NULL,
                    etag TEXT,
                    lastModified TEXT,
                    state TEXT NOT NULL,
                    retryCount INTEGER NOT NULL,
                    errorMessage TEXT
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_store_install_artifacts_variantId " +
                    "ON store_install_artifacts(variantId)"
            )
        }
    }
)
