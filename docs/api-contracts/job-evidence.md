# Job photo evidence / completion acceptance

Booking owns the evidence records, image bytes and related conversation messages. Finance and Identity databases are not queried directly. This feature uses real database storage and does not require a new media provider or public image URLs.

## Authenticated routes

All paths are under `/api/v1/booking/bookings/{bookingId}/evidence` and require the existing bearer session.

| Method | Path | Purpose |
|---|---|---|
| GET | base path | List metadata plus JPEG thumbnail data URLs |
| POST | base path | Assigned STAFF uploads `{stage:"BEFORE"\|"AFTER",requestKey,imageBase64}` |
| GET | `/{id}/original` | `{dataUrl,sha256}` of the stored original, private/no-store |
| GET | `/{id}/image` | Original image bytes, private/no-store |

IDs are numeric. List fields: `id,bookingId,assignmentId,staffId,stage,mimeType,sha256,width,height,messageId,createdAt,thumbnailUrl`. Staff sees their own evidence, the owning customer and admin can see all evidence for that booking. Other accounts cannot list or fetch original images.

Only JPEG/PNG images up to 2 MiB and 16 million pixels are accepted; image bytes are decoded and validated server-side. The base64 field contains raw encoded file bytes, without a data URL prefix. Request keys are 1–120 alphanumeric/underscore/hyphen characters. Each staff assignment allows at most five BEFORE and five AFTER images, leaving capacity for both stages. Identical image bytes cannot be submitted twice by the same staff for the same booking. Reusing a request key with different data or stage returns 409; retrying an identical request returns the original saved result, including after completion.

## Lifecycle and transaction guarantees

The staff arrival action reminds them to capture the scene before and after work. BEFORE uploads are accepted in EN_ROUTE or IN_PROGRESS; AFTER requires IN_PROGRESS. The staff must preview and submit images; selecting a local file alone does not count as saved evidence.

An upload locks staff, booking, assignment and its conversation in the established workflow order. Image bytes, immutable evidence metadata, the IMAGE chat message and outbox notification commit together. The server resolves the conversation from booking + assigned staff; the client cannot choose another conversation. The response returns `saved:true,id,messageId,conversationId` only after the transaction succeeds. Image messages include `evidenceId,evidenceBookingId,evidenceStage`; existing text message fields and customer deletion cutoffs are unchanged.

Completion requires at least one BEFORE and one AFTER image belonging to the exact staff assignment. This is checked in the progress service and a PostgreSQL trigger. Each worker in a multi-staff booking needs their own evidence; another worker's photos do not satisfy the requirement. Existing already-completed historical assignments are not retroactively changed. Uploading new evidence to an ended booking is rejected, but original evidence remains readable by its authorized participants/admin.

Evidence rows reject UPDATE and DELETE. Originals are retained in `booking_job_evidence`; deleting a customer's chat history only advances that customer's message visibility cutoff and does not remove the evidence or admin audit. Every saved photo adds a timestamped booking history entry referencing its evidence ID, actor and stage. Metadata includes SHA-256 of uploaded bytes; the system records upload time, not a verified camera capture time. Photo content still needs human review when resolving disputes.

## UI / verification

Mobile staff job detail has separate BEFORE/AFTER capture, library, preview and submit controls. Chat displays thumbnails and can open authorized originals, including after service completion. Admin booking detail has a grouped evidence gallery showing staff ID, upload time, original image, assignment/message references and checksum for investigation.

Migration: Booking V13. `JobEvidencePostgresTests` uses disposable PostgreSQL with `EVIDENCE_POSTGRES_VERIFY=true` and `EVIDENCE_TEST_URL` (test-only postgres password in annotation). It verifies actual HTTP upload/read, ownership, image validation, atomic chat creation, completion gating, retry/concurrency deduplication, immutable retention, multi-worker isolation and chat deletion survival. Runtime services never use test fixtures.
