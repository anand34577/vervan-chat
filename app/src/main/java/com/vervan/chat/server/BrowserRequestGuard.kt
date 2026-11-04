package com.vervan.chat.server

/**
 * Browser-facing request checks for the local API server when it runs without an API key.
 *
 * - [isAllowedHost] blocks DNS rebinding: a hostile site can point its own hostname at
 *   127.0.0.1 or this phone's LAN address, but the browser then sends that hostname in `Host`.
 *   Only loopback names and IP literals are accepted.
 * - [isSameOrigin] blocks cross-site reads: a page served by this server sends its own origin
 *   (or none, for top-level navigation and non-browser clients); any other origin is refused.
 */
object BrowserRequestGuard {
    private val ipv4 = Regex("""^\d{1,3}(\.\d{1,3}){3}$""")

    fun isAllowedHost(hostHeader: String?): Boolean {
        // HTTP/1.0 and some non-browser clients omit Host entirely; browsers always send it.
        val host = hostHeader?.trim()?.lowercase() ?: return true
        if (host.isEmpty()) return true
        val name = stripPort(host)
        return name == "localhost" || name.endsWith(".localhost") || ipv4.matches(name) || name.startsWith("[")
    }

    fun isSameOrigin(originHeader: String?, hostHeader: String?): Boolean {
        val origin = originHeader?.trim()?.lowercase() ?: return true
        // Sandboxed iframes and file:// pages send the literal "null" origin, which is refused below.
        if (origin.isEmpty()) return true
        val host = hostHeader?.trim()?.lowercase() ?: return false
        return origin == "http://$host"
    }

    private fun stripPort(host: String): String =
        if (host.startsWith("[")) host.substringBefore("]") + "]"
        else host.substringBefore(":")
}
