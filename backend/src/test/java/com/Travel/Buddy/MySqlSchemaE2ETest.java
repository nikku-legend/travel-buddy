package com.Travel.Buddy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles; 
import javax.sql.DataSource;

import java.net.URL;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The gate every other MySQL test depends on. (FR-20..FR-28)
 *
 * <p>The default test profile runs on H2 with Flyway disabled, so the
 * whole migration chain has never been executed against the database
 * it is written for. A migration that only fails on MySQL -- a wrong
 * ENUM spelling, a missing index that a concurrent write needs, an
 * ALTER that assumes a column a later migration renamed -- passes
 * every test in the suite and breaks the first real deployment.
 *
 * <p>This boots the real application against MySQL 8. Spring runs
 * Flyway during startup, and {@code ddl-auto=validate} then checks every
 * entity against the migrated schema. So a green run here means every
 * migration applied in order AND the entities still match.
 *
 * <p>Separate database from the developer's own, so a failed run cannot
 * leave the local data half-migrated.
 *
 * <p><b>Opt-in on purpose.</b> Surefire's default includes match this
 * class ({@code *Test}), so without a guard a contributor with no MySQL
 * would see their build fail on a connection error. The gate below
 * skips the class before Spring starts, so no context and no
 * connection are attempted.
 *
 * <p>Run it with:
 * <pre>
 *   mvn test -Dtravelbuddy.mysql.e2e=true -Dtest=MySqlSchemaE2ETest
 * </pre>
 */
@EnabledIfSystemProperty(
        named = "travelbuddy.mysql.e2e",
        matches = "true"
)
@SpringBootTest
@ActiveProfiles("mysql-e2e")
@DisplayName("Real MySQL schema (migrations + entity mapping)")
class MySqlSchemaE2ETest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("Flyway applies every migration to real MySQL")
    void migrationsApplyCleanly() {
        Integer failed =
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM flyway_schema_history "
                                + "WHERE success = 0",
                        Integer.class
                );

        assertNotNull(failed, "flyway_schema_history must exist");
        assertTrue(failed == 0, "no migration may be recorded as failed");
    }

    /**
     * The highest migration number on the classpath.
     *
     * <p>Derived rather than hardcoded. This assertion used to say
     * "52", which meant adding V53 failed the suite until someone
     * remembered to edit a number -- a test that punishes doing the
     * job rather than checking it. It also silently stopped being a
     * guard: the moment a migration was added and the literal was not
     * bumped, the only signal was a red build unrelated to the real
     * question, so people learn to ignore it.
     *
     * <p>Now the expectation follows the migrations themselves, so it
     * still fails when Flyway is behind but needs no edit when a
     * migration is correctly added.
     */
    private int highestMigrationOnClasspath() throws Exception {
        URL root = getClass().getResource("/db/migration");

        assertNotNull(
                root,
                "migrations must be on the test classpath"
        );

        try (java.util.stream.Stream<java.nio.file.Path> files =
                     java.nio.file.Files.list(
                             java.nio.file.Path.of(root.toURI()))) {

            return files
                    .map(path -> path.getFileName().toString())
                    .map(name -> {
                        if (!name.startsWith("V")
                                || !name.contains("__")) {

                            return null;
                        }

                        String digits = name.substring(
                                1,
                                name.indexOf("__")
                        );

                        try {
                            return Integer.valueOf(digits);
                        } catch (NumberFormatException ex) {
                            return null;
                        }
                    })
                    .filter(java.util.Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0);
        }
    }

    @Test
    @DisplayName("Flyway reached the latest migration version")
    void migrationsAreUpToDate() throws Exception {
        String latest = jdbc.queryForObject(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success = 1 ORDER BY installed_rank DESC LIMIT 1",
                String.class
        );

        int expected = highestMigrationOnClasspath();

        assertNotNull(latest, "no successful migration is recorded");
        assertEquals(
                expected,
                Integer.parseInt(latest),
                "expected the chain to reach V" + expected
                        + " but latest applied was " + latest
        );
    }

    @Test
    @DisplayName("The tables the partner portals depend on exist")
    void partnerTablesExist() {
        /*
         * These are the tables the hotel and cab portals read. If a
         * migration stopped creating one, every other test would fail
         * with a confusing "table not found" from deep inside JPA; this
         * names the missing piece instead.
         */
        String[] required = {
                "properties", "room_types", "physical_rooms", "room_stays",
                "hotel_reservations", "bookings", "room_inventory_daily",
                "cabs", "cab_rides", "booking_vouchers",
        };

        for (String table : required) {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name = ?",
                    Integer.class,
                    table
            );

            assertTrue(
                    count != null && count == 1,
                    "expected table " + table + " to exist on MySQL"
            );
        }
    }
}