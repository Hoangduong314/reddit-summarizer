package com.bvp.redditsum

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SHARED_TEXT = "shared_text"
        private const val PREFS = "reddit_sum"
        private const val KEY_RELAY = "relay_url"
        // Public relay (Cloudflare Worker holding the Gemini key). Override in Settings.
        private const val DEFAULT_RELAY = "https://reddit-summarizer-relay.duonghoang314.workers.dev/summarize"
    }

    private lateinit var input: EditText
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView
    private lateinit var summarizeBtn: Button
    private lateinit var settingsBtn: Button
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val pad = dp(12)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        input = EditText(this).apply {
            hint = "Dán link Reddit (hoặc Share từ app Reddit → Reddit Summarizer)"
            isSingleLine = false
            maxLines = 3
            setText(intent?.getStringExtra(EXTRA_SHARED_TEXT) ?: "")
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { summarize(); true } else false
        }
        root.addView(input, lp())

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        summarizeBtn = Button(this).apply { text = "Tóm tắt"; setOnClickListener { summarize() } }
        settingsBtn = Button(this).apply { text = "Cài đặt"; setOnClickListener { showSettings() } }
        row.addView(summarizeBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(settingsBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        statusText = TextView(this).apply { textSize = 13f; setTextColor(0xFF666666.toInt()) }
        root.addView(statusText, lp())

        val scroll = ScrollView(this)
        resultText = TextView(this).apply {
            textSize = 15f
            setTextIsSelectable(true)
            gravity = Gravity.TOP
        }
        scroll.addView(resultText)
        root.addView(scroll, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))

        setContentView(root)

        if (intent?.getStringExtra(EXTRA_SHARED_TEXT) != null) summarize()
    }

    private fun lp() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun relayUrl(): String =
        prefs.getString(KEY_RELAY, DEFAULT_RELAY) ?: DEFAULT_RELAY

    private fun showSettings() {
        val pad = dp(16)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        layout.addView(TextView(this).apply {
            text = "URL relay (Worker chứa Gemini key).\n" +
                "Mặc định dùng relay công khai; nhập URL worker riêng nếu bạn tự deploy."
        })
        val edit = EditText(this).apply {
            hint = "https://...workers.dev/summarize"
            setText(prefs.getString(KEY_RELAY, ""))
        }
        layout.addView(edit)

        AlertDialog.Builder(this)
            .setTitle("Cài đặt")
            .setView(layout)
            .setPositiveButton("Lưu") { _, _ ->
                prefs.edit().putString(KEY_RELAY, edit.text.toString().trim()).apply()
                statusText.text = "Đã lưu cài đặt."
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun summarize() {
        val raw = input.text.toString().trim()
        val url = RedditUrl.extractPostUrl(raw)
        if (url == null) {
            Toast.makeText(this, "Không tìm thấy link bài viết Reddit hợp lệ.", Toast.LENGTH_LONG).show()
            return
        }
        summarizeBtn.isEnabled = false
        resultText.text = ""
        statusText.text = "Đang tóm tắt qua relay + Gemini..."
        lifecycleScope.launch {
            try {
                val out = Relay.summarize(relayUrl(), url)
                resultText.text = buildString {
                    append("r/").append(out.optString("subreddit", "?"))
                    append(" • ").append(out.optString("title", "")).append("\n\n")
                    append(out.getString("summary"))
                }
                statusText.text = "Xong."
            } catch (e: Exception) {
                statusText.text = "Lỗi."
                resultText.text = e.message ?: e.toString()
            } finally {
                summarizeBtn.isEnabled = true
            }
        }
    }
}
