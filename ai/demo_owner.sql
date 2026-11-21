-- Demo owner and 8 weeks of bookings for "[Demo] Salon Yasmine", for trying
-- owner insights locally. Run demo_salons.sql first. Safe to re-run: it only
-- replaces users whose email ends with "@looki.demo" and their appointments.
--
--   Get-Content ai\demo_owner.sql | docker exec -i looki-postgres psql -U loomea -d loomea -v owner_password=<password>
--
-- Then sign in to the app as owner@looki.demo (salon owner) or
-- client1@looki.demo (client) with that password.

\set ON_ERROR_STOP on
SET client_min_messages = warning;
BEGIN;

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DELETE FROM appointment_services WHERE appointment_id IN (
    SELECT a.id FROM appointments a JOIN users u ON u.id = a.user_id WHERE u.email LIKE '%@looki.demo');
DELETE FROM appointments WHERE user_id IN (SELECT id FROM users WHERE email LIKE '%@looki.demo');
UPDATE salons SET owner_id = NULL WHERE owner_id IN (SELECT id FROM users WHERE email LIKE '%@looki.demo');
DELETE FROM users WHERE email LIKE '%@looki.demo';

INSERT INTO users (id, email, full_name, password, phone_verified, role, reliability_score,
                   consecutive_completions, created_at)
VALUES ('d1000000-0000-4000-8000-000000000000', 'owner@looki.demo', 'Yasmine (Demo owner)',
        crypt(:'owner_password', gen_salt('bf', 10)), true, 'SALON_OWNER', 100, 0, now());

UPDATE salons SET owner_id = 'd1000000-0000-4000-8000-000000000000'
WHERE id = 'd0000000-0000-4000-8000-000000000001';

-- Demo clients. Only client1@looki.demo can sign in (same password as the
-- owner); "!" is not a valid password hash, so the others cannot.
INSERT INTO users (id, email, full_name, password, phone_verified, role, reliability_score,
                   consecutive_completions, created_at)
SELECT ('d2000000-0000-4000-8000-0000000000' || lpad(n::text, 2, '0'))::uuid,
       'client' || n || '@looki.demo', 'Demo client ' || n,
       CASE WHEN n = 1 THEN crypt(:'owner_password', gen_salt('bf', 10)) ELSE '!' END,
       true, 'CLIENT', 100, 0, now()
FROM generate_series(1, 12) AS n;

DO $$
DECLARE
    -- Weekly pattern (ISO weekday, start time, only in the last 4 weeks).
    -- Saturdays are busy, Tuesday mornings are empty, Thursdays and Fridays have grown recently.
    pattern CONSTANT text[][] := ARRAY[
        ['1','10:00','n'], ['1','14:00','n'],
        ['2','14:00','n'],
        ['3','10:30','n'], ['3','15:00','n'],
        ['4','11:00','n'], ['4','15:00','n'], ['4','16:00','y'],
        ['5','10:00','n'], ['5','14:30','n'], ['5','17:30','n'], ['5','18:00','y'],
        ['6','09:30','n'], ['6','11:00','n'], ['6','13:00','n'],
        ['6','14:00','n'], ['6','15:30','n'], ['6','16:00','n']];
    services CONSTANT text[][] := ARRAY[
        ['Haircut (Women''s)','35','45'], ['Haircut (Women''s)','35','45'],
        ['Blowout / Blowdry','25','30'], ['Hair Coloring','90','90'], ['Manicure','20','30']];
    salon CONSTANT uuid := 'd0000000-0000-4000-8000-000000000001';
    today CONSTANT date := (now() AT TIME ZONE 'Africa/Tunis')::date;
    now_time CONSTANT time := (now() AT TIME ZONE 'Africa/Tunis')::time;
    day date;
    p int;
    i int := 0;
    svc int;
    start_at time;
    appt_status text;
    appt uuid;
BEGIN
    FOR day IN SELECT generate_series(today - 55, today + 7, interval '1 day')::date LOOP
        FOR p IN 1 .. array_length(pattern, 1) LOOP
            CONTINUE WHEN pattern[p][1]::int <> extract(isodow FROM day);
            CONTINUE WHEN pattern[p][3] = 'y' AND day < today - 27;
            i := i + 1;
            svc := 1 + i % 5;
            start_at := pattern[p][2]::time;
            IF day > today OR (day = today AND start_at >= now_time) THEN
                appt_status := 'CONFIRMED';
            ELSIF i % 11 = 0 THEN
                appt_status := 'CANCELLED';
            ELSIF i % 17 = 0 THEN
                appt_status := 'NO_SHOW';
            ELSE
                appt_status := 'COMPLETED';
            END IF;
            appt := gen_random_uuid();
            INSERT INTO appointments (id, salon_id, user_id, date, start_time, end_time, status,
                                      payment_method, total_price, version, created_at,
                                      requires_client_confirmation)
            VALUES (appt, salon,
                    ('d2000000-0000-4000-8000-0000000000' || lpad((1 + (i * 7) % 12)::text, 2, '0'))::uuid,
                    day, start_at, start_at + make_interval(mins => services[svc][3]::int), appt_status,
                    'PAY_AT_SALON', services[svc][2]::double precision, 0, now(), false);
            INSERT INTO appointment_services (id, appointment_id, service_name, price, duration_minutes)
            VALUES (gen_random_uuid(), appt, services[svc][1], services[svc][2]::double precision,
                    services[svc][3]::int);
        END LOOP;
    END LOOP;
END $$;

COMMIT;
