CREATE TABLE room_inventory_daily (
                                      inventory_id BIGINT AUTO_INCREMENT PRIMARY KEY,

                                      room_type_id BIGINT NOT NULL,

                                      inventory_date DATE NOT NULL,

                                      total_inventory INT NOT NULL,

                                      reserved_inventory INT NOT NULL DEFAULT 0,

                                      available_inventory INT NOT NULL,

                                      created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

                                      updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                                          ON UPDATE CURRENT_TIMESTAMP,

                                      CONSTRAINT chk_inventory_total
                                          CHECK (total_inventory >= 0),

                                      CONSTRAINT chk_inventory_reserved
                                          CHECK (reserved_inventory >= 0),

                                      CONSTRAINT chk_inventory_available
                                          CHECK (available_inventory >= 0),

                                      CONSTRAINT chk_inventory_consistency
                                          CHECK (
                                              available_inventory + reserved_inventory = total_inventory
                                              ),

                                      CONSTRAINT fk_inventory_room_type
                                          FOREIGN KEY (room_type_id)
                                              REFERENCES room_types(room_type_id)
                                              ON DELETE CASCADE,

                                      UNIQUE KEY uq_room_inventory_date (
                                                                         room_type_id,
                                                                         inventory_date
                                          ),

                                      INDEX idx_inventory_date (inventory_date),
                                      INDEX idx_inventory_room_date (
                                                                     room_type_id,
                                                                     inventory_date
                                          )
);