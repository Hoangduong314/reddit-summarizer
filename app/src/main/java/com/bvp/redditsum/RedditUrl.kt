package com.bvp.redditsum

import java.net.URI

object RedditUrl {
    private val postRegex = Regex(
        """^https?://(?:(?:www|old|new|m|np)\.)?reddit\.com(/r/[^/]+/comments/([a-z0-9]+))""",
        RegexOption.IGNORE_CASE
    )

    /** Find the first reddit.com post URL in arbitrary shared text; normalize to canonical form. */
    fun extractPostUrl(raw: String): String? {
        // Reddit share text often looks like: "comment_title https://www.reddit.com/r/x/comments/abc/slug/?share=1"
        for (token in raw.split(Regex("\\s+"))) {
            val cleaned = token.trim('<', '>', '(', ')', ',', ';')
            if (!cleaned.startsWith("http")) continue
            try {
                val m = postRegex.find(cleaned) ?: continue
                val id = m.groupValues[2]
                val path = m.groupValues[1]  // /r/sub/comments/id
                return "https://www.reddit.com$path/"
            } catch (_: Exception) { }
        }
        return null
    }

    @Suppress("unused")
    private fun hostOf(u: String): String? = try { URI(u).host } catch (_: Exception) { null }
}
