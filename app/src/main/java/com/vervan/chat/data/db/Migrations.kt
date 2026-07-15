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
    }
)
