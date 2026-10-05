-- ============================================================
-- MULTI-ROLE RBAC FOUNDATION
--
-- WHY THIS MIGRATION EXISTS
--
-- `users.role` is a single ENUM column, which means one account
-- can only ever hold ONE role. Travel Buddy needs the opposite:
--
--   Ravi
--   ├── Traveler          ROLE_USER
--   ├── Hotel Partner     ROLE_HOTEL_PARTNER
--   └── Cab Partner       ROLE_CAB_PARTNER
--
-- Roles are therefore stored in a separate `roles` table and
-- linked to users through the `user_roles` join table.
--
-- `users.role` is intentionally KEPT as a denormalised
-- "primary role" column so that existing single-role reads keep
-- working while the rest of the platform migrates to `user_roles`.
-- New authorisation decisions must use `user_roles`.
-- ============================================================

CREATE TABLE roles (
                      role_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                      role_name VARCHAR(30) NOT NULL UNIQUE,

                      description VARCHAR(150) NULL,

                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                      INDEX idx_roles_name (role_name)
);

-- ============================================================
-- SEED THE TRAVEL BUDDY ROLE CATALOGUE
-- ============================================================

INSERT INTO roles (role_name, description)
VALUES ('ROLE_USER', 'Traveler - discover, plan and book'),
       ('ROLE_HOTEL_PARTNER', 'Hotel, resort, homestay or lodge owner'),
       ('ROLE_GUIDE_PARTNER', 'Local guide or experience provider'),
       ('ROLE_CAB_PARTNER', 'Driver, taxi operator or vehicle owner'),
       ('ROLE_SUPER_ADMIN', 'Travel Buddy super administrator');


-- ============================================================
-- USER <-> ROLE JOIN TABLE
-- ============================================================

CREATE TABLE user_roles (
                        user_role_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                        user_id BIGINT NOT NULL,

                        role_id BIGINT NOT NULL,

                        -- When the role was granted (partner approval time).
                        granted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        -- Which admin approved the grant. NULL for baseline roles.
                        granted_by_user_id BIGINT NULL,

                        CONSTRAINT fk_user_roles_user
                            FOREIGN KEY (user_id)
                                REFERENCES users(user_id)
                                ON DELETE CASCADE,

                        CONSTRAINT fk_user_roles_role
                            FOREIGN KEY (role_id)
                                REFERENCES roles(role_id)
                                ON DELETE CASCADE,

                        CONSTRAINT fk_user_roles_granted_by
                            FOREIGN KEY (granted_by_user_id)
                                REFERENCES users(user_id)
                                ON DELETE SET NULL,

                        -- A user can hold a given role only once.
                        UNIQUE KEY uq_user_roles_user_role (user_id, role_id),

                        INDEX idx_user_roles_user (user_id),
                        INDEX idx_user_roles_role (role_id)
);


-- ============================================================
-- BACKFILL
--
-- Rule: everyone is at least a Traveler. Partner/admin accounts
-- keep the role they already had, and ALSO keep traveler access
-- so an approved partner can still plan and book their own trips.
-- ============================================================

INSERT INTO user_roles (user_id, role_id)
SELECT u.user_id,
       r.role_id
FROM users u
         JOIN roles r ON r.role_name = 'ROLE_USER';

INSERT IGNORE INTO user_roles (user_id, role_id)
SELECT u.user_id,
       r.role_id
FROM users u
         JOIN roles r ON r.role_name = u.role
WHERE u.role <> 'ROLE_USER';
