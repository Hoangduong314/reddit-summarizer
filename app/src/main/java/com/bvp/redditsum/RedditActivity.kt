package com.bvp.redditsum

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle

/** Trampoline activity: receives ACTION_SEND (shared Reddit link), forwards it to MainActivity. */
class RedditActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shared = extractSharedText()
        val viewUri = intent?.data?.toString()
        val target = shared ?: viewUri
        val i = Intent(this, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (target != null) putExtra(MainActivity.EXTRA_SHARED_TEXT, target)
        }
        startActivity(i)
        finish()
    }

    private fun extractSharedText(): String? {
        val i = intent ?: return null
        if (i.action != Intent.ACTION_SEND) return null
        val text = i.getStringExtra(Intent.EXTRA_TEXT)
        val subject = i.getStringExtra(Intent.EXTRA_SUBJECT)
        return listOfNotNull(text, subject).firstOrNull { it.isNotBlank() }
    }
}
