CREATE TABLE users (
user_id BIGINT AUTO_INCREMENT PRIMARY KEY,

full_name VARCHAR(100) NOT NULL,
email VARCHAR(150) NOT NULL UNIQUE,

password_hash VARCHAR(255) NOT NULL,

phone_number VARCHAR(20),

                       role ENUM(
        'ROLE_GUEST',
        'ROLE_USER',
        'ROLE_HOTEL_PARTNER',
        'ROLE_GUIDE_PARTNER',
        'ROLE_SUPER_ADMIN'
    ) NOT NULL DEFAULT 'ROLE_USER',

                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                       updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                           ON UPDATE CURRENT_TIMESTAMP,

                       INDEX idx_users_email (email),
                       INDEX idx_users_role (role)
);