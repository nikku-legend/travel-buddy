CREATE TABLE guides (
                        guide_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                        user_id BIGINT NOT NULL,

                        state_id INT NOT NULL,

                        daily_rate DECIMAL(12,2) NOT NULL,

                        currency_code CHAR(3) NOT NULL DEFAULT 'INR',

                        is_verified BOOLEAN NOT NULL DEFAULT FALSE,

                        is_active BOOLEAN NOT NULL DEFAULT TRUE,

                        created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                            ON UPDATE CURRENT_TIMESTAMP,

                        CONSTRAINT chk_guide_daily_rate
                            CHECK (daily_rate >= 0),

                        CONSTRAINT fk_guides_user
                            FOREIGN KEY (user_id)
                                REFERENCES users(user_id)
                                ON DELETE CASCADE,

                        CONSTRAINT fk_guides_state
                            FOREIGN KEY (state_id)
                                REFERENCES states(state_id)
                                ON DELETE RESTRICT,

                        UNIQUE KEY uq_guides_user (user_id),

                        INDEX idx_guides_state (state_id),
                        INDEX idx_guides_verified (is_verified),
                        INDEX idx_guides_active (is_active)
);