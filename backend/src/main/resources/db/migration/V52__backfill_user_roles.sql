-- =====================================================================
-- V52: every account gets a real user_roles row
-- =====================================================================
--
-- V27 backfilled user_roles from the legacy users.role column, but
-- V50 went on to create the guide and cab partner demo accounts
-- afterwards. Those accounts had ROLE_GUIDE_PARTNER in users.role
-- and nothing at all in user_roles, so they authenticated only
-- because CustomUserDetailsService falls back to the legacy column
-- when user_roles is empty.
--
-- That fallback is a safety net, not a design. It means those
-- accounts are the only ones in the system whose authorities come
-- from a different source than everyone else''s, and the moment a
-- second user_roles row is added to one of them the legacy role is
-- ignored -- silently dropping their partner access.
--
-- So the join table is brought in line with the column it replaced.
-- Safe to re-run: only inserts rows that do not already exist.
--
-- A partner role is also granted to the roles catalogue, because
-- CustomUserDetailsService reads authorities from user_roles and
-- every other partner path (the application workflow) expects the
-- catalogue row to exist.
-- =====================================================================

-- Catalogue entries for the partner roles V50 seeds.
INSERT INTO roles (role_name, description)
SELECT 'ROLE_GUIDE_PARTNER', 'Approved guide partner'
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE role_name = 'ROLE_GUIDE_PARTNER'
);

INSERT INTO roles (role_name, description)
SELECT 'ROLE_CAB_PARTNER', 'Approved cab partner'
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE role_name = 'ROLE_CAB_PARTNER'
);

-- Every traveller is a traveller, whoever created the account.
INSERT INTO user_roles (user_id, role_id, granted_at)
SELECT u.user_id, r.role_id, CURRENT_TIMESTAMP
FROM users u
JOIN roles r ON r.role_name = 'ROLE_USER'
WHERE NOT EXISTS (
    SELECT 1
    FROM user_roles ur
    WHERE ur.user_id = u.user_id
      AND ur.role_id = r.role_id
);

-- ...and keeps whatever partner role the legacy column claims.
INSERT INTO user_roles (user_id, role_id, granted_at)
SELECT u.user_id, r.role_id, CURRENT_TIMESTAMP
FROM users u
JOIN roles r ON r.role_name = u.role
WHERE u.role IN ('ROLE_HOTEL_PARTNER', 'ROLE_GUIDE_PARTNER', 'ROLE_CAB_PARTNER')
  AND NOT EXISTS (
      SELECT 1
      FROM user_roles ur
      WHERE ur.user_id = u.user_id
        AND ur.role_id = r.role_id
  );