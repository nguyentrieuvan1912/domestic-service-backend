# Community media, threaded comments and reactions

Identity owns all data. V13 extends posts/comments and adds community_media and community_comment_reactions.
Existing moderation policy remains: user posts PENDING, admin posts PUBLISHED. Guests see PUBLISHED only;
owners can see their own pending/hidden posts; admin can see all. Writes require a current active session.

## Post reads and creation

GET `/api/v1/identity/community/posts` (latest100) and GET `/community/posts/{id}` include
media `[{id,uri,type,width,height}]`, commentCount, likes (number of all reactions), myReaction,
reactions `{LIKE: n, ...}`, content/status/author/avatar/createdAt.
Feed `comments` is now an empty compatibility array; load the paginated thread instead of embedding all comments.
GET `/admin/community/posts` requires ADMIN and includes moderationNote.

POST `/community/posts`: `{content,mediaIds:[Long],requestKey}`. Max5000 characters, max4 unique media IDs;
at least content or media required. Only READY verified media owned by this session can be attached, once.
Same user/key/body returns original result; different body with same key409. Raw frontend media URLs are rejected.
PATCH `/admin/community/posts/{id}` retains `{status,note}` moderation.

## Cloudinary

GET `/community/media/capabilities`: enabled (credentials configured, not a provider permission test), maxFiles4,
maxImageBytes10MiB, maxVideoBytes50MiB, maxVideoSeconds120.
POST `/community/media/signature` auth body `{type:"image"|"video"}` creates owned upload ticket and returns
id, uploadUrl, apiKey, signature, params. Client posts multipart directly to the fixed Cloudinary endpoint.
Only API key is public; API secret remains in backend. Sign SHA256 sorted params +secret, overwrite=false,
server-generated unique public_id and restricted allowed_formats. Max20 ticket reservations per user/hour.
POST `/community/media/{id}/complete` auth reads exact owned asset from authenticated Cloudinary Admin API,
checks public_id/type/format/bytes/dimensions/duration/delivery host and records READY; frontend response/URL is not trusted.
Unused tickets expire for confirmation after15minutes. Completed READY assets remain owned until attached.
Image formats JPG/JPEG/PNG/WebP; videos MP4/WebM/MOV. Oversized/invalid media400; missing config/provider unavailable503.
Cloudinary upload403 should be shown as a provider Upload/Create permission issue, preserving the draft.

Identity-only environment variables: CLOUDINARY_CLOUD_NAME, CLOUDINARY_API_KEY, CLOUDINARY_API_SECRET in ignored .env.
Key must allow asset creation/upload and resource metadata read in the selected product environment.
No unsigned upload preset or public webhook needed. Existing bank/finance configuration is independent.

## Threads

GET `/community/posts/{id}/comments`: latest50 in ascending display order, hasMore, nextBefore.
`?before=Long` older50; `?after=Long` next50 new comments (ascending, hasMore allows catchup). Do not use both.
Items: id,userId,author,avatar,content,media,deleted,createdAt,parentId,parentAuthor,parentContent,myReaction,reactions.
V14 adds `deletedIds` to every comment page, including empty `after` responses. Clients scrub cached comment text,
media, reactions and parent quotes for these IDs so moderation also applies to previously loaded comments.
Deleted comments are tombstones; commentCount counts nondeleted comments. Their original evidence is admin-report-only.
POST same route: `{content,parentId:null|Long,mediaIds:[Long],requestKey}` max2000 chars, max4 attachments;
content or media is required. Same verified, owned, attach-once Cloudinary rules as posts; attachments cannot be reused
between a post and a comment. Reply parent must be nondeleted and belong to this post;
database composite FK enforces this. Retries are idempotent by user/requestKey, changed payload409.
POST returns the saved full comment for immediate display; merging this acknowledgement must not advance the incremental polling cursor past other unseen comments.
Replies display in one chat timeline with quoted parent, preserving arbitrary reply chains without deeply nested indentation.

## Reactions

PATCH `/community/posts/{id}/reaction` with `{reaction:null|LIKE|LOVE|HAHA|WOW|SAD|SUPPORT}`.
One reaction per user/post, replacing a reaction never increments total users. Null removes.
Returns updated post with verified counts/myReaction. Legacy PATCH `/like` `{liked:boolean}` maps to LIKE/null.
PATCH `/community/posts/{id}/comments/{commentId}/reaction` same body; returns updated comment and counts.
One reaction per user/comment. Parent post visibility and membership checked on every write.

Mobile thread polls new comments while foreground every5s, feed every15s; no background writes or automatic
mutation retry. Draft/request keys and uploaded verified IDs survive explicit retry in the open composer.
Web admin can review actual media, moderate, react and participate in each post thread.

## Comment reports (V14)

POST `/community/posts/{postId}/comments/{commentId}/reports` requires an active session and a visible post,
nondeleted comment in that post. Body `{reason,detail}`: reason SPAM / MISINFORMATION / ABUSE / OTHER,
detail max2000 chars and required for OTHER. One report per reporter/comment; repeated submission returns the saved report.
Returns `{id,status}` only after persistence. Reporting never automatically removes content or blocks the author.

ADMIN GET `/admin/community/reports?status=OPEN|RESOLVED|DISMISSED|ALL&before=Long` returns latest50 reports,
`{items,hasMore,nextBefore}`. Includes original comment/media, author ID/role/accountStatus, reporter/reason/detail,
post and parent reply context, deletion flag, resolution/note/reviewer/time. Evidence stays available after moderation.

ADMIN PATCH `/admin/community/reports/{id}` body `{action,note}` with DISMISS / DELETE / BLOCK / DELETE_AND_BLOCK.
BLOCK actions require a reason and only target CUSTOMER/STAFF, never ADMIN. Account status becomes BLOCKED,
all sessions are revoked in the same transaction, and concurrent comment writers recheck status under the account lock.
DELETE creates a tombstone, preserves replies/evidence, resolves all open reports for that comment and removes
content/media/reactions from public thread responses. BLOCK alone leaves the comment visible; the combined action does both.
DISMISS affects only the selected report. Repeating the same completed action returns its result; conflicting action409.
The report action and account change are Identity-only; existing account administration handles later unblocking.
