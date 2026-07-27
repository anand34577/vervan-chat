package com.vervan.chat.ui.nav

/**
 * Central definitions for the shell and chat route family. Keeping the literal chat suffixes
 * here prevents a generic action route from drifting back into conflict with `/tree` or `/info`.
 */
internal object AppRoutes {
    const val HOME = "home"
    const val ONBOARDING = "onboarding"

    const val CHAT = "chat/{chatId}"
    const val CHAT_START = "chat/{chatId}/start/{startAction}"
    const val CHAT_TREE = "chat/{chatId}/tree"
    const val CHAT_INFO = "chat/{chatId}/info"

    fun chat(chatId: String) = "chat/$chatId"
    fun chatStart(chatId: String, action: String) = "chat/$chatId/start/$action"
    fun chatTree(chatId: String) = "chat/$chatId/tree"
    fun chatInfo(chatId: String) = "chat/$chatId/info"
}
