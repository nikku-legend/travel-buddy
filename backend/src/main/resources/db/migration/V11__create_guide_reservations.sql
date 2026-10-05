CREATE TABLE guide_reservations (
                                    guide_reservation_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                    booking_id BIGINT NOT NULL,

                                    guide_id BIGINT NOT NULL,

                                    tour_date DATE NOT NULL,

                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT fk_guide_reservations_booking
                                        FOREIGN KEY (booking_id)
                                            REFERENCES bookings(booking_id)
                                            ON DELETE CASCADE,

                                    CONSTRAINT fk_guide_reservations_guide
                                        FOREIGN KEY (guide_id)
                                            REFERENCES guides(guide_id)
                                            ON DELETE RESTRICT,

                                    INDEX idx_guide_reservations_booking (booking_id),
                                    INDEX idx_guide_reservations_guide (guide_id),
                                    INDEX idx_guide_reservations_date (tour_date)
);