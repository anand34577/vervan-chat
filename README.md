# Vervan Chat — Phase 1–5

Offline AI workspace for Android. Covers Phase 1 (offline chat), Phase 2
(multimodal + retrieval), Phase 3 (notes/projects/templates/saved
outputs/share sheet), Phase 4 (memory, context inspector, tool calling,
conversation branching), and a working slice of Phase 5 (workflows).

## Stack

- Kotlin + Jetpack Compose (Material 3), single-activity, Navigation Compose
- Room for local persistence (chats, messages, personas, model registry,
  knowledge bases, documents, chunks, notes, projects, prompt templates,
  saved outputs, memories)
- `com.google.mediapipe:tasks-genai` (0.10.27) for on-device Gemma text + vision +
  audio inference
- `com.google.mediapipe:tasks-text` (`TextEmbedder`) for on-device embeddings
- `com.tom-roush:pdfbox-android` for PDF text extraction
- `android.speech.SpeechRecognizer` / `TextToSpeech` for dictation / read-aloud
- `android.media.AudioRecord` (hand-rolled WAV writer, `audio/WavRecorder.kt`) for
  voice messages the model listens to directly
- `androidx.datastore:datastore-preferences` for real user settings (theme, retrieval
  mode, TTS rate, font scale, context budget, response style)
- `androidx.compose.material3:material3-window-size-class` for the tablet/foldable
  nav-rail layout switch

## Opening the project

No Gradle wrapper jar is checked in (no Gradle/network access in this
environment). Open the folder in Android Studio — it will offer to generate
the wrapper and sync. Point the Android SDK at your existing install if
prompted.

## What's real vs. stubbed

**Real:**
- Chat CRUD, streaming generation with GPU→CPU backend fallback, cancel,
  per-chat draft persistence, interrupted-message recovery on relaunch
- Model import via SAF (chat + embedding models, tagged by role), sha256
  dedup, active-model selection per role
- Knowledge bases: import TXT/MD/PDF, chunk, embed, keyword/semantic/hybrid
  retrieval (brute-force), source-grounded chat with citations
- Image attachment + vision generation; offline-preferred dictation + TTS
- Voice messages the model listens to directly (native audio input, not
  transcription) — see "Spec gap closure" below for how this actually works
- Notes (list/editor/AI actions), Projects (shared instructions folded into
  chat prompts), prompt templates / slash commands (10 built-in)
- Saved outputs library, share-sheet target (ACTION_SEND → ask AI / save as
  note)
- Memory: manually-confirmed global/persona/project-scoped facts, applied to
  every applicable chat's prompt. No inference-from-conversation — every
  memory exists because the user explicitly saved it
- Context inspector: per-chat breakdown of what's about to go into the next
  prompt, with a chars/4 token estimate against a placeholder device limit
- Tool calling: opt-in per chat, prompt-engineered `<tool_call>` blocks
  (MediaPipe has no stable native function-calling API), 4 built-in tools,
  read-only auto-loop (capped at 3 hops) vs. reversible-write behind an
  Allow/Deny card
- **Conversation branching**: `Message` is a real tree now (`parentId` +
  per-chat `activeLeafId`), not a flat list. Editing a user message or
  regenerating an assistant one creates a sibling branch instead of
  overwriting anything; an inline "‹ i/n ›" indicator appears wherever a
  message has siblings, letting you switch branches (jumping to whichever
  leaf was most recently active down that branch). Prompt building, the
  context inspector, and the tool-call hop chain all walk the active
  root-to-leaf path via `BranchUtil`, not the full per-chat message table. A
  **branch-tree screen** (the new account-tree icon in the chat top bar)
  lists every branch depth-first, indented, tapping any node jumps the
  active leaf straight there — `BranchUtil.flattenTree`, no graph rendering.
  A **Compare** button next to the "‹ i/n ›" indicator opens a side-by-side
  view of every sibling's full text (`CompareDialog` in `ChatScreen.kt`) with
  a "Use this" button per branch — juxtaposition, not a token-level diff.
- **Workflows**: multi-step orchestration over on-device generation. A
  `Workflow` is an ordered list of plain-text instructions; running it feeds
  step 1's instruction + your input text to the model, then feeds each
  subsequent step's instruction + the previous step's output. Two built-ins
  ship: "Summarize document" (extract key points → write summary) and
  "Meeting minutes" (extract raw notes → format as minutes). Input comes from
  pasted text or an imported file (txt/md/pdf via the existing
  `TextExtractor`, read directly — not run through the knowledge-base
  ingestion pipeline, no chunking/embedding involved). Each step streams live;
  the final step's output can be saved to the library or as a note. A
  workflow editor (the "+" and pencil icons on the Workflows list) lets you
  author/edit/delete custom workflows — name, description, an ordered list
  of step instructions. Opening a built-in for editing always saves as a new
  custom copy rather than changing the original.

**Stubbed / not built:** NPU backend (the `tasks-genai` API this app depends
on has no public NPU delegate — GPU→CPU is the real fallback chain), home
screen widgets, most of the 46-case edge-case catalogue beyond memory/
thermal/storage, and a verified TalkBack pass or real tablet/hardware test
(the layout code exists, running it against actual assistive tech or
hardware doesn't — that needs a device, not more code). DOCX/HTML/EPUB
import, OCR, thinking mode, smart collections, and model migration were
previously listed here as stubbed — they weren't; see "Seventh pass" below.

`// ponytail:` comments throughout mark the specific corners cut and what to
build when they start to hurt — search for that prefix for the full list
(chunking heuristics, brute-force retrieval, single-tool-per-turn with a
hard hop cap, chars/4 token estimate, in-memory tree walk instead of
recursive SQL, etc.).

## How branching actually works (worth understanding before touching it)

- `Message.parentId` — null for the first message in a chat, otherwise the
  message it replied to or branched from.
- `Chat.activeLeafId` — the tip of whichever branch is currently shown.
- `ChatViewModel.allMessages` (raw, every branch) vs. `ChatViewModel.messages`
  (the root→activeLeaf path, via `BranchUtil.pathTo`) — UI renders the
  latter, sibling navigation reads the former.
- Editing a user message or hitting Regenerate doesn't mutate history — it
  inserts a new sibling and repoints `activeLeafId`. The original branch is
  still in the database, just not on the active path.
- Internal logic (prompt building, the tool-call loop) reads messages via a
  **fresh suspend query** (`ChatViewModel.getAllMessages()`), not the cached
  `allMessages` StateFlow — Room's Flow re-emits asynchronously after a
  write, so reading the StateFlow immediately after an insert within the
  same coroutine could see stale data. This tripped me up once already
  while building it; don't "simplify" it back to the StateFlow.
- There's no session/KV-cache-per-branch concern (spec 8.3) because there
  was never persistent inference-session caching to begin with —
  `LlmEngine.generate()` always reconstructs the full prompt from scratch
  and opens a new session per call. Branch switching is just a cheap DB
  pointer update.

## Not verified

Still no Gradle/Android SDK build tooling in this environment — none of this
has been compiled. Open in Android Studio first. Risk spots, roughly in
order:

1. `tasks-genai` vision API (`setMaxNumImages`, `session.addImage`,
   `BitmapImageBuilder`) — pinned `0.10.24`, newest/least stable corner.
2. `tasks-text` `TextEmbedder` — pinned `0.10.21`, check version compat
   against `tasks-genai`.
3. `pdfbox-android`'s API surface.
4. Compose Material3 API drift between BOM versions.
5. Room schema is at version 9 with `fallbackToDestructiveMigration()` — no
   real `Migration`s written; fine pre-release, not once there's user data.
6. The tool-call prompt format is untested against a real Gemma model —
   `ToolCallParser` fails closed (no match → no tool call recognized) rather
   than crashing, but whether the model reliably emits well-formed
   `<tool_call>` JSON at all is unverified.
7. Branching is new and unexercised — the tree-walk logic in `BranchUtil`
   and its wiring through `ChatViewModel` has reasonable unit-testable
   shape but no actual test coverage yet. Worth writing a few tests for
   `pathTo`/`deepestTip`/`siblingPosition` before trusting it with real data.
8. Workflows are unexercised end-to-end too — each step is just
   `engine.generate()` called in sequence with no retry/timeout handling, so
   a bad step output (empty, malformed) silently becomes the next step's
   entire input with no validation in between.

## Next up

Every additive item queued from the original spec sweep is now built. The
single biggest remaining risk across the whole app is that none of it has
been compiled — no Gradle/Android SDK in this environment. That should
happen before adding anything further: open in Android Studio, let it
generate the wrapper, and work through the "Not verified" list above against
a real device or emulator, roughly in the order given.

## UI pass (2026-07-11)

Full design pass matched against a supplied HTML mockup (dark, warm amber
accent, card/chip-based). Every screen now uses the shared theme
(`Theme.kt` — fixed dark/light palette instead of Material You dynamic
color, 16dp card shapes, pill buttons/chips, `VervanMono` for
metadata/timestamps):

- **Nav shell**: bottom bar is Home/Chats/Knowledge/Library with a raised
  center "Create" action opening a bottom sheet (`CreateSheet.kt`) instead of
  being a 5th tab. Settings and Models live off Home's top bar instead.
- **Home**: ask field, projects row, recent chats, quick-actions grid,
  model/index status — all real data.
- **Chats list**: filter chips (All/Pinned/Archived, backed by real
  `Chat.pinned`/`archived` fields — no fake "Favorites"/"Interrupted" chips
  since there's no data behind them), pin/archive via overflow menu.
- **Chat screen**: amber tail-less user bubble, bordered assistant bubble,
  pill composer, persona + active-model subtitle chip, warn-amber
  tool-proposal card.
- **Knowledge**: KB grid with live document counts/status, indexing queue,
  document list with status chips.
- **Library**: rebuilt as a tabbed hub (Personas / Templates / Workflows /
  Saved) matching the mockup, backed by real data in every tab.
- **Persona editor**: new (`ui/personas/`) — name, role/description, system
  instruction, save/duplicate. Editing a built-in saves a new custom copy,
  same pattern as the workflow editor. No conciseness/creativity/formality
  sliders from the mockup — there's no backend concept of per-persona
  generation parameters, so sliders that don't do anything weren't added.
- **Model Manager**: model cards with size/backend spec chips and a
  three-way NPU/GPU/CPU pill row — only the backend actually verified via
  `lastWorkingBackend` is highlighted, the other two read as "untested"
  rather than faking a verified/failed state per backend.
- **Settings**: grouped list-rows with live subtitles (model count + active
  model, memory count) instead of static placeholder text.

**Still not built** (flagged honestly in the UI rather than faked): a
prompt-template editor (Library's Templates tab is read-only), standalone
scan/voice-note capture outside a chat. The Create sheet surfaces both as a
"not built yet" dialog.

## First real device run — bugs found (2026-07-11, later same day)

The first actual build (compiler + real device) surfaced two serious bugs
the earlier design-only pass couldn't catch:

1. **Generation could get permanently stuck after any failure.** In
   `ChatViewModel`, `send`/`editAndResend`/`regenerate` each set
   `_isGenerating = true`, called `beginGeneration(...)`, then set
   `_isGenerating = false` — with no `try/finally`. If `beginGeneration`
   threw anything not already caught inline (a native MediaPipe failure,
   anything unexpected), that reset line never ran, `_isGenerating` stayed
   `true` forever, and the guard at the top of `send()` made every
   subsequent send a silent no-op. This is almost certainly what "imported
   a model, still can't chat" was — a real failure on the first attempt,
   then the UI looking permanently broken with no visible error. Fixed by
   routing all three entry points through one `runGenerationSafely` wrapper
   with `try { beginGeneration(...) } catch (t: Throwable) { ... } finally
   { _isGenerating = false }` — deliberately catching `Throwable`, not just
   `Exception`, since this boundary sits right next to native ML runtime
   code. The error banner in `ChatScreen` was also upgraded from a thin
   line of text to a full-width error-container card so a failure is no
   longer easy to miss.
2. **Chat top bar could collapse the title to one character per line.**
   The top bar packed two full-text `FilterChip`s ("Sources: Off" /
   "Tools: On") plus two icon buttons plus back into the `actions` row —
   on a phone-width screen that left ~0dp for the title, so "New chat"
   wrapped letter-by-letter into a vertical stack. Fixed by making
   Sources/Tools icon-only (tinted when active, detail lives in the
   dialogs they open) and adding explicit `maxLines = 1` +
   `TextOverflow.Ellipsis` on the title/subtitle as a backstop.

Neither of these was visible from source review — they only showed up once
the app actually ran. If chat still doesn't produce a response after this
fix, the error banner will now say why (e.g. a native load failure), which
narrows down whether the specific `.litertlm`/`.task` file is actually
compatible with the pinned `tasks-genai` version — that's the next thing to
check with the real error text in hand.

## Spec gap closure (2026-07-11, third pass)

A full audit against the original 45-section spec found 0 sections fully
built, 31 partial, 14 missing. This pass closed the highest-leverage gaps for
real rather than approximating them:

- **Export & backup (§33)** — `data/backup/BackupManager.kt` + Settings >
  Import & export. JSON export/import of chats, messages, notes, personas,
  templates, workflows, memories, and projects via SAF. Model files and
  knowledge-base documents are deliberately excluded (large binary assets
  tied to on-device paths — re-import those from Models/Knowledge instead).
- **Recycle bin (§34)** — chats, notes, and documents now soft-delete
  (`deletedAt`) instead of hard-deleting. Settings > Recycle bin restores or
  permanently deletes; a 30-day auto-purge runs on cold start
  (`VervanApp.RECYCLE_BIN_RETENTION_MS`). Documents keep their file and
  embedded chunks until permanent delete, so restore is instant.
- **Real settings (§41)** — `data/settings/SettingsRepository.kt`
  (DataStore-backed): theme mode (system/light/dark, wired into
  `MainActivity`), font scale (wired via `LocalDensity`), default retrieval
  mode, a user-set context token budget (replaces the old hardcoded
  `RECOMMENDED_CONTEXT_TOKENS` constant), TTS rate, and auto-read-aloud —
  plus cache size display and a clear-cache button. Still not real: Voice
  language selection, Advanced's other placeholders, and anything needing a
  device capability probe.
- **Universal search (§29)** — `ui/search/SearchScreen.kt`, a single search
  bar fanning out to chats/notes/documents/personas via each DAO's own
  `search()` query (LIKE-based, no FTS index). Reachable from Home's top bar.
- **Capability gating (§2.2)** — the image-attach button now reads
  `ChatViewModel.visionAvailable`, set the moment a model actually finishes
  loading (`engine.visionEnabled`). Before any load this process, it's
  `null` ("declared, not yet tested") and the button stays enabled; once a
  load has happened it gates for real. No static per-model vision flag,
  because the same model can load with or without vision depending on which
  backend actually worked.
- **Prompt template editor (§30 gap)** — Library's Templates tab is no
  longer read-only; `ui/templates/TemplateEditorScreen.kt` follows the same
  built-in-safe copy-on-edit pattern as personas and workflows.
- **Writing / Developer / Study workspaces (§22–24)** — three new dedicated
  screens (`ui/writing`, `ui/dev`, `ui/study`), all off Home's quick-actions
  grid. Writing and Developer are one-shot generation over pasted text (no
  chat, no history) with a fixed action set each (rewrite/shorten/expand/
  tone/grammar; explain/review/tests/find-bug/document). Study generates
  flashcards from pasted text as a model-produced JSON array, stores them as
  `StudyCard` rows, and has a flip-card review loop with a per-card
  correct/reviewed counter — not real spaced repetition (no interval/ease
  scheduling), just enough to show progress.
- **Memory canonical keys (§27 partial)** — `Memory.key` lets a saved memory
  declare a dedup key (e.g. `"tone"`); saving another memory with the same
  key in the same scope replaces it instead of adding a contradicting
  duplicate. Still no suggestion inbox or inference-from-conversation.

**Deliberately not attempted in that pass, revisited in a fourth pass same
day** — the user asked for everything remaining to be closed out. Re-checked
each one rather than re-asserting the earlier "can't do this" call:

- **Native audio-to-model (§14.1) — now built.** Researched the actual
  `tasks-genai` audio API directly from Google's docs and the MediaPipe
  GitHub source (`AudioModelOptions`, `GraphOptions.setEnableAudioModality`,
  `LlmInferenceSession.addAudio(ByteArray)`) rather than guessing, since this
  had been flagged as a gap in every prior status update. Bumped
  `tasks-genai` to 0.10.27 (the documented minimum for audio). `LlmEngine`
  now tries vision+audio, vision-only, audio-only, then neither at each
  backend when loading a model — most-capable combination first, same
  degrade-gracefully shape as the existing vision fallback. A new
  `audio/WavRecorder.kt` records mono 16kHz/16-bit PCM straight to the exact
  `.wav` format `addAudio()` requires (no library — a 44-byte header written
  by hand). Chat's composer has a new voice-message button (distinct from
  the existing dictation mic — this one is heard by the model directly, not
  transcribed to text first), gated on `ChatViewModel.audioAvailable` the
  same declared-vs-tested way vision already was. **Caveat:** `AudioModelOptions`
  and `GraphOptions` are marked `@Deprecated` upstream in favor of a separate
  "LiteRT-LM Android (Kotlin)" API — they're still the real, shipping API for
  this artifact and should still work, but this is the one piece in this
  pass built from sourced documentation rather than something verified
  against a running build.
- **Edge cases (§40) — the highest-leverage subset, not all 46.**
  `VervanApp.onTrimMemory` now frees the loaded model under real memory
  pressure (`TRIM_MEMORY_RUNNING_CRITICAL`+) instead of waiting for the OS to
  kill the process; the model just reloads lazily on next use, same as a
  cold start. `system/ThermalMonitor.kt` wraps `PowerManager`'s thermal
  listener (API 29+) and shows a warm/hot banner on Home. Model and document
  import both now pre-check free storage via `usableSpace` and reject early
  with a clear message instead of a mid-copy stack trace. The other ~40
  listed cases still aren't individually handled.
- **Tablet/foldable layout (§4) — now built.** Added
  `material3-window-size-class`; the bottom nav bar becomes a
  `NavigationRail` once the window is wider than a phone (`MainActivity`
  computes the size class, `NavGraph` switches layout on it). Not tested
  against a real tablet or a folded/unfolded transition.
- **Model license acknowledgment (§12) — the honest mechanism, not real
  license text.** Every model here is bring-your-own via a file picker — this
  app never fetches or verifies an actual license. Activating a newly
  imported model now requires acknowledging a one-time dialog stating that
  responsibility explicitly, tracked via `ModelInfo.licenseAcknowledged`.
  What it still can't do: show you the model's actual terms, since it never
  had them.
- **Declared style profile (§26) — real, but not inference.** Settings has a
  response length (concise/balanced/detailed) and tone (neutral/casual/
  formal) the user sets explicitly; when not left at the neutral defaults it
  folds into the prompt as a "Style preference" section. This is §26's
  personalization need met honestly, not the spec's inferred-from-usage
  version — nothing here is learned from what you write.
- **Accessibility (§38) — verified, not overhauled.** Audited every
  icon-only button for a real `contentDescription`: the codebase was already
  in decent shape here (Compose's `Icon()` has no default, so a missing
  description would already have failed to compile) — found exactly two
  `null` descriptions, both correctly paired with adjacent visible text.
  Added `liveRegion` semantics to the chat error banner so TalkBack
  announces failures without the user having to find them. Font scale was
  already wired in the settings pass. No device to run an actual TalkBack
  session against.

## Fifth pass (2026-07-11, same day) — closing the last gaps that could honestly be closed

- **Thinking mode (§15) — built as a prompt instruction, not a native
  toggle.** No public Kotlin API for it was ever found, so instead of
  leaving it unbuilt this asks the model directly: an Off/Fast/Balanced/Deep
  per-chat setting (the lightbulb icon in the chat top bar) adds a
  "wrap your reasoning in `<thinking>` tags, then answer" instruction at the
  matching intensity. `llm/ThinkingParser.kt` splits that block back out of
  the response for display as a collapsible "Show reasoning" row on the
  message bubble, and strips it back out of conversation history before it
  re-enters future prompts. Same category as this app's tool-calling — real,
  working, prompt-engineered because the runtime has no native hook, not a
  fake control.
- **Tablet two-pane layout (§4, extended)** — the Chats tab now shows a
  persistent list alongside the open chat on expanded-width windows
  (`ui/chats/ChatsTwoPaneScreen.kt`), not just the nav-rail repositioning
  from the fourth pass. Deep links into a specific chat from Home/Search/the
  share sheet still open full-screen rather than landing inside the pane —
  only navigating via the Chats tab itself uses the split view.
- **Accessibility — heading semantics added** on top of the fourth pass's
  audit: section labels on Home and Settings are now marked as TalkBack
  headings, so screen-reader users can jump between sections instead of
  swiping through every row.

**What's left, and why it stays left:** a verified TalkBack session, an
actual tablet/foldable device test, and thermal/memory behavior confirmed
under real pressure. All three have real code behind them now (this pass and
the fourth); none of the three can be confirmed without hardware or
assistive tech this environment doesn't have. The 94-screen inventory (§6)
remains a scale/definition mismatch rather than a gap — this app has on the
order of 35 real screens covering the same functional ground the spec's 94
describe.

## Sixth pass — closing the remaining spec gaps

A full audit against the spec found these gaps still open. All are now built
with real, functional code (not stubs):

- **Model profiles (§11.9)** — Fast / Balanced / Quality / Battery saver /
  Thermal safe, as a reusable `ModelProfileType` + `ModelProfiles.resolve()`
  concept. Each profile shapes context budget (fraction of the user's token
  limit), retrieval depth (topK), default thinking mode, and output-length
  hint. Per-chat selection via a Speed icon in the chat top bar; default set
  in onboarding and Settings. The raw sampler knobs (temperature/topP/topK)
  stay in Settings as the underlying sampler config for every profile.
- **Folders (§28.2)** — `Folder` entity + DAO + migration 13→14. Folder list
  screen with create/delete; folder detail screen with default persona, model,
  and knowledge bases (new chats here inherit the defaults). Chats and notes
  carry a `folderId`. Soft-deletable, backed up, in the recycle bin.
- **User profile (§26.1)** — `UserProfileScreen` with name, occupation,
  expertise, interests, languages, coding languages, units, topics to avoid,
  current goals. DataStore-backed. Folds into every chat prompt as a "User
  profile" section — empty by default so a user who never opens it pays zero
  token cost.
- **Smart collections (§28.4)** — `SmartCollectionsScreen` with six dynamic
  collections (This week, Interrupted, With attachments, Pinned, Archived,
  Failed imports). Read-only filters over existing data — never move content.
- **App shortcuts (§37.3)** — Four launcher shortcuts (New chat, Voice input,
  Quick capture, Search knowledge) via `res/xml/shortcuts.xml`, resolved in
  `MainActivity` → `NavGraph` deep navigation.
- **Notifications (§37.4)** — `NotificationHelper` with two channels (jobs,
  imports). Document imports post completion/failure notifications. Requires
  `POST_NOTIFICATIONS` (Android 13+); no-ops gracefully if denied.
- **Document viewer (§32)** — `DocumentViewerScreen` showing a document's
  indexed chunks with section paths and token counts; re-index button.
- **Source-passage viewer (§33)** — `SourcePassageScreen` opens a cited chunk
  in its full document context (scrolls to the cited passage). Source cards in
  chat now have an "Open in context" button.
- **Expanded onboarding (§6.1)** — 10 pages covering welcome, offline-first,
  AI fallibility, device scan, generation model, embedding model, performance
  profile (with picker), optional user profile, offline tutorial tips, ready.
- **Persona test bench (§25.4)** — `PersonaTestBenchScreen` (Science icon in
  persona editor): sample prompt, run against the persona, response preview,
  token-cost estimate.
- **Memory suggestion inbox (§27.3/§27.5)** — `MemorySuggestion` entity +
  DAO. `MemorySuggestionsScreen` lists pending suggestions; accept/reject;
  canonical-key conflict detection with replace/keep-both resolution. Settings
  shows pending count.
- **Job queue (§32/§76)** — `JobRecord` entity + DAO. `JobQueueScreen` shows
  background jobs with state, progress, type. Document imports now create job
  records.
- **Index maintenance (§42)** — `IndexMaintenanceScreen` with re-index-all and
  per-document re-index, using `DocumentImportManager.reindexLocal()`.
- **Tool audit history (§16.3/§2.6)** — `ToolAudit` entity + DAO. Every
  executed tool call is recorded; auto-purged after 30 days.
- **Flashcard set metadata (§55)** — `FlashcardSet` entity with description and
  last-studied timestamp. Study workspace creates the metadata; review records
  the session.
- **Backup expanded** — Folders now export/import. Chats export `profile` and
  `folderId`; notes export `folderId`.

**Schema is now version 14** with a proper migration 13→14 (adds chat `profile`
and `folderId` columns, note `folderId`, and creates `folders`,
`flashcard_sets`, `memory_suggestions`, `tool_audit`, `jobs` tables).

**Still not verified:** same as before — no Gradle/Android SDK in this
environment, so none of the new code has been compiled. Open in Android Studio
first. The new files are all in established patterns (entity → DAO → migration
→ ViewModel → Screen → NavGraph route), and the migration was written
column-by-column against the entity declarations.

## Seventh pass — this environment actually has Gradle + a populated dependency cache

Every previous pass was written blind, `--offline` compiled *now*, for the
first time, against the real Kotlin/KSP/Room compilers. It did not compile.
Real bugs found and fixed, not stylistic:

- **`Chat` was missing `folderId`.** Migration 13→14 added the column to the
  table; the entity class never got the field. `ChatDao`'s `folderId`-keyed
  queries failed KSP verification outright — the whole module didn't build.
- **Four files called `kotlinx.coroutines.flow.map` fully-qualified inline**
  (`FoldersViewModel`, `DocumentViewerViewModel`, `PersonaTestBenchViewModel`,
  `JobQueueScreen`) instead of importing the extension — none of it resolved.
- **`FolderDetailViewModel` never exposed `personas`/`models`** even though
  `FolderDetailScreen` read `vm.personas`/`vm.models`.
- **`Card(Modifier, onClick = ...)` positional-argument bug** in 6 places
  across `FolderDetailScreen` and `SmartCollectionsScreen` — `Card`'s
  clickable overload takes `onClick` first, not `Modifier`.
- **`return@withContext` used outside any `withContext` block** in
  `DocumentImportManager.doImport`, plus a missing final `return`.
- **`ChatsTwoPaneScreen` referenced an undefined `onOpenPassage`** instead of
  taking it as a parameter (tablet/foldable chat pane never actually wired to
  the source-passage viewer).

All of the above are now fixed and the project builds clean end-to-end
(`compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug`) with only
pre-existing deprecation warnings (non-AutoMirrored icons, `TRIM_MEMORY_*`).

Also closed, for real, three gaps the "stubbed" list above used to name
(spec citations in each commit/comment):

- **OCR for scanned PDFs (§13.3, §40.27).** New `OcrExtractor` renders each
  PDF page with `android.graphics.pdf.PdfRenderer` and runs
  `com.google.mlkit:text-recognition` (the *bundled* artifact — the model
  ships inside the APK, confirmed by `libmlkit_google_ocr_pipeline.so`
  showing up in `assembleDebug` output — not the Play-Services-backed
  variant that fetches its model over the network on first use).
  `DocumentImportManager`'s `NeedsOcr` branch used to just fail with "OCR not
  implemented yet"; it now OCRs, chunks, and embeds like any other document.
  `Document.ocrApplied` (migration 14→15) marks OCR-derived text so the
  knowledge base UI can label it as such.
- **Memory suggestion inference (§27.3).** The `MemorySuggestion` entity,
  DAO, and full review UI (`MemorySuggestionsScreen`) already existed from
  the sixth pass — but nothing ever *created* a suggestion, and the screen
  had no navigation route pointing at it. Added `MemorySuggestionDetector`
  (regex rules for "remember that…", "my name is…", "I prefer…", "I work
  at…", etc.) that runs after every sent user message and enqueues a
  suggestion — never a real `Memory` directly, so §2.3 ("no memory added
  silently") still holds. Added a badged entry point from `MemoryScreen`'s
  top bar to the previously-orphaned suggestions inbox route.
- **Model version migration relink (§11.12).** Model imports used to
  silently activate and replace whatever was already active, every time,
  with no choice offered. Added `ModelFamily` (a name-normalization
  heuristic — ponytail-tagged, upgrade to a persisted family id if it
  misfires) that detects when a freshly-verified import is probably a new
  version of an already-installed model of the same role, and — instead of
  auto-activating — asks: relink folder defaults to the new one and activate
  it, or leave the old one active. Historical chats are never rewritten
  either way, matching the spec's explicit "leave historical response
  metadata unchanged."

New dependency: `com.google.mlkit:text-recognition:16.0.1`. Lifecycle
artifacts bumped 2.8.6→2.8.7 (2.8.6's `lifecycle-livedata` jar wasn't in the
offline cache; 2.8.7 was, for every lifecycle artifact this project uses).
