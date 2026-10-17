# SmartMeter Backend

Java 17, Spring Boot 3.3, MySQL 8, JWT auth, MQTT (cloud broker over TLS), 20-meter simulator.

## 1. Run
```
export DB_USER=root DB_PASS=yourpass
export JWT_SECRET='a-long-random-string-of-at-least-32-characters'
export BOOT_LANDLORD_USER=landlord@example.com BOOT_LANDLORD_PASS='ChangeMe123!'

# Email (for "forgot password" links) - use an app password, not your real inbox password, for Gmail
export MAIL_HOST=smtp.gmail.com MAIL_PORT=587
export MAIL_USERNAME='you@gmail.com' MAIL_PASSWORD='your-16-char-app-password'
export MAIL_FROM='no-reply@yourapp.com'
export FRONTEND_URL='http://localhost:5173'

# Your own contact, shown on the landlord's Customer Care tab (and to tenants alongside their landlord's)
export SUPPORT_NAME='Your Name'
export SUPPORT_EMAIL='you@example.com'
export SUPPORT_PHONE='+91-XXXXXXXXXX'

# MQTT cloud broker (EMQX Cloud or HiveMQ Cloud) - use the TLS port, usually 8883
export MQTT_BROKER_URL='ssl://xxxx.ala.eu-central-1.emqxsl.com:8883'
export MQTT_USERNAME='your-mqtt-username'
export MQTT_PASSWORD='your-mqtt-password'

# Simulator: 20 virtual meters publishing every 3s
export SIMULATOR_ENABLED=true
export SIMULATOR_METER_COUNT=20
export SIMULATOR_INTERVAL_MS=3000
export SIMULATOR_DEVICE_PREFIX=SIM

mvn spring-boot:run
```
The first landlord is created only when BOOT_LANDLORD_PASS is set and no landlord exists yet.

## 2. Getting a free cloud broker (EMQX Cloud Serverless or HiveMQ Cloud free tier)
1. Sign up, create a deployment/cluster.
2. Create MQTT credentials (username + password) - do NOT use the default anonymous access.
3. Copy the TLS connection host and port (8883) into MQTT_BROKER_URL as `ssl://host:8883`.
4. In EMQX/HiveMQ's ACL settings, once you're ready for production, restrict each real device's
   credentials to only its own `meters/{deviceId}/telemetry` (publish) and `meters/{deviceId}/cmd`
   (subscribe) topics. The backend and simulator need broader access (`meters/+/telemetry` etc.).

## 3. End-to-end test flow
1. Start the app with the simulator enabled (above). Logs will show `Simulator MQTT connected ... (20 virtual meters)`
   publishing to `meters/SIM-001/telemetry` ... `meters/SIM-020/telemetry` every 3 seconds.
2. Log in as the landlord (`POST /api/auth/login`), set tariff/min-balance (`PUT /api/landlord/settings`),
   then create rooms with `deviceId` matching the simulator's ids: `POST /api/landlord/rooms`
   `{"name":"Room 1","deviceId":"SIM-001"}` ... up to `SIM-020`.
3. Onboard a tenant into that room with an opening wallet balance: `POST /api/landlord/tenants`.
4. Watch it bill itself: every ~3s the backend receives that meter's telemetry, computes kWh used since
   the last sample, deducts the tenant's wallet at the configured tariff, and stores a `Reading` row.
5. Let the balance run down below the minimum -> the backend auto-publishes a relay-OFF command
   (`meters/SIM-001/cmd`), the simulator sets that meter's power to 0, and an SMS (logged, not actually
   sent - see SmsService) fires once.
6. Tenant recharges (`POST /api/tenant/recharges` with a UTR), landlord approves it
   (`POST /api/landlord/recharges/{id}/approve`) -> wallet credited, relay auto-restored (unless the
   landlord had cut it manually, which is never auto-overridden).
7. Test the offline buffer: `POST /api/dev/simulator/SIM-003/offline` stops that one virtual meter from
   publishing (it keeps generating and queuing samples in memory instead, exactly like the ESP32 would
   write to LittleFS while WiFi is down). `POST /api/dev/simulator/SIM-003/online` reconnects it and
   flushes the whole backlog in one burst, oldest first - confirming no data or billing is lost.

## 4. Architecture notes
- `MqttGateway`: the backend's own MQTT client (subscribes to `meters/+/telemetry` and `meters/+/status`).
- `TelemetryListener` -> `TelemetryService`: the billing pipeline. Delta kWh since the meter's last reading
  x tariff = cost, deducted from the wallet under a row lock (`WalletService`), with the relay cut/restored
  and an SMS queued as needed.
- `MeterCommandService`: the only place that changes a relay's desired state; always publishes to
  `meters/{deviceId}/cmd` so the DB and the real/simulated device never disagree for long.
- `SimulatorRunner`: a second, independent MQTT client pretending to be N real devices - this is what your
  ESP32 firmware should eventually replace, topic-for-topic.
- `Reading`: one row per telemetry sample, kept for the consumption charts/history (next module).

## 5. Security notes (unchanged from module 1, still apply)
BCrypt (cost 12), JWT (HS256, 60 min, re-checked in DB every request), `/api/landlord/**` vs
`/api/tenant/**` strictly role-scoped and filtered by owner id, login lockout after 5 failures,
UPI recharge UTRs unique (no double credit), QR upload validated PNG/JPEG + magic bytes + 2MB cap.
`/api/dev/simulator/**` is open with no auth - it only controls the in-memory simulator, not real data,
but delete `SimulatorController` before deploying anywhere public.

## 6. Not yet done (next modules)
Consumption chart APIs (daily / yesterday / last 5 days, landlord revenue per room), React frontend,
ESP32 firmware (LittleFS offline queue mirroring the simulator's buffer-and-flush).

## 7. Login is now by email
Usernames are email addresses end to end - tenant onboarding requires a valid email, and login treats it
case-insensitively (always stored lowercased). The first landlord's `BOOT_LANDLORD_USER` should be an email too.

## 8. Password reset (self-service, by email)
- `POST /api/auth/forgot-password {"email":"..."}` - always returns the same generic message, whether or
  not the email is registered (prevents account enumeration). If it exists, a reset link is emailed.
- `POST /api/auth/reset-password {"email":"...","token":"...","newPassword":"..."}` - the token is only
  ever stored as a SHA-256 hash on the user row (`resetTokenHash`/`resetTokenExpiry`), expires after
  `app.password-reset.expiry-minutes` (default 30), and is single-use.
- Needs real SMTP credentials in MAIL_* env vars to actually send. Without them, the request still
  "succeeds" from the caller's point of view but the email silently fails to send (logged as an error) -
  check the console if a reset email never arrives.

## 9. Self-service account settings ("Security" tab)
- `GET /api/account/me` - current email/phone/name/role.
- `PUT /api/account/profile {"email":"...","phone":"...","currentPassword":"..."}` - changes login email
  and/or phone; requires the current password. Since email is the JWT subject, the response includes a
  **new token** - the frontend must store it immediately or the old token stops working on the next call.
- `PUT /api/account/password {"currentPassword":"...","newPassword":"..."}`.
Available to both roles (`/api/account/**` accepts ROLE_LANDLORD or ROLE_TENANT).

## 10. Customer Care tab
- `GET /api/landlord/support` - just your own (the product maker's) contact, from SUPPORT_NAME/EMAIL/PHONE.
- `GET /api/tenant/support` - the tenant's landlord's name/email/phone, plus your contact as a fallback.
