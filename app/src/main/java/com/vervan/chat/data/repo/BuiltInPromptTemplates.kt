package com.vervan.chat.data.repo

import com.vervan.chat.data.db.entities.PromptTemplate

object BuiltInPromptTemplates {
    val defaults = listOf(
        PromptTemplate(id = "builtin-summarize", name = "summarize", description = "Summarize text", body = "Summarize the following concisely:\n\n{{input}}", isBuiltIn = true),
        PromptTemplate(id = "builtin-rewrite", name = "rewrite", description = "Rewrite text", body = "Rewrite the following, keeping the same meaning:\n\n{{input}}", isBuiltIn = true),
        PromptTemplate(id = "builtin-translate", name = "translate", description = "Translate text", body = "Translate the following. Detect the source language and ask which target language if it isn't specified:\n\n{{input}}", isBuiltIn = true),
        PromptTemplate(id = "builtin-explain", name = "explain", description = "Explain something", body = "Explain the following clearly, as if to someone unfamiliar with the topic:\n\n{{input}}", isBuiltIn = true),
        PromptTemplate(id = "builtin-simplify", name = "simplify", description = "Simplify text", body = "Rewrite the following in simpler, plainer language:\n\n{{input}}", isBuiltIn = true),
        PromptTemplate(id = "builtin-expand", name = "expand", description = "Expand text", body = "Expand the following with more detail and context:\n\n{{input}}", isBuiltIn = true),
        PromptTemplate(id = "builtin-shorten", name = "shorten", description = "Shorten text", body = "Shorten the following while keeping its key points:\n\n{{input}}", isBuiltIn = true),
        PromptTemplate(id = "builtin-compare", name = "compare", description = "Compare items", body = "Compare the following, highlighting key similarities and differences:\n\n{{input}}", isBuiltIn = true),
        PromptTemplate(id = "builtin-todo", name = "todo", description = "Extract action items", body = "Extract action items from the following as a checklist:\n\n{{input}}", isBuiltIn = true),
        PromptTemplate(id = "builtin-table", name = "table", description = "Convert to table", body = "Reformat the following as a markdown table:\n\n{{input}}", isBuiltIn = true)
    )
}
