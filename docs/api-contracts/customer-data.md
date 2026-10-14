# Customer data — initial frontend integration

All routes go through Gateway 8080. Private routes require `Authorization: Bearer <Identity session>` and use the authenticated user ID, ignoring any client-supplied ownership field. Account/session authentication remains the existing Identity contract. Errors use ProblemDetail; all entity IDs serialize as JSON numbers (Long/BIGINT); frontend adapters convert them to strings for the current view models.

## Identity

| Method | Path under /api/v1/identity | Result |
|---|---|---|
| GET | /customer/addresses | Customer's address array |
| GET | /customer/addresses/{id} | Owned address, otherwise 404 |
| POST | /customer/addresses | 201, committed address |
| PUT | /customer/addresses/{id} | Complete replacement of editable fields, owned address |
| PATCH | /customer/addresses/{id}/default | Committed default address |
| DELETE | /customer/addresses/{id} | 204 after deletion |
| GET | /notifications | Notifications owned by the session user, newest first |
| GET | /notifications/{id} | Owned notification, otherwise 404 |
| PATCH | /notifications/{id}/read | `{ "changed": 0 or 1 }`; idempotent |
| PATCH | /notifications/read-all | `{ "changed": number }`, current user only |
| POST | /support/requests | Public contact intake; 201 `{ "id": number, "status": "RECEIVED" }` after persistence |

Address body: `title`, `recipientName`, `recipientPhone`, `streetAddress`, `district`, `city`, optional `ward`, `note`, and boolean `isDefault`. Phone uses the existing Vietnamese phone validation. Responses also contain `id`, `customerId`, `fullAddress`, and nullable coordinates. No fabricated ward or coordinates are assigned when a form has not collected them. Existing coordinates remain on edit. The first address is default. Selecting another default and deleting a default are serialized by locking the customer row inside the transaction; deletion picks the first remaining address if necessary.

Notifications return `id`, `userId`, `targetRole`, `type`, `title`, `content`, nullable `referenceId`, `data` from the JSON payload, `isRead`, and `createdAt`. This phase persists read state only; it does not fabricate booking/payment notifications. Producers in other services need an agreed integration contract. The mobile customer notification screen reads the real payload; linked Booking/Finance navigation remains unavailable until those screens use real APIs. Staff frontend notifications retain their previous repository in this phase.

Contact body: `name`, `email`, `subject`, `message`. Migration Identity V7 adds `support_requests`; receipt means the request is stored, not that an email response has been sent.

## Catalog

`GET /api/v1/catalog/promotions` is public and returns promotions whose database status is ACTIVE, end date is in the future, and usage is not exhausted. Future promotions have `isActive: false`. Response fields: `id` (number), `code`, `name`, `description`, `discountType`, `discountValue`, nullable `maxDiscountAmount`, `minimumBookingAmount`, `startDate`, `endDate`, nullable `usageLimit`, `usedCount`, `isActive`, and numeric `serviceIds`.

This endpoint is for display. It does not reserve usage or confirm discounts; the future price quote must revalidate eligibility server-side. Web add-on tabs use the existing Catalog service detail/packages for CLEANING_UPHOLSTERY; absent package prices stay empty instead of using mock prices.
