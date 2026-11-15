// Cloudflare Worker: Reddit -> Gemini summarizer relay.
// Secrets: GEMINI_API_KEY (wrangler secret put GEMINI_API_KEY).
const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type",
};
const UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/126 Mobile Safari/537.36";

export default {
  async fetch(req, env) {
    if (req.method === "OPTIONS") return new Response(null, { headers: CORS });
    const url = new URL(req.url);
    if (url.pathname !== "/summarize") {
      return new Response("Reddit Summarizer relay is running.", { headers: CORS });
    }
    try {
      const { url: postUrl } = await req.json();
      const idMatch = String(postUrl || "").match(/\/comments\/([a-z0-9]+)/i);
      if (!idMatch) return json({ error: "Không tìm thấy ID bài viết Reddit." }, 400);
      const id = idMatch[1];

      // 1. Fetch post + comments JSON from Reddit
      let j = null;
      for (const u of [
        `https://www.reddit.com/comments/${id}.json?raw_json=1&limit=10`,
        `https://old.reddit.com/comments/${id}.json?raw_json=1&limit=10`,
      ]) {
        const r = await fetch(u, { headers: { "User-Agent": UA } });
        if (!r.ok) continue;
        try {
          const parsed = await r.json();
          if (parsed && parsed[0] && parsed[0].data) { j = parsed; break; }
        } catch (e) {}
      }
      if (!j) return json({ error: "Reddit chặn truy cập JSON từ server. Thử lại sau." }, 502);

      const child = j[0].data.children[0].data;
      const post = {
        title: child.title, subreddit: child.subreddit,
        selftext: child.selftext || "", url: child.url || "",
        score: child.score, num_comments: child.num_comments,
      };
      const comments = (j[1]?.data?.children || [])
        .map(c => c.data && c.data.body).filter(Boolean)
        .slice(0, 8).map(b => b.slice(0, 600));

      // 2. Summarize with Gemini (post content + comment highlights)
      const bodyText = (post.selftext || `[bài link tới ${post.url}]`).slice(0, 9000);
      const commentsText = comments.join("\n---\n").slice(0, 4000);
      const prompt =
`Bạn là trợ lý tóm tắt bài viết Reddit bằng TIẾNG VIỆT.
Subreddit: r/${post.subreddit}
Tiêu đề: ${post.title}
Điểm: ${post.score}, số bình luận: ${post.num_comments}

NỘI DUNG BÀI VIẾT:
${bodyText}

BÌNH LUẬN NỔI BẬT:
${commentsText || "(không có)"}`;

      const gRes = await fetch(
        `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${env.GEMINI_API_KEY}`,
        {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            contents: [{ parts: [{ text: prompt }] }],
            generationConfig: { temperature: 0.3, maxOutputTokens: 1500 },
          }),
        }
      );
      const gj = await gRes.json();
      const text = gj?.candidates?.[0]?.content?.parts?.map(p => p.text).join("\n") || "";
      if (!gRes.ok || !text) return json({ error: `Gemini lỗi: ${JSON.stringify(gj).slice(0, 300)}` }, 502);

      return json({ title: post.title, subreddit: post.subreddit, summary: text });
    } catch (e) {
      return json({ error: String(e && e.message || e) }, 500);
    }
  },
};

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { ...CORS, "Content-Type": "application/json" } });
}
