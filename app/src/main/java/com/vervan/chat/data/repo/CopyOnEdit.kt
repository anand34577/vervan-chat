package com.vervan.chat.data.repo

import java.util.UUID

/** Persona/Template/Workflow editors all save a built-in-opened-for-edit as a new custom
 * copy, keeping the original id only when editing an existing custom entry. */
fun resolveEditId(existingId: String?, isBuiltIn: Boolean): String =
    if (existingId != null && !isBuiltIn) existingId else UUID.randomUUID().toString()
