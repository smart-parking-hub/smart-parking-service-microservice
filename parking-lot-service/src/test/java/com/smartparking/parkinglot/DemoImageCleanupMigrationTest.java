package com.smartparking.parkinglot;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DemoImageCleanupMigrationTest {
    private static final ClassPathResource CLEANUP = new ClassPathResource(
            "db/migration/V12__remove_unbacked_demo_image_metadata.sql");

    private Connection seededDatabase() throws Exception {
        var connection = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL", "sa", "");
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V1__init_parkinglot_schema.sql"));
        try (var statement = connection.createStatement()) {
            // V10's MySQL multi-clause ALTER is not supported by H2; create its
            // image_url prerequisite without changing the released migration.
            statement.executeUpdate("ALTER TABLE parking_lot_images ADD COLUMN image_url VARCHAR(500)");
            for (long id : new long[]{1, 2, 4, 5, 8, 12, 16, 24}) {
                statement.executeUpdate("INSERT INTO parking_lots (id, merchant_id, name, address, latitude, longitude) "
                        + "VALUES (" + id + ", 4, 'Migration lot', 'Address', 10.75, 106.65)");
            }
        }
        String seed = new ClassPathResource("db/migration/V2__seed_demo_data.sql").getContentAsString(StandardCharsets.UTF_8);
        String images = seed.substring(seed.indexOf("INSERT IGNORE INTO parking_lot_images"));
        ScriptUtils.executeSqlScript(connection, new ByteArrayResource(images.getBytes(StandardCharsets.UTF_8)));
        return connection;
    }

    private int imageCount(Connection connection) throws Exception {
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT COUNT(*) FROM parking_lot_images")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    @Test
    void removesOnlyTheEightOriginalMetadataPlaceholdersAndIsIdempotent() throws Exception {
        try (var connection = seededDatabase()) {
            assertEquals(8, imageCount(connection));
            ScriptUtils.executeSqlScript(connection, CLEANUP);
            assertEquals(0, imageCount(connection));
            ScriptUtils.executeSqlScript(connection, CLEANUP);
            assertEquals(0, imageCount(connection));
        }
    }

    @Test
    void preservesCdnUploadsAndEditedSeedRecords() throws Exception {
        try (var connection = seededDatabase(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE parking_lot_images SET image_url = 'https://cdn.example.invalid/real.webp' WHERE id = 1");
            statement.executeUpdate("UPDATE parking_lot_images SET stored_file_name = 'uploaded.webp' WHERE id = 2");
            statement.executeUpdate("UPDATE parking_lot_images SET original_file_name = 'actual.jpg' WHERE id = 3");
            statement.executeUpdate("UPDATE parking_lot_images SET size_bytes = 12345 WHERE id = 4");
            statement.executeUpdate("UPDATE parking_lot_images SET content_type = 'image/jpeg' WHERE id = 5");
            statement.executeUpdate("UPDATE parking_lot_images SET updated_at = '2026-11-21 10:00:00' WHERE id = 6");
            statement.executeUpdate("UPDATE parking_lot_images SET is_cover = FALSE WHERE id = 7");
            statement.executeUpdate("UPDATE parking_lot_images SET created_at = '2026-11-21 10:00:00' WHERE id = 8");
            statement.executeUpdate("INSERT INTO parking_lot_images (id, parking_lot_id, stored_file_name, original_file_name, content_type, size_bytes, is_cover) "
                    + "VALUES (99, 1, 'real-upload.webp', 'real.jpg', 'image/webp', 12345, TRUE)");
            ScriptUtils.executeSqlScript(connection, CLEANUP);
            assertEquals(9, imageCount(connection));
        }
    }
}
