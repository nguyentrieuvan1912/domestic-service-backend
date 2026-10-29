# Booking reminders, push and LiveKit calls

Booking owns accepted assignment/time eligibility, reminder deduplication and call records (V10).
Identity retains the existing notification inbox and adds push devices/deliveries (V15).
No cross-database SQL; notification-service remains schema/health only in this iteration.

## Reminders

Booking scans every15seconds for future bookings in CONFIRMED/ACCEPTED/STAFF_ASSIGNED and assignments ACCEPTED.
At60minutes (>30remaining) and30minutes (<=30remaining), sends a persisted internal Identity event of type
SCHEDULE_UPDATE. Unique assignment_id/starts_at/minutes_before avoids duplicates. Retries use the same event key.
Late acceptance below30minutes only sends the30minute window. Cancellation/completion/unaccepted assignments skip.
Data: bookingId,assignmentId,startsAt,reminderMinutes,expiresAt (ISO UTC). One-hour expiry is start-30minutes;
half-hour expiry is start. Notification delivery timing remains subject to provider/device/network availability.

## Push

POST `/api/v1/identity/push/devices` bearer auth `{token:ExpoPushToken[...],platform:android|ios}` stores
the real device token and current session hash. Token registration is idempotent; tokens move to the currently
authenticated account on login. Revoked/expired sessions, disabled devices and blocked accounts cannot deliver.
Returns `{registered:true,pushEnabled:boolean}` only after persistence. EXPO_PUSH_ENABLED defaultsfalse.

Only recent, unexpired SCHEDULE_UPDATE/CALL_INCOMING events with expiresAt enter the push queue.
Before provider send Identity POSTs `/api/v1/booking/internal/push-validity` using X-Service-Key and payload+userId.
Booking verifies current assignment/start or ringing call/recipient. Invalid work expires, outage retries validation.
Expo send tickets and receipts are persisted separately; ACCEPTED means provider acceptance, not user receipt/read.
HTTP429/5xx retry bounded; ambiguous network send becomes UNKNOWN and is not blindly replayed.
DeviceNotRegistered disables token. Private Firebase service account is configured in EAS, never sent to frontend/backend.

## Calls

All routes below under `/api/v1/booking`, require current CUSTOMER/STAFF bearer auth.

- GET `/calls/capabilities` → `{enabled}` (configuration present, not a provider connectivity test).
- POST `/conversations/{id}/calls` `{requestKey}` → call view. Same caller/key returns original record;
  key reused with another conversation409. Calls require ACTIVE conversation, accepted staff assignment and
  live booking CONFIRMED/ACCEPTED/STAFF_ASSIGNED/IN_PROGRESS. One active call per conversation and participant.
- GET `/calls/incoming` → at most one unexpired ringing call for this recipient.
- GET `/calls/{id}` → `{id,conversationId,bookingId,callerId,calleeId,status}` scoped to the two participants.
- PATCH `/calls/{id}` `{action:ACCEPT|DECLINE|END}`. Only callee may accept/decline; either may end.
- POST `/calls/{id}/token` → `{serverUrl,token}`. Room/user identity come from server records, never client input.
  Caller can join while ringing; callee must explicitly accept. Short-lived HS256 grant only roomJoin/publish/subscribe.

Provider CreateRoom succeeds before a new call is persisted; two-participant room, no recording by default.
CALL_INCOMING goes via existing Booking outbox → Identity inbox/push with60second expiry.
No answer after60seconds → MISSED; invalid booking/assignment or maximum2hours → ENDED.
Maintenance closes remote rooms and revokes both participant tokens via LiveKit Cloud; failed cleanup retries.
Statuses RINGING/ACCEPTED describe invitation/signaling, never prove media connectivity. SDK connection and remote
participant presence determine connected UI. Secret/admin grants remain server-side.

## Native/setup requirements

Mobile uses LiveKit React Native/WebRTC and expo-notifications in a development build; Expo Go stays usable for
existing booking/chat and shows a truthful unsupported message for calling. Calls start audio-only; camera opt-in.
Native Android package com.domesticservice.cleanmaster. Private google-services.json ignored by Git.
Server env LIVEKIT_URL(wss),LIVEKIT_API_KEY,LIVEKIT_API_SECRET (Booking only), EXPO_PUSH_ENABLED and optional
EXPO_PUSH_ACCESS_TOKEN (Identity only). Keep INTERNAL_SERVICE_TOKEN shared for current authenticated event flow.
No real push/audio success is claimed before project credentials, native permissions and two-device test are complete.
