# Blog và studio quản trị

Identity owns article and upload metadata; no cross-database access. Public readers do not need an account. Admin endpoints require the existing ADMIN bearer session.

- `GET /api/v1/identity/blog`: published summaries (up to 200), newest published first. Fields: `id`, `slug`, `title`, `excerpt`, `category`, `coverUrl`, `authorName`, `status`, `version`, `publishedAt`, `updatedAt`.
- `GET /api/v1/identity/blog/{slug}`: published article summary plus `document`. Draft/archived/missing returns 404.
- `GET /api/v1/identity/admin/blog`: admin summaries including DRAFT/PUBLISHED/ARCHIVED (up to 200).
- `GET /api/v1/identity/admin/blog/{id}`: complete editable article.
- `POST /api/v1/identity/admin/blog`: create, returns 201 with saved article.
- `PUT /api/v1/identity/admin/blog/{id}`: save/publish/hide; requires last fetched `version`, otherwise 409 for stale data. No automatic overwrite.

Write body: `slug` (unique lowercase letters/digits/hyphens, max 180), `title` (max 240), `excerpt` (max 800), `category` (`CLEANING_TIPS` or `FAMILY_KNOWLEDGE`), `coverUrl` (HTTPS), `authorName` (max 160; blank defaults to authenticated admin), `document`, `status` (`DRAFT`, `PUBLISHED`, `ARCHIVED`), and `version` for update. Published articles require cover, summary and nonempty text. Hiding preserves records/history and removes public access. First publication time is retained across subsequent saves.

`document` is structured Tiptap-compatible JSON, not arbitrary HTML. Allowed nodes: doc, paragraph, heading (1–3), text, bulletList, orderedList, listItem, blockquote, hardBreak, horizontalRule, codeBlock, image, video, youtube. Allowed marks: bold, italic, underline, strike, code, link, textStyle. Fonts, sizes, colors, spacing and alignment are validated; only HTTPS media/link URLs (or root-relative internal links) are accepted. YouTube hosts are restricted. Max serialized length 250,000, 5,000 nodes, depth 24. Web renders through an allowlisted React renderer, never raw HTML.

Media:

- `GET /admin/blog/media/capabilities`: enabled flag and limits.
- `POST /admin/blog/media/signature` with `{type: "image" | "video"}`: persisted upload reservation and signed Cloudinary upload ticket, valid 10 minutes.
- Upload browser FormData directly to returned Cloudinary `uploadUrl` with ticket params, signature, api_key and file.
- `POST /admin/blog/media/{id}/complete`: admin must own reservation. Backend inspects Cloudinary asset and validates public ID/type/HTTPS Cloudinary URL/format/size/duration before marking READY and returning `{id,url,type}`. API secret never goes to frontend.
- Images JPG/PNG/WebP ≤10 MB; videos MP4/WebM/MOV ≤50 MB and ≤120 seconds. Unconfigured/unavailable provider reports an actual error; no placeholder success. Admin can alternatively insert HTTPS media URLs or YouTube video URLs without upload.

V19 creates blog_articles/blog_media. V20 persists full original editorial content for the four previously displayed static articles, preserving their slugs and publication dates. Web routes `/blog`, `/blog/:slug`, `/admin/blog`; admin Studio is lazy-loaded. No existing mobile consumer was changed.
