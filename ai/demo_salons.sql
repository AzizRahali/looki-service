-- Demo salons for trying smart search locally. Safe to re-run: it replaces
-- only salons whose name starts with "[Demo]".
--   Get-Content ai\demo_salons.sql | docker exec -i looki-postgres psql -U loomea -d loomea

BEGIN;

CREATE TEMP TABLE demo_ids ON COMMIT DROP AS
    SELECT id FROM salons WHERE name LIKE '[Demo]%';
DELETE FROM salon_services WHERE salon_id IN (SELECT id FROM demo_ids);
DELETE FROM working_hours  WHERE salon_id IN (SELECT id FROM demo_ids);
DELETE FROM salon_tags     WHERE salon_id IN (SELECT id FROM demo_ids);
DELETE FROM salons         WHERE id       IN (SELECT id FROM demo_ids);

INSERT INTO salons (id, name, description, address, phone, latitude, longitude, rating, review_count, opening_hours) VALUES
 ('d0000000-0000-4000-8000-000000000001', '[Demo] Salon Yasmine', 'Coiffure et soins pour femmes.',
  '45 Avenue Habib Bourguiba, Tunis', '+216 71 000 001', 36.8008, 10.1800, 4.7, 128, '9AM - 7PM'),
 ('d0000000-0000-4000-8000-000000000002', '[Demo] Barber Ali', 'Barbier traditionnel et moderne.',
  '12 Rue de Marseille, Tunis', '+216 71 000 002', 36.7990, 10.1820, 4.5, 86, '10AM - 9PM'),
 ('d0000000-0000-4000-8000-000000000003', '[Demo] Marsa Beauty', 'Ongles, sourcils et beauté.',
  '8 Rue du Saf Saf, La Marsa', '+216 71 000 003', 36.8782, 10.3247, 4.8, 203, '9AM - 6PM'),
 ('d0000000-0000-4000-8000-000000000004', '[Demo] Sousse Spa', 'Massages et soins du visage.',
  'Boulevard du 14 Janvier, Sousse', '+216 73 000 004', 35.8378, 10.6310, 4.6, 154, '10AM - 8PM'),
 ('d0000000-0000-4000-8000-000000000005', '[Demo] Hammamet Wellness', 'Spa et bien-être en bord de mer.',
  'Avenue de la Paix, Hammamet', '+216 72 000 005', 36.4000, 10.6167, 4.4, 71, '9AM - 7PM');

INSERT INTO salon_services (id, salon_id, name, category, price, duration_minutes, icon) VALUES
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000001', 'Haircut (Women''s)', 'Hair', 35, 45, 'content_cut'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000001', 'Blowout / Blowdry', 'Hair', 25, 30, 'air'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000001', 'Hair Coloring', 'Hair', 90, 90, 'palette'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000001', 'Manicure', 'Nails', 20, 30, 'brush'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000002', 'Haircut (Men''s)', 'Hair', 15, 30, 'content_cut'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000002', 'Beard Trim', 'Beard & Shave', 10, 20, 'content_cut'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000003', 'Manicure', 'Nails', 25, 30, 'brush'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000003', 'Gel Manicure', 'Nails', 40, 45, 'auto_awesome'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000003', 'Pedicure', 'Nails', 30, 45, 'brush'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000003', 'Eyebrow Shaping', 'Skin & Face', 12, 15, 'face'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000004', 'Swedish Massage', 'Massage', 70, 60, 'self_improvement'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000004', 'Hot Stone Massage', 'Massage', 110, 75, 'local_fire_department'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000004', 'Facial (Basic)', 'Skin & Face', 50, 45, 'face'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000005', 'Swedish Massage', 'Massage', 85, 60, 'self_improvement'),
 (gen_random_uuid(), 'd0000000-0000-4000-8000-000000000005', 'Body Scrub', 'Body Treatments', 60, 45, 'bubble_chart');

-- day_of_week: 0 = Monday ... 6 = Sunday
INSERT INTO working_hours (id, salon_id, day_of_week, open_time, close_time, closed)
SELECT gen_random_uuid(), s.id, d.day,
       CASE WHEN s.closed_day = d.day THEN NULL ELSE s.open_time END,
       CASE WHEN s.closed_day = d.day THEN NULL ELSE s.close_time END,
       s.closed_day = d.day
FROM (VALUES
        ('d0000000-0000-4000-8000-000000000001'::uuid, TIME '09:00', TIME '19:00', 6),
        ('d0000000-0000-4000-8000-000000000002'::uuid, TIME '10:00', TIME '21:00', -1),
        ('d0000000-0000-4000-8000-000000000003'::uuid, TIME '09:00', TIME '18:00', 0),
        ('d0000000-0000-4000-8000-000000000004'::uuid, TIME '10:00', TIME '20:00', -1),
        ('d0000000-0000-4000-8000-000000000005'::uuid, TIME '09:00', TIME '19:00', -1)
     ) AS s(id, open_time, close_time, closed_day)
CROSS JOIN generate_series(0, 6) AS d(day);

COMMIT;
