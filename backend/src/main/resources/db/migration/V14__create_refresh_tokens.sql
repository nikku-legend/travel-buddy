CREATE TABLE refresh_tokens (
                                refresh_token_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                user_id BIGINT NOT NULL,

                                token_hash VARCHAR(255) NOT NULL UNIQUE,

                                expires_at TIMESTAMP NOT NULL,

                                revoked BOOLEAN NOT NULL DEFAULT FALSE,

                                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                revoked_at TIMESTAMP NULL,

                                CONSTRAINT fk_refresh_tokens_user
                                    FOREIGN KEY (user_id)
                                        REFERENCES users(user_id)
                                        ON DELETE CASCADE,

                                INDEX idx_refresh_tokens_user (user_id),
                                INDEX idx_refresh_tokens_expires (expires_at),
                                INDEX idx_refresh_tokens_revoked (revoked)
);