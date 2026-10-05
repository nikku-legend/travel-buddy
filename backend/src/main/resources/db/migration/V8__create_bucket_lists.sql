CREATE TABLE bucket_lists (
                              bucket_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                              user_id BIGINT NOT NULL,

                              place_id BIGINT NOT NULL,

                              added_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                              CONSTRAINT fk_bucket_lists_user
                                  FOREIGN KEY (user_id)
                                      REFERENCES users(user_id)
                                      ON DELETE CASCADE,

                              CONSTRAINT fk_bucket_lists_place
                                  FOREIGN KEY (place_id)
                                      REFERENCES tourist_places(place_id)
                                      ON DELETE CASCADE,

                              UNIQUE KEY uq_user_place (user_id, place_id),

                              INDEX idx_bucket_lists_user (user_id),
                              INDEX idx_bucket_lists_place (place_id)
);