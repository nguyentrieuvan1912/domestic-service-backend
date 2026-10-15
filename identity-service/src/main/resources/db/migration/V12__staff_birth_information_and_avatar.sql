ALTER TABLE staffs ADD COLUMN birth_year SMALLINT CHECK (birth_year BETWEEN 1940 AND 2100);

UPDATE staffs SET birth_year=extract(year FROM date_of_birth)::int WHERE date_of_birth IS NOT NULL;
-- Preserve the precision actually recorded by older recruitment forms.
UPDATE staffs s SET birth_year=(c.payload::jsonb->>'birthYear')::int
FROM staff_candidates c WHERE c.staff_id=s.id AND s.birth_year IS NULL
  AND c.payload::jsonb->>'birthYear' ~ '^[0-9]{4}$'
  AND (c.payload::jsonb->>'birthYear')::int BETWEEN 1940 AND 2100;

UPDATE users SET avatar_url='/api/v1/identity/staff-avatar.png'
WHERE role='STAFF' AND coalesce(btrim(avatar_url),'')='';
