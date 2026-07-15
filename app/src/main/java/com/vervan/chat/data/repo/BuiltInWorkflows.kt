package com.vervan.chat.data.repo

import com.vervan.chat.data.db.entities.Workflow

object BuiltInWorkflows {
    val defaults = listOf(
        Workflow(
            id = "builtin-summarize-document",
            name = "Summarize document",
            description = "Extract key points, then write a concise summary from them",
            stepsJson = Workflow.encodeSteps(
                listOf(
                    "Extract the key points from the following document as a plain list:",
                    "Turn the following key points into a concise, well-written summary:"
                )
            ),
            isBuiltIn = true
        ),
        Workflow(
            id = "builtin-meeting-minutes",
            name = "Meeting minutes",
            description = "Extract attendees, decisions, and action items, then format as minutes",
            stepsJson = Workflow.encodeSteps(
                listOf(
                    "From the following meeting transcript, extract: attendees, key discussion points, decisions made, and action items with owners if mentioned. Output as raw notes:",
                    "Format the following raw notes into clean meeting minutes with headings: Attendees, Discussion, Decisions, Action Items:"
                )
            ),
            isBuiltIn = true
        ),
        Workflow(
            id = "builtin-extract-action-items",
            name = "Extract action items",
            description = "Pull out action items with owners, then format as a checklist",
            stepsJson = Workflow.encodeSteps(
                listOf(
                    "Extract every action item, task, or commitment mentioned in the following text, with an owner if one is named:",
                    "Format the following action items as a Markdown checklist, one per line, grouped by owner if owners are known:"
                )
            ),
            isBuiltIn = true
        ),
        Workflow(
            id = "builtin-build-faq",
            name = "Build FAQ",
            description = "Turn source material into a question/answer FAQ",
            stepsJson = Workflow.encodeSteps(
                listOf(
                    "Read the following material and list the 5-10 most likely questions a reader would have, one per line:",
                    "For each question below, write a concise, accurate answer based on the material this workflow started with. Format as Q: / A: pairs:"
                )
            ),
            isBuiltIn = true
        ),
        Workflow(
            id = "builtin-translate-and-polish",
            name = "Translate and polish",
            description = "Translate to English, then clean up grammar and flow",
            stepsJson = Workflow.encodeSteps(
                listOf(
                    "Translate the following text into English, preserving meaning and tone:",
                    "Polish the following translated text for natural, fluent English without changing its meaning:"
                )
            ),
            isBuiltIn = true
        )
    )
}
