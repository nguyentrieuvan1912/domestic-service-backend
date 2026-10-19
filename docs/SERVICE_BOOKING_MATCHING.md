# Service questionnaires and staff job search

The customer Mode B confirmation button calls booking creation and opens its waiting screen only after the server returns the saved order. The booking enters `MATCHING` after its real catalog quote is consumed. It remains open until the required number of eligible staff accept. The waiting screen shows the saved booking code and accepted/required staff counts, links to the persisted booking, and lets the customer leave while the job stays open. Confirmation and staff cards derive from saved status and accepted assignments; there is no preview acceptance button. Prices use the quote response, including staff and package quantity. A customer selecting a particular staff member continues to use the existing Mode A invitation flow.

## Service-specific booking information

Catalog migration V7 adds persisted questionnaires for the current service categories: hourly/deep cleaning, upholstery, AC cleaning/maintenance, washing machines, dryers, refrigerators, childcare, elderly care, pets, cooking, laundry, grocery shopping, plants, and disinfection. Repeated catalog offerings in the same category share their category's questions.

`GET /api/v1/catalog/services/{id}` returns these definitions in `requirements`. Mobile renders the same definitions on service details and inside either booking mode. The public web service detail displays the preparation requirements and retains its app download flow; admin booking details already read persisted answers and labels.

Quote preview, quote creation and therefore booking creation require valid `answers`. Fields from another service are rejected. Numeric bounds and integer counts are checked on the server; booleans must be actual JSON booleans, and select/multi-select values must belong to the service. Answers and their labels are copied into the quote and booking snapshot, so later catalog edits do not alter old bookings.

For `priceUnit=ITEM`, `quantity` multiplies the entire selected package price and duration. A package that already contains multiple appliances remains a whole package; quantity is the number of those packages. Total duration is rounded to 30 minutes and limited to 12 hours by the existing pricing policy. Add-ons retain their configured real prices. Grocery purchase budget describes the customer's requested shopping budget and does not become the service fee. A recurring service selection still creates one dated booking; this change does not automatically generate future bookings.

## Staff matching and acceptance

`GET /api/v1/booking/staff/me/matching` returns eligible Mode B jobs only. It checks active/online profile, approved capabilities for both primary and related services, restrictions, registered schedule week, matching province/ward, full availability coverage, existing bookings and the one-hour buffer, start time, and remaining positions. Mode A invitations retain their per-staff visibility and eligibility reasons.

Mobile opens the homepage job feed in Mode B and lets staff further filter by service, local calendar date, area, and text, with sorting. Filter choices are derived from real jobs. The feed refreshes every five seconds while focused and after a failed claim.

`POST /api/v1/booking/staff/opportunities/{id}/claim` authenticates the staff member, locks their schedule row, locks the booking row, and rechecks conditions inside one transaction. Assignment, reservation, conversation, booking status/history and outbox event commit together. A repeated claim by the same staff member returns the same assignment. A competing claimant for the final filled position receives HTTP 409. Multi-staff packages accept at most the configured number of different staff.

Booking migration V9 adds a database trigger that serializes new `ACCEPTED` assignments on the booking row and rejects capacity overflow, including writes outside the workflow. The existing active staff uniqueness index and reservation exclusion constraint remain in force.

## Verification

Run the regular Maven verification from the backend root. Against the migrated local PostgreSQL databases, enable `JPA_POSTGRES_VERIFY=true` for the six `BookingClaimPostgresTests`: one-slot race, multi-slot race, overlapping jobs, idempotent retries, server filtering, and concurrent direct database writes. The tests isolate only the identity HTTP peer and clean up their own persisted fixtures; the real PostgreSQL transaction/locking and workflow code are exercised.

Catalog `ServiceQuestionnaireValidatorTest` covers required fields, field ownership, numeric rules, choices, booleans, and input length. Mobile `npm run test:service-booking` covers client questionnaire validation and matching filters. Also run the mobile TypeScript check and web production build.
