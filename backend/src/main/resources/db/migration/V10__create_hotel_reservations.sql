CREATE TABLE hotel_reservations (
                                    reservation_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                    booking_id BIGINT NOT NULL,

                                    room_type_id BIGINT NOT NULL,

                                    check_in DATE NOT NULL,

                                    check_out DATE NOT NULL,

                                    rooms_booked INT NOT NULL,

                                    assigned_room_numbers VARCHAR(500),

                                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                    CONSTRAINT chk_hotel_dates
                                        CHECK (check_out > check_in),

                                    CONSTRAINT chk_hotel_rooms
                                        CHECK (rooms_booked > 0),

                                    CONSTRAINT fk_hotel_reservations_booking
                                        FOREIGN KEY (booking_id)
                                            REFERENCES bookings(booking_id)
                                            ON DELETE CASCADE,

                                    CONSTRAINT fk_hotel_reservations_room_type
                                        FOREIGN KEY (room_type_id)
                                            REFERENCES room_types(room_type_id)
                                            ON DELETE RESTRICT,

                                    INDEX idx_hotel_reservations_booking (booking_id),
                                    INDEX idx_hotel_reservations_room_type (room_type_id),
                                    INDEX idx_hotel_reservations_dates (check_in, check_out)
);