-- =============================================================
--  Looki – Full seed data
--  Run this once against the local looki DB.
--  Requires: CREATE EXTENSION IF NOT EXISTS pgcrypto;
--  Password for all seed users: Test1234!
-- =============================================================

-- Enable pgcrypto for proper BCrypt hashing
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- ── 1. USERS ─────────────────────────────────────────────────
-- Roles: SALON_OWNER, CLIENT

INSERT INTO users (id, full_name, email, password, phone, location, role,
                   phone_verified, reliability_score, consecutive_completions,
                   created_at)
VALUES
  -- Owners (role = SALON_OWNER)
  ('00000000-0000-0000-0000-000000000002',
   'Sarra Ben Ali', 'sarra.owner@looki.tn',
   crypt('Test1234!', gen_salt('bf', 10)),
   '+21698000002', 'Tunis', 'SALON_OWNER', true, 100, 0, NOW()),

  ('00000000-0000-0000-0000-000000000003',
   'Karim Mansour', 'karim.owner@looki.tn',
   crypt('Test1234!', gen_salt('bf', 10)),
   '+21698000003', 'Sfax', 'SALON_OWNER', true, 100, 0, NOW()),

  -- Clients
  ('00000000-0000-0000-0000-000000000004',
   'Aymen Trabelsi', 'aymen@looki.tn',
   crypt('Test1234!', gen_salt('bf', 10)),
   '+21692000004', 'Tunis', 'CLIENT', true, 100, 3, NOW()),

  ('00000000-0000-0000-0000-000000000005',
   'Nour Khelifi', 'nour@looki.tn',
   crypt('Test1234!', gen_salt('bf', 10)),
   '+21692000005', 'Sousse', 'CLIENT', false, 85, 1, NOW()),

  ('00000000-0000-0000-0000-000000000006',
   'Ines Chaabane', 'ines@looki.tn',
   crypt('Test1234!', gen_salt('bf', 10)),
   '+21692000006', 'Bizerte', 'CLIENT', true, 110, 5, NOW());


-- ── 2. SALONS ─────────────────────────────────────────────────

INSERT INTO salons (id, name, description, address, phone,
                    latitude, longitude, rating, review_count,
                    opening_hours, owner_id)
VALUES
  ('10000000-0000-0000-0000-000000000001',
   'Glamour Studio',
   'Premium hair and beauty salon in central Tunis. Expert stylists, latest trends.',
   '12 Avenue Habib Bourguiba, Tunis', '+21671000011',
   36.8189, 10.1657, 4.7, 0,
   'Mon-Sat 09:00-20:00',
   '00000000-0000-0000-0000-000000000002'),

  ('10000000-0000-0000-0000-000000000002',
   'Barber & Co.',
   'Traditional and modern barbershop in Sfax city centre.',
   '5 Rue de la République, Sfax', '+21674000022',
   34.7398, 10.7600, 4.5, 0,
   'Mon-Sun 08:00-21:00',
   '00000000-0000-0000-0000-000000000003');


-- ── 3. SALON TAGS ─────────────────────────────────────────────

INSERT INTO salon_tags (salon_id, tag) VALUES
  ('10000000-0000-0000-0000-000000000001', 'hair'),
  ('10000000-0000-0000-0000-000000000001', 'nails'),
  ('10000000-0000-0000-0000-000000000001', 'makeup'),
  ('10000000-0000-0000-0000-000000000002', 'barber'),
  ('10000000-0000-0000-0000-000000000002', 'beard'),
  ('10000000-0000-0000-0000-000000000002', 'haircut');


-- ── 4. SALON SERVICES ────────────────────────────────────────

INSERT INTO salon_services (id, name, description, category, price, duration_minutes, icon, salon_id)
VALUES
  -- Glamour Studio
  ('20000000-0000-0000-0000-000000000001',
   'Haircut & Blowdry', 'Cut, style and blowdry', 'Hair', 45.00, 60, 'scissors',
   '10000000-0000-0000-0000-000000000001'),
  ('20000000-0000-0000-0000-000000000002',
   'Hair Colouring', 'Full colour or highlights', 'Hair', 90.00, 120, 'palette',
   '10000000-0000-0000-0000-000000000001'),
  ('20000000-0000-0000-0000-000000000003',
   'Manicure', 'Classic nail care and polish', 'Nails', 25.00, 45, 'sparkles',
   '10000000-0000-0000-0000-000000000001'),
  ('20000000-0000-0000-0000-000000000004',
   'Gel Nails', 'Long-lasting gel application', 'Nails', 40.00, 75, 'gem',
   '10000000-0000-0000-0000-000000000001'),
  ('20000000-0000-0000-0000-000000000005',
   'Bridal Makeup', 'Full bridal look with trial', 'Makeup', 150.00, 120, 'star',
   '10000000-0000-0000-0000-000000000001'),

  -- Barber & Co.
  ('20000000-0000-0000-0000-000000000006',
   'Classic Haircut', 'Scissors or clippers cut', 'Barber', 15.00, 30, 'scissors',
   '10000000-0000-0000-0000-000000000002'),
  ('20000000-0000-0000-0000-000000000007',
   'Beard Trim', 'Shape and trim beard', 'Barber', 10.00, 20, 'beard',
   '10000000-0000-0000-0000-000000000002'),
  ('20000000-0000-0000-0000-000000000008',
   'Haircut + Beard', 'Cut and beard combo', 'Barber', 22.00, 50, 'scissors',
   '10000000-0000-0000-0000-000000000002'),
  ('20000000-0000-0000-0000-000000000009',
   'Hot Towel Shave', 'Traditional straight-razor shave', 'Barber', 18.00, 40, 'flame',
   '10000000-0000-0000-0000-000000000002');


-- ── 5. WORKING HOURS ─────────────────────────────────────────
-- day_of_week: 0=Mon … 6=Sun

-- Glamour Studio: Mon-Sat 09:00-20:00, closed Sun
INSERT INTO working_hours (id, salon_id, day_of_week, open_time, close_time, closed) VALUES
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000001', 0, '09:00', '20:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000001', 1, '09:00', '20:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000001', 2, '09:00', '20:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000001', 3, '09:00', '20:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000001', 4, '09:00', '20:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000001', 5, '09:00', '20:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000001', 6, NULL,    NULL,    true);

-- Barber & Co.: Mon-Sun 08:00-21:00
INSERT INTO working_hours (id, salon_id, day_of_week, open_time, close_time, closed) VALUES
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000002', 0, '08:00', '21:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000002', 1, '08:00', '21:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000002', 2, '08:00', '21:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000002', 3, '08:00', '21:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000002', 4, '08:00', '21:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000002', 5, '08:00', '21:00', false),
  (gen_random_uuid(), '10000000-0000-0000-0000-000000000002', 6, '08:00', '21:00', false);


-- ── 6. SUBSCRIPTIONS ─────────────────────────────────────────

INSERT INTO subscriptions (id, user_id, plan, status, start_date, end_date, payment_reference, card_last4, created_at)
VALUES
  (gen_random_uuid(),
   '00000000-0000-0000-0000-000000000002',
   'MONTHLY', 'ACTIVE',
   NOW(), NOW() + INTERVAL '30 days',
   'PAY-MOCK-0001', '4242', NOW()),

  (gen_random_uuid(),
   '00000000-0000-0000-0000-000000000003',
   'YEARLY', 'ACTIVE',
   NOW(), NOW() + INTERVAL '365 days',
   'PAY-MOCK-0002', '1234', NOW());


-- ── 7. APPOINTMENTS ──────────────────────────────────────────

INSERT INTO appointments (id, user_id, salon_id, date, start_time, end_time,
                          status, payment_method, total_price, notes,
                          requires_client_confirmation, version, created_at)
VALUES
  -- Completed past appointment
  ('30000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000004',
   '10000000-0000-0000-0000-000000000001',
   CURRENT_DATE - 7, '10:00', '11:00',
   'COMPLETED', 'PAY_AT_SALON', 45.00, 'Please use rose tones.',
   false, 0, NOW() - INTERVAL '7 days'),

  -- Upcoming confirmed appointment
  ('30000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000005',
   '10000000-0000-0000-0000-000000000002',
   CURRENT_DATE + 3, '14:00', '14:30',
   'CONFIRMED', 'PAY_AT_SALON', 15.00, NULL,
   false, 0, NOW()),

  -- Pending appointment
  ('30000000-0000-0000-0000-000000000003',
   '00000000-0000-0000-0000-000000000006',
   '10000000-0000-0000-0000-000000000001',
   CURRENT_DATE + 5, '09:00', '10:15',
   'PENDING', 'PAY_WITH_CARD', 65.00, 'Gel nails + manicure combo',
   false, 0, NOW());


-- ── 8. APPOINTMENT SERVICES ──────────────────────────────────

INSERT INTO appointment_services (id, appointment_id, service_name, price, duration_minutes)
VALUES
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000001', 'Haircut & Blowdry', 45.00, 60),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000002', 'Classic Haircut',   15.00, 30),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000003', 'Manicure',           25.00, 45),
  (gen_random_uuid(), '30000000-0000-0000-0000-000000000003', 'Gel Nails',          40.00, 75);


-- ── 9. REVIEWS ───────────────────────────────────────────────

INSERT INTO reviews (id, salon_id, user_id, rating, comment, created_at)
VALUES
  (gen_random_uuid(),
   '10000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000004',
   5, 'Amazing salon! Super professional and friendly staff.', CURRENT_DATE - 6),

  (gen_random_uuid(),
   '10000000-0000-0000-0000-000000000002',
   '00000000-0000-0000-0000-000000000005',
   4, 'Great haircut, clean place. Will come back.', CURRENT_DATE - 2);


-- Update salon review counts and ratings
UPDATE salons SET review_count = 1, rating = 5.0
WHERE id = '10000000-0000-0000-0000-000000000001';

UPDATE salons SET review_count = 1, rating = 4.0
WHERE id = '10000000-0000-0000-0000-000000000002';


-- ── 10. PORTFOLIO POSTS ──────────────────────────────────────

INSERT INTO portfolio_posts (id, salon_id, caption, image_url, created_at)
VALUES
  (gen_random_uuid(),
   '10000000-0000-0000-0000-000000000001',
   'Balayage highlights – client transformation',
   'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?w=400',
   NOW() - INTERVAL '10 days'),

  (gen_random_uuid(),
   '10000000-0000-0000-0000-000000000001',
   'Bridal makeup – natural glam look',
   'https://images.unsplash.com/photo-1487412947147-5cebf100ffc2?w=400',
   NOW() - INTERVAL '5 days'),

  (gen_random_uuid(),
   '10000000-0000-0000-0000-000000000002',
   'Classic fade with line-up',
   'https://images.unsplash.com/photo-1503951914875-452162b0f3f1?w=400',
   NOW() - INTERVAL '3 days');


-- ── 11. SAVED SALONS (users bookmarking salons) ──────────────

INSERT INTO user_saved_salons (user_id, salon_id) VALUES
  ('00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000001'),
  ('00000000-0000-0000-0000-000000000005', '10000000-0000-0000-0000-000000000002'),
  ('00000000-0000-0000-0000-000000000006', '10000000-0000-0000-0000-000000000001');


-- ── 12. PAYMENT RECORDS ──────────────────────────────────────

INSERT INTO payment_records (id, payment_ref, type, user_id, reference_id,
                             amount, currency, status, created_at, updated_at)
VALUES
  (gen_random_uuid(),
   'PAY-MOCK-BOOKING-001', 'BOOKING',
   '00000000-0000-0000-0000-000000000006',
   '30000000-0000-0000-0000-000000000003',
   65000, 'TND', 'PENDING', NOW(), NOW()),

  (gen_random_uuid(),
   'PAY-MOCK-SUB-001', 'SUBSCRIPTION',
   '00000000-0000-0000-0000-000000000002',
   'MONTHLY',
   29990, 'TND', 'COMPLETED', NOW() - INTERVAL '1 day', NOW() - INTERVAL '1 day');


-- ── 13. WAITLIST ENTRIES ─────────────────────────────────────

INSERT INTO waitlist_entries (id, user_id, salon_id, date, status, created_at)
VALUES
  (gen_random_uuid(),
   '00000000-0000-0000-0000-000000000004',
   '10000000-0000-0000-0000-000000000002',
   CURRENT_DATE + 1, 'WAITING', NOW());


-- ── 14. JOIN REQUESTS ────────────────────────────────────────

INSERT INTO join_requests (id, salon_id, user_id, user_name, user_email,
                           message, status, created_at)
VALUES
  (gen_random_uuid(),
   '10000000-0000-0000-0000-000000000001',
   '00000000-0000-0000-0000-000000000004',
   'Aymen Trabelsi', 'aymen@looki.tn',
   'I am an experienced stylist looking to join your team.',
   'PENDING', NOW());
