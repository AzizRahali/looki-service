# Loomea Service

Spring Boot REST API backend for the Loomea salon booking platform. Handles authentication, salon management, booking lifecycle, real-time events via SSE, and owner dashboard.

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
│   ├── dto/
│   ├── entity/
│   ├── repository/
│   └── service/
├── booking/                # Booking lifecycle & time slots
│   ├── controller/
│   ├── dto/
│   ├── entity/
│   ├── repository/
│   └── service/
├── owner/                  # Owner dashboard, salon & booking management
│   ├── controller/
│   ├── dto/
│   └── service/
├── salon/                  # Public salon browsing & reviews
│   ├── controller/
│   ├── dto/
│   ├── entity/
│   ├── repository/
│   └── service/
├── search/                 # Salon search
│   ├── controller/
│   └── service/
└── common/                 # Shared: security, config, DTOs, exceptions
    ├── config/
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

Tokens are issued on `/api/auth/login` and `/api/auth/register`. They expire after **24 hours**. Refresh tokens expire after **7 days**.

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
| `POST` | `/api/auth/register` | Public | Register a new user |
| `POST` | `/api/auth/login` | Public | Login and receive JWT |
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
  "phone": "+1234567890"
}
```

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
    "refreshToken": "eyJ...",
    "user": {
      "id": "uuid",
      "fullName": "Jane Doe",
      "email": "jane@example.com",
      "phone": "+1234567890",
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

#### `GET /api/search`
Query params: `q` (search query string)

```
GET /api/search?q=haircut
```

---

### Reviews

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/reviews/salon/{salonId}` | Public | Get all reviews for a salon |
| `POST` | `/api/reviews` | JWT | Submit a review |
| `DELETE` | `/api/reviews/{reviewId}` | JWT | Delete your review |

#### `POST /api/reviews`
```json
{
  "salonId": "uuid",
  "rating": 5,
  "comment": "Amazing service!"
}
```

---

### Bookings (Client)

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/api/bookings/slots` | Public | Get available time slots |
| `POST` | `/api/bookings` | JWT | Create a booking |
| `GET` | `/api/bookings/upcoming` | JWT | Get upcoming appointments |
| `GET` | `/api/bookings/past` | JWT | Get past appointments |
| `GET` | `/api/bookings/{id}` | JWT | Get booking by ID |
| `PUT` | `/api/bookings/{id}/cancel` | JWT | Cancel a booking (late penalty if < 12h before) |
| `PUT` | `/api/bookings/{id}/reschedule` | JWT | Reschedule a booking |
| `PUT` | `/api/bookings/{id}/confirm-attendance` | JWT | Confirm attendance (required for risky-tier users) |
| `GET` | `/api/bookings/events` | JWT | SSE stream — real-time booking status updates |

#### `GET /api/bookings/slots`
Query params: `salonId`, `date` (yyyy-MM-dd), `duration` (minutes, default 60)

```
GET /api/bookings/slots?salonId=uuid&date=2026-04-10&duration=60
```

**Response:**
```json
{
  "data": [
    { "time": "09:00 AM", "status": "AVAILABLE" },
    { "time": "10:00 AM", "status": "LIMITED" },
    { "time": "11:00 AM", "status": "BOOKED" }
  ]
}
```

#### `POST /api/bookings`
```json
{
  "salonId": "uuid",
  "date": "2026-04-10",
  "startTime": "09:00",
  "serviceIds": ["uuid1", "uuid2"],
  "paymentMethod": "PAY_AT_SALON",
  "notes": "Optional notes"
}
```

#### `PUT /api/bookings/{id}/reschedule`
```json
{
  "date": "2026-04-12",
  "startTime": "10:00"
}
```

#### SSE — `GET /api/bookings/events`
Streams `booking-confirmed` and `booking-rejected` events:
```
event: booking-confirmed
data: { "id": "uuid", "status": "CONFIRMED", "salonName": "...", ... }

event: booking-rejected
data: { "id": "uuid", "status": "REJECTED", ... }
```

---

### Owner — Salon Management

> All `/api/owner/**` endpoints require JWT and `ROLE_SALON_OWNER`.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/owner/salons` | Create a new salon |
| `GET` | `/api/owner/salons` | List my salons |
| `GET` | `/api/owner/salons/{salonId}` | Get one salon by ID |
| `PUT` | `/api/owner/salons/{salonId}` | Update my salon |
| `DELETE` | `/api/owner/salons/{salonId}` | Delete my salon |

#### `POST /api/owner/salons`
```json
{
  "name": "Luna Hair Studio",
  "description": "Premium hair salon",
  "address": "123 Main St, New York",
  "category": "Hair",
  "phone": "+1234567890",
  "lat": 40.7128,
  "lng": -74.0060,
  "openingTime": "09:00",
  "closingTime": "20:00",
  "imageUrl": "https://..."
}
```

---

### Owner — Service Management

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/owner/salons/{salonId}/services` | Add a service |
| `PUT` | `/api/owner/salons/{salonId}/services/{serviceId}` | Update a service |
| `DELETE` | `/api/owner/salons/{salonId}/services/{serviceId}` | Remove a service |

#### `POST /api/owner/salons/{salonId}/services`
```json
{
  "name": "Haircut",
  "description": "Classic haircut",
  "category": "Hair",
  "price": 45.00,
  "durationMinutes": 60
}
```

---

### Owner — Booking Management

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/owner/salons/{salonId}/bookings` | All bookings |
| `GET` | `/api/owner/salons/{salonId}/bookings/upcoming` | Upcoming bookings |
| `GET` | `/api/owner/salons/{salonId}/bookings/past` | Past bookings |
| `PUT` | `/api/owner/bookings/{bookingId}/confirm` | Confirm a pending booking |
| `PUT` | `/api/owner/bookings/{bookingId}/complete` | Mark booking as completed — awards +2 reliability to client |
| `PUT` | `/api/owner/bookings/{bookingId}/reject` | Reject a booking |
| `PUT` | `/api/owner/bookings/{bookingId}/no-show` | Mark as no-show — applies −20 reliability penalty to client |

---

### Owner — Portfolio

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/owner/salons/{salonId}/portfolio` | Get portfolio posts |
| `POST` | `/api/owner/salons/{salonId}/portfolio` | Add a portfolio post |
| `DELETE` | `/api/owner/salons/{salonId}/portfolio/{postId}` | Delete a post |

#### `POST /api/owner/salons/{salonId}/portfolio`
```json
{
  "caption": "Fresh fade cut",
  "imageUrl": "https://..."
}
```

---

### Owner — Dashboard

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/owner/salons/{salonId}/dashboard` | Salon stats & today's bookings |

**Response:**
```json
{
  "data": {
    "totalRevenue": 1250.00,
    "todayRevenue": 180.00,
    "totalBookings": 48,
    "todayBookings": 4,
    "pendingBookings": 2,
    "todayBookingsList": [ ... ]
  }
}
```

---

### Subscriptions

> Requires JWT and `ROLE_SALON_OWNER`. All `/api/owner/**` endpoints are blocked unless the owner has an ACTIVE subscription.

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/subscriptions` | Create or renew a subscription |
| `GET` | `/api/subscriptions/me` | Get current subscription status |

#### `POST /api/subscriptions`
```json
{
  "plan": "MONTHLY",
  "cardNumber": "4111 1111 1111 1111",
  "cardExpiry": "12/27",
  "cardCvv": "123",
  "cardholderName": "Jane Doe"
}
```

**Plans:**

| Plan | Price | Duration |
|---|---|---|
| `MONTHLY` | $29.99 | 30 days |
| `YEARLY` | $249.99 | 365 days (save 28%) |

**Response:**
```json
{
  "data": {
    "id": "uuid",
    "plan": "MONTHLY",
    "planLabel": "Monthly",
    "priceLabel": "$29.99 / month",
    "price": 29.99,
    "status": "ACTIVE",
    "startDate": "2026-04-14T00:00:00Z",
    "endDate": "2026-05-14T00:00:00Z",
    "paymentReference": "PAY-A1B2C3D4",
    "cardLast4": "1111"
  }
}
```

**Subscription Statuses:**

| Status | Meaning |
|---|---|
| `ACTIVE` | Owner can access all owner features |
| `EXPIRED` | End date passed — owner is blocked; must renew |
| `CANCELLED` | Manually cancelled or replaced by a new subscription |

**Enforcement:** A `SubscriptionInterceptor` intercepts every `/api/owner/**` request and returns `403 SUBSCRIPTION_REQUIRED` if no active subscription is found.

**Scheduled job:** `SubscriptionExpirationService` runs daily at 02:00 and marks ACTIVE subscriptions with a passed `endDate` as EXPIRED.

---

### Owner — Real-time Events (SSE)

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/owner/salons/{salonId}/events` | SSE stream — new booking alerts |
| `GET` | `/api/salons/{salonId}/slots/events` | SSE stream — slot availability updates |

```
event: new-booking
data: { "bookingId": "uuid", "clientName": "...", "startTime": "10:00", ... }

event: slot-booked
data: { "date": "2026-04-10", "startTime": "10:00" }
```

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
| `403` | Forbidden — insufficient role |
| `404` | Resource not found |
| `409` | Conflict |
| `500` | Internal server error |

---

## Roles

| Role | Value | Access |
|---|---|---|
| Client | `CLIENT` | Booking, profile, saved salons |
| Salon Owner | `SALON_OWNER` | All owner endpoints + client features |

---

## Reliability Score System

Every client has a **reliability score** (0–120, default 100) that reflects their booking behaviour over time. The score controls booking restrictions and encourages punctuality.

### Score Changes

| Event | Delta | Notes |
|---|---|---|
| Appointment completed | **+2** | Applied when owner marks COMPLETED |
| 3-in-a-row streak | **+5 bonus** | Added on top of the +2 at every 3rd consecutive completion |
| No-show | **−20** | Applied when owner marks NO_SHOW; resets streak |
| Late cancellation | **−10** | Cancellation within 12 hours of appointment start; resets streak |
| Passive recovery | **+1** | Awarded automatically once per 7-day window with no negative actions |

Score is bounded to **[0, 120]**.

### Tiers & Restrictions

| Tier | Score Range | Restrictions |
|---|---|---|
| **Trusted** | 80–120 | No restrictions — book freely |
| **Normal** | 50–79 | Standard flow; extra appointment reminders |
| **Risky** | 20–49 | Must confirm attendance before appointment; max 2 active bookings |
| **Restricted** | 0–19 | Deposit required; booking blocked if score < 10 |

### Progressive Restriction Logic

| Score threshold | Restriction applied |
|---|---|
| < 50 | Attendance confirmation required before appointment |
| < 30 | Max 1 active (PENDING/CONFIRMED) booking at a time |
| < 20 | Deposit required to book |
| < 10 | Booking temporarily blocked for 7 days |

### Attendance Confirmation Flow

For clients with score < 50, each booking has `requiresClientConfirmation: true`.

- The client must call `PUT /api/bookings/{id}/confirm-attendance` at least **3 hours** before the appointment.
- A scheduled job (`ConfirmationReminderService`) runs every **15 minutes** and **auto-cancels** any CONFIRMED booking that required confirmation but was not confirmed within the 3-hour window.
- Auto-cancelled bookings do **not** add an extra score penalty (the low score already triggered the requirement).

### Scheduled Jobs

| Job | Schedule | Action |
|---|---|---|
| `BookingExpirationService` | Every 2 min | Expires PENDING bookings older than 15 min → CANCELLED |
| `ConfirmationReminderService` | Every 15 min | Auto-cancels unconfirmed risky-tier appointments within 3h window |
| `PassiveRecoveryService` | Daily at 06:00 | Awards +1 to users with a 7-day clean window |

### Score in API Responses

The `GET /api/auth/me` response and login/register responses include:

```json
{
  "reliabilityScore": 85,
  "reliabilityTier": "TRUSTED"
}
```

Owner booking responses include the client's score:

```json
{
  "clientReliabilityScore": 34,
  "clientReliabilityTier": "RISKY"
}
```

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
    refresh-expiration-ms: 604800000  # 7 days
```
