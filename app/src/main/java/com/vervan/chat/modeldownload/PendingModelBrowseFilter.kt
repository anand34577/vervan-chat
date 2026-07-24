package com.vervan.chat.modeldownload

/**
 * One-shot handoff from the Model Calculator's "Browse models that fit" action to Model Manager —
 * same in-memory, consume-once pattern as [com.vervan.chat.model.PendingChatSend], for the same
 * reason: this is a transient "the next screen open should react to this" signal, not state worth
 * a DB column or a nav argument. Model Manager consumes it once on entry to auto-expand the
 * catalogue and sort by fit; a plain "Model manager" open from anywhere else (bottom nav, Home,
 * Settings) never sees it.
 */
object PendingModelBrowseFilter {
    @Volatile private var budgetBytes: Long? = null

    fun stash(budgetBytes: Long) {
        this.budgetBytes = budgetBytes
    }

    fun consume(): Long? {
        val value = budgetBytes
        budgetBytes = null
        return value
    }
}
