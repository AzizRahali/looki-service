# Loomea Service

Spring Boot REST API backend for the Loomea salon booking platform. Handles authentication, salon management, booking lifecycle, staff team management with QR-based onboarding, owner dashboard, and real payments via Konnect.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Framework | Spring Boot 4.0.4 |
| Language | Java 17 |
| Security | Spring Security + JWT (JJWT 0.12.6) |
| Persistence | Spring Data JPA / Hibernate |
| Database (dev) | H2 (in-memory) |
| Database (prod) | PostgreSQL |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Maven |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL (production only)

### Run in Development (H2)

```bash
mvn spring-boot:run
```

The server starts on **http://localhost:8080** with an in-memory H2 database. Sample data is seeded automatically via `DataSeeder`.

### Run with Docker / PostgreSQL

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

Set the following environment variables:

```env
DB_HOST=localhost
DB_PORT=5432
DB_NAME=loomea
DB_USERNAME=postgres
DB_PASSWORD=secret
JWT_SECRET=your-256-bit-secret-key-at-least-32-chars-long
```

### Swagger UI

```
http://localhost:8080/swagger-ui.html
```

### H2 Console (dev only)

```
http://localhost:8080/h2-console
JDBC URL: jdbc:h2:mem:loomea
```

---

## Project Structure

```
src/main/java/com/loomea/
├── auth/                   # Authentication & user management
│   ├── controller/
│   ├── dto/                # incl. StaffRegisterRequest
│   ├── entity/             # User, Role (CLIENT | SALON_OWNER | STAFF)
│   ├── repository/
│   └── service/            # incl. registerStaff()
├── booking/                # Booking lifecycle & time slots
│   ├── controller/
│   ├── dto/
│   ├── entity/
│   ├── repository/
│   └── service/
├── owner/                  # Owner dashboard, salon, booking, staff management
│   ├── controller/         # incl. POST …/staff/{staffId}/invite
│   ├── dto/                # incl. StaffMemberDto, StaffInviteResponse
│   ├── entity/             # StaffMember (incl. inviteToken, linkedUserId)
│   ├── repository/         # incl. findByInviteToken, findByLinkedUserId
│   └── service/            # incl. generateInvite()
├── staff/                  # Staff self-service endpoints
│   ├── controller/         # GET /api/staff/me, /me/today, /me/bookings/*
│   ├── dto/                # StaffProfileDto
│   └── service/            # StaffService — filters bookings by staffMember FK
├── salon/                  # Public salon browsing & reviews
│   ├── controller/
│   ├── dto/
│   ├── entity/
│   ├── repository/
│   └── service/
├── payment/                # Konnect payment gateway integration
│   ├── config/             # KonnectConfig (binds env vars, creates RestTemplate bean)
│   ├── controller/         # PaymentController (initiate, webhook, status)
│   ├── dto/                # InitiatePaymentRequest/Response, PaymentStatusResponse, KonnectDTOs
│   ├── entity/             # PaymentRecord, PaymentType, PaymentRecordStatus
│   ├── repository/
│   └── service/            # PaymentService (orchestration), KonnectClient (HTTP calls)
├── search/                 # Salon search
│   ├── controller/
│   └── service/
└── common/                 # Shared: security, config, DTOs, exceptions
    ├── config/             # SecurityConfig, SubscriptionInterceptor, WebMvcConfig
    ├── controller/
    ├── dto/
    ├── exception/
    └── security/
```

---

## Authentication

All protected endpoints require a **Bearer JWT** token in the `Authorization` header:

```
Authorization: Bearer <access_token>
```

Tokens are issued on `/api/auth/login`, `/api/auth/verify-phone`, and `/api/auth/staff/register`. They expire after **24 hours**.

---

## Roles

| Role | Value | Access |
|---|---|---|
| Client | `CLIENT` | Booking, profile, saved salons |
| Salon Owner | `SALON_OWNER` | All owner endpoints + client features |
| Staff | `STAFF` | `/api/staff/**` — own schedule and profile only |

---

## API Reference

### Base URL

```
http://localhost:8080
```

---

### Health

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/health` | Public | Service health check |

---

### Auth

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/auth/register` | Public | Register a new CLIENT or SALON_OWNER |
| `POST` | `/api/auth/login` | Public | Login and receive JWT |
| `POST` | `/api/auth/request-otp` | Public | Send phone verification OTP |
| `POST` | `/api/auth/verify-phone` | Public | Verify OTP — returns JWT |
| `POST` | `/api/auth/staff/register` | Public | Register a staff account via invite token |
| `GET` | `/api/auth/me` | JWT | Get current user profile |
| `PUT` | `/api/auth/me` | JWT | Update current user profile |
| `POST` | `/api/auth/me/saved-salons/{salonId}` | JWT | Save a salon to favourites |
| `DELETE` | `/api/auth/me/saved-salons/{salonId}` | JWT | Remove a salon from favourites |

#### `POST /api/auth/register`
```json
{
  "fullName": "Jane Doe",
  "email": "jane@example.com",
  "password": "secret123",
  "phone": "+1234567890",
  "role": "CLIENT"
}
```

#### `POST /api/auth/staff/register`
Registers a staff account using a QR invite token. No OTP step is required — the token validates identity.
```json
{
  "inviteToken": "abc123...",
  "fullName": "Alex Smith",
  "email": "alex@example.com",
  "password": "secret123",
  "phone": "+1234567890"
}
```
Returns a JWT immediately on success. The invite token is consumed (single-use).

#### `POST /api/auth/login`
```json
{
  "email": "jane@example.com",
  "password": "secret123"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJ...",
    "user": {
      "id": "uuid",
      "fullName": "Jane Doe",
      "email": "jane@example.com",
      "role": "CLIENT",
      "savedSalonIds": []
    }
  }
}
```

---

### Salons (Public)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/salons` | Public | List all salons |
| `GET` | `/api/salons/{id}` | Public | Get salon by ID |
| `GET` | `/api/salons/by-name/{name}` | Public | Get salon by name (case-insensitive) |
| `GET` | `/api/salons/category/{category}` | Public | Get salons by service category |
| `GET` | `/api/salons/nearby` | Public | Get nearby salons |
| `GET` | `/api/salons/{salonId}/portfolio` | Public | Get salon portfolio posts |

#### `GET /api/salons/nearby`
Query params: `lat`, `lng`, `radius` (km, default 10)

```
GET /api/salons/nearby?lat=40.7128&lng=-74.0060&radius=5
```

---

### Search

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/search` | Public | Search salons by name, address or description |
| `GET` | `/api/search/category/{category}` | Public | Filter salons by category |

---

### Reviews

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/reviews/salon/{salonId}` | Public | Get all reviews for a salon |
| `POST` | `/api/reviews` | JWT | Submit a review |
| `DELETE` | `/api/reviews/{reviewId}` | JWT | Delete your review |

---

### Bookings (Client)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/bookings/slots` | Public | Get available time slots |
| `POST` | `/api/bookings` | JWT | Create a booking |
| `GET` | `/api/bookings/upcoming` | JWT | Get upcoming appointments |
| `GET` | `/api/bookings/past` | JWT | Get past appointments |
| `GET` | `/api/bookings/{id}` | JWT | Get booking by ID |
| `PUT` | `/api/bookings/{id}/cancel` | JWT | Cancel a booking |
| `PUT` | `/api/bookings/{id}/reschedule` | JWT | Reschedule a booking |
| `PUT` | `/api/bookings/{id}/confirm-attendance` | JWT | Confirm attendance (required for risky-tier users) |

#### `GET /api/bookings/slots`
```
GET /api/bookings/slots?salonId=uuid&date=2026-04-10&duration=60
```

#### `POST /api/bookings`
```json
{
  "salonId": "uuid",
  "date": "2026-04-10",
  "startTime": "09:00",
  "serviceIds": ["uuid1", "uuid2"],
  "paymentMethod": "PAY_AT_SALON",
  "staffMemberId": "uuid",
  "notes": "Optional notes"
}
```

---

### Owner — Salon Management

> All `/api/owner/**` endpoints require JWT with `ROLE_SALON_OWNER` and an active subscription.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/owner/salons` | Create a new salon |
| `GET` | `/api/owner/salons` | List my salons |
| `GET` | `/api/owner/salons/{salonId}` | Get one salon by ID |
| `PUT` | `/api/owner/salons/{salonId}` | Update my salon |
| `DELETE` | `/api/owner/salons/{salonId}` | Delete my salon |
| `POST` | `/api/owner/salons/{salonId}/image` | Upload salon cover image (Base64) |

---

### Owner — Service Management

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/owner/salons/{salonId}/services` | Add a service |
| `PUT` | `/api/owner/salons/{salonId}/services/{serviceId}` | Update a service |
| `DELETE` | `/api/owner/salons/{salonId}/services/{serviceId}` | Remove a service |

---

### Owner — Staff Management

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/owner/salons/{salonId}/staff` | List active staff (incl. invite status) |
| `POST` | `/api/owner/salons/{salonId}/staff` | Add a staff member |
| `PUT` | `/api/owner/salons/{salonId}/staff/{staffId}` | Update a staff member |
| `DELETE` | `/api/owner/salons/{salonId}/staff/{staffId}` | Deactivate a staff member |
| `POST` | `/api/owner/salons/{salonId}/staff/{staffId}/invite` | Generate / refresh QR invite token |
| `GET` | `/api/owner/staff/{staffId}/avatar` | Serve staff avatar image |

#### `POST …/staff/{staffId}/invite`
Generates a one-time invite token valid for **7 days**. Calling it again on a pending staff member refreshes the token. Returns:
```json
{
  "data": {
    "staffId": "uuid",
    "inviteToken": "abc123...",
    "deepLink": "loomea://staff/join?token=abc123...",
    "expiresAt": "2026-05-03T12:00:00"
  }
}
```

The Flutter app renders the `deepLink` as a QR code via `qr_flutter`. Staff members scan it to open `StaffRegisterPage`.

#### `StaffMemberDto` fields

| Field | Type | Description |
|---|---|---|
| `id` | string | UUID |
| `salonId` | string | UUID |
| `name` | string | Display name |
| `role` | string | Job title (e.g. "Stylist") |
| `avatarUrl` | string? | Served avatar path |
| `active` | bool | Whether the member is active |
| `inviteToken` | string? | Current pending token (only returned if not yet accepted) |
| `inviteStatus` | string | `"none"` / `"pending"` / `"active"` |

---

### Owner — Booking Management

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/owner/salons/{salonId}/bookings` | All bookings |
| `GET` | `/api/owner/salons/{salonId}/bookings/upcoming` | Upcoming bookings |
| `GET` | `/api/owner/salons/{salonId}/bookings/past` | Past bookings |
| `PUT` | `/api/owner/bookings/{bookingId}/confirm` | Confirm a pending booking |
| `PUT` | `/api/owner/bookings/{bookingId}/complete` | Mark as completed (+2 reliability to client) |
| `PUT` | `/api/owner/bookings/{bookingId}/reject` | Reject a booking |
| `PUT` | `/api/owner/bookings/{bookingId}/no-show` | Mark as no-show (−20 reliability to client) |

---

### Owner — Portfolio

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/owner/salons/{salonId}/portfolio` | Get portfolio posts |
| `POST` | `/api/owner/salons/{salonId}/portfolio` | Add a portfolio post |
| `PUT` | `/api/owner/salons/{salonId}/portfolio/{postId}` | Update caption/image |
| `DELETE` | `/api/owner/salons/{salonId}/portfolio/{postId}` | Delete a post |

---

### Payments (Konnect)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `POST` | `/api/payments/initiate` | JWT | Initiate a booking or subscription payment |
| `GET` | `/api/payments/webhook` | Public | Konnect webhook callback (always returns 200) |
| `GET` | `/api/payments/status/{paymentRef}` | JWT | Poll payment status (also fulfills inline if completed) |

#### `POST /api/payments/initiate`

**Booking payment** — atomically creates the appointment AND initiates the Konnect payment in one call:
```json
{
  "type": "BOOKING",
  "salonId": "uuid",
  "date": "2026-04-10",
  "startTime": "09:00",
  "serviceIds": ["uuid1", "uuid2"],
  "staffMemberId": "uuid"
}
```

**Subscription payment:**
```json
{
  "type": "SUBSCRIPTION",
  "plan": "MONTHLY"
}
```

**Response:**
```json
{
  "data": {
    "payUrl": "https://app.preprod.konnect.network/pay/xxx",
    "paymentRef": "664d0...abc"
  }
}
```

The Flutter app opens `payUrl` in an external browser. Konnect hosts the card entry form.

#### `GET /api/payments/status/{paymentRef}`

```json
{
  "data": {
    "paymentRef": "664d0...abc",
    "status": "COMPLETED",
    "type": "BOOKING"
  }
}
```

Statuses: `PENDING` | `COMPLETED` | `FAILED`

---

### Owner — Dashboard

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/owner/salons/{salonId}/dashboard` | Salon stats & today's bookings |

**Response:**
```json
{
  "data": {
    "salonId": "uuid",
    "salonName": "Luna Hair Studio",
    "todayAppointments": 4,
    "upcomingAppointments": 12,
    "completedAppointments": 48,
    "cancelledAppointments": 3,
    "todayRevenue": 180.00,
    "totalRevenue": 1250.00,
    "todayBookings": [ ... ]
  }
}
```

---

### Owner — Working Hours

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/owner/salons/{salonId}/working-hours` | Get working hours |
| `PUT` | `/api/owner/salons/{salonId}/working-hours` | Replace all working hours |

---

### Staff Self-Service

> Requires JWT with `ROLE_STAFF`. Staff members see only their own assigned bookings.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/staff/me` | Get staff profile (name, role, salon info) |
| `GET` | `/api/staff/me/today` | Today's bookings assigned to this staff member |
| `GET` | `/api/staff/me/bookings/upcoming` | Upcoming assigned bookings |
| `GET` | `/api/staff/me/bookings/past` | Past assigned bookings |

Filtering is done server-side by matching `appointment.staffMember.linkedUserId` against the authenticated user's ID.

**Staff profile response:**
```json
{
  "data": {
    "staffId": "uuid",
    "name": "Alex Smith",
    "role": "Stylist",
    "avatarUrl": "/api/owner/staff/uuid/avatar",
    "salonId": "uuid",
    "salonName": "Luna Hair Studio",
    "salonAddress": "123 Main St, New York"
  }
}
```

---

### Subscriptions

> Requires JWT and `ROLE_SALON_OWNER`. All `/api/owner/**` endpoints are blocked unless the owner has an ACTIVE subscription.

> **Payment for subscriptions is handled via Konnect** — use `POST /api/payments/initiate` with `"type": "SUBSCRIPTION"` instead of posting card details here. The `/api/subscriptions` endpoint is used for status checks only.

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/subscriptions/me` | Get current subscription status |

**Plans:**

| Plan | Price | Duration |
|---|---|---|
| `MONTHLY` | $29.99 | 30 days |
| `YEARLY` | $249.99 | 365 days (save 28%) |

**Subscription Statuses:**

| Status | Meaning |
|---|---|
| `ACTIVE` | Owner can access all owner features |
| `EXPIRED` | End date passed — owner is blocked; must renew |
| `CANCELLED` | Manually cancelled or replaced by a new subscription |

---

## Response Format

All endpoints return a consistent envelope:

```json
{
  "success": true,
  "message": "Optional message",
  "data": { ... }
}
```

Error responses:

```json
{
  "success": false,
  "message": "Descriptive error message",
  "data": null
}
```

| Status | Meaning |
|---|---|
| `200` | OK |
| `201` | Created |
| `400` | Bad Request / Validation error |
| `401` | Unauthorized — missing or invalid JWT |
| `403` | Forbidden — insufficient role or expired subscription |
| `404` | Resource not found |
| `409` | Conflict (e.g. duplicate email, invite already used) |
| `500` | Internal server error |

---

## Reliability Score System

Every client has a **reliability score** (0–120, default 100) that reflects booking behaviour over time.

### Score Changes

| Event | Delta | Notes |
|---|---|---|
| Appointment completed | **+2** | Applied when owner marks COMPLETED |
| 3-in-a-row streak | **+5 bonus** | Added on top of the +2 at every 3rd consecutive completion |
| No-show | **−20** | Applied when owner marks NO_SHOW; resets streak |
| Late cancellation | **−10** | Within 12 hours of appointment start; resets streak |
| Passive recovery | **+1** | Awarded automatically once per 7-day window with no negative actions |

### Tiers & Restrictions

| Tier | Score Range | Restrictions |
|---|---|---|
| **Trusted** | 80–120 | No restrictions — book freely |
| **Normal** | 50–79 | Standard flow |
| **Risky** | 20–49 | Must confirm attendance; max 2 active bookings |
| **Restricted** | 0–19 | Deposit required; booking blocked if score < 10 |

### Scheduled Jobs

| Job | Schedule | Action |
|---|---|---|
| `BookingExpirationService` | Every 2 min | Expires PENDING bookings older than 15 min → CANCELLED |
| `ConfirmationReminderService` | Every 15 min | Auto-cancels unconfirmed risky-tier appointments past the 3h window |
| `PassiveRecoveryService` | Daily at 06:00 | Awards +1 to users with a 7-day clean window |
| `SubscriptionExpirationService` | Daily at 02:00 | Marks ACTIVE subscriptions with a passed endDate as EXPIRED |

---

## Konnect Payment Integration

Loomea uses [Konnect](https://konnect.network) as its hosted payment gateway (Tunisian market — TND). Card entry happens entirely on Konnect's page; no card data touches the backend.

### Flow

```
Flutter                          Backend                        Konnect
  │                                 │                              │
  │── POST /api/payments/initiate ──►│                              │
  │     {type, salonId, date, ...}  │                              │
  │                                 │── POST /payments/init-payment►│
  │                                 │◄─ {payUrl, paymentRef} ───────│
  │◄──── {payUrl, paymentRef} ──────│                              │
  │                                 │                              │
  │── opens payUrl in browser ─────────────────────────────────────►│
  │                                 │                              │
  │ (card entry happens on Konnect's hosted page)                   │
  │                                 │                              │
  │                     (on completion, Konnect may call:)          │
  │                                 │◄─ GET /api/payments/webhook? ─│
  │                                 │    payment_ref=xxx            │
  │                                 │                              │
  │── GET /api/payments/status/ref ►│                              │
  │   (polls every 3 seconds)       │── GET /payments/:ref ────────►│
  │                                 │◄─ {status: "completed"} ──────│
  │                                 │ fulfills order inline         │
  │◄──── {status: "COMPLETED"} ─────│                              │
```

Both the webhook and the polling endpoint call Konnect to verify the actual payment status before fulfilling an order — neither path trusts the call alone.

### Booking Fulfillment

When payment is confirmed: the appointment's status changes from `PENDING` → `CONFIRMED`.

### Subscription Fulfillment

When payment is confirmed: any existing `ACTIVE` subscription is cancelled, and a new one is created starting immediately.

### Security Measures

- The Konnect API key (`KONNECT_API_KEY`) lives only in the backend environment — it never reaches Flutter.
- The webhook endpoint always returns HTTP 200 to avoid leaking information to attackers.
- Idempotency: `PaymentRecord.status` is checked before processing — duplicate webhook calls are ignored.
- Ownership is validated on `/api/payments/status/:ref` — users can only see their own payment records.
- Amounts are validated server-side; the Flutter `totalAmount` field is display-only.

### Environment Variables

| Variable | Required | Description |
|---|---|---|
| `KONNECT_API_KEY` | Yes | Konnect API key (keep secret) |
| `KONNECT_WALLET_ID` | Yes | Konnect receiver wallet ID |
| `KONNECT_BASE_URL` | No | Konnect API base URL (default: preprod) |
| `APP_BASE_URL` | No | Public HTTPS origin for webhook push (e.g. `https://api.yourdomain.com`). If unset, Flutter polling is the sole fulfillment path — works fine for local dev. |

---

## Configuration Reference

```yaml
# application.yml
server:
  port: 8080

app:
  jwt:
    secret: <256-bit-secret>
    expiration-ms: 86400000      # 24 hours

  konnect:
    api-key: ${KONNECT_API_KEY}
    wallet-id: ${KONNECT_WALLET_ID}
    base-url: ${KONNECT_BASE_URL:https://api.preprod.konnect.network/api/v2}
    webhook-origin: ${APP_BASE_URL:}   # leave blank for polling-only (local dev)
```
