# Reddit Summarizer (Android) + Gemini Relay

App Android nhỏ: **Share** link bài viết Reddit từ app bất kỳ (hoặc dán link) → app gọi **Cloudflare Worker relay** → relay lấy JSON đầy đủ nội dung + bình luận nổi bật từ Reddit → gửi **Gemini 2.5 Flash** → trả về **tóm tắt tiếng Việt**.

## Kiến trúc

```
[App Android] --POST {url}--> [Cloudflare Worker] --GET--> reddit.com/comments/<id>.json
                                     |
                                     +--POST--> Gemini API (key nằm ở Worker secret, KHÔNG nằm trong APK)
```

- `app/` — source Android (Kotlin, không cần Compose, build bằng Gradle thường).
  - `MainActivity.kt` — UI: ô nhập link, nút Tóm tắt, nút Cài đặt (đổi URL relay).
  - `RedditActivity.kt` — nhận Share intent (`ACTION_SEND`) từ app Reddit rồi chuyển về MainActivity.
  - `RedditUrl.kt` — trích URL chuẩn `.../comments/<id>/...` từ mọi dạng link Reddit.
  - `Relay.kt` — POST tới Worker.
- `worker/` — Cloudflare Worker giữ key Gemini và fetch Reddit JSON (máy VN bị Reddit chặn nên phải có relay).

## Build APK

Yêu cầu: JDK 17, Android SDK (platform 36 + build-tools).

```bash
export JAVA_HOME=/path/to/jdk-17
gradle assembleDebug --no-daemon
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Cài lên điện thoại: copy APK sang máy → bật "Cài app không rõ nguồn gốc" → mở file.

## Triển khai Worker (bắt buộc để app chạy)

```bash
cd worker
npm install          # cài wrangler
npx wrangler login   # đăng nhập tài khoản Cloudflare (miễn phí)
npx wrangler secret put GEMINI_API_KEY   # dán key Gemini từ https://aistudio.google.com/apikey
npx wrangler deploy
```

Sau khi deploy, wrangler in ra URL dạng `https://reddit-summarizer-relay.<subdomain>.workers.dev`.
Mở app → nút **Cài đặt** → dán `<URL>/summarize` → Lưu. (URL mặc định trong app đã trỏ sẵn `reddit-summarizer-relay.duonghoang314.workers.dev/summarize`.)

## Cách dùng

1. Trong app Reddit (hoặc trình duyệt), bấm **Chia sẻ** → chọn **Reddit Summarizer**.
2. App hiển thị tiêu đề + tóm tắt tiếng Việt gồm nội dung bài viết và ý chính các bình luận nổi bật.

Hoặc mở app → dán link → **Tóm tắt**.

## Ghi chú bảo mật

- Key Gemini KHÔNG bao giờ nằm trong APK — chỉ là secret của Worker.
- Worker hiện chấp nhận mọi origin (CORS `*`) cho tiện; nếu muốn giới hạn, siết lại trong `worker/src/index.js`.
