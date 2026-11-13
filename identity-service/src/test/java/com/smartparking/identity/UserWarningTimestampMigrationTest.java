package com.smartparking.identity;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class UserWarningTimestampMigrationTest {
    @Test void addsInheritedTimestampWithoutChangingExistingWarnings() throws Exception {
        try (var c = DriverManager.getConnection("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";MODE=MySQL", "sa", "")) {
            c.createStatement().execute("CREATE TABLE user_warnings (id BIGINT PRIMARY KEY, reason VARCHAR(1000), created_at TIMESTAMP)");
            c.createStatement().execute("INSERT INTO user_warnings VALUES (1,'existing warning',CURRENT_TIMESTAMP)");
            ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V6__add_updated_at_to_user_warnings.sql"));
            var r = c.createStatement().executeQuery("SELECT reason,created_at,updated_at FROM user_warnings WHERE id=1");
            assertTrue(r.next());
            assertEquals("existing warning", r.getString(1));
            assertNotNull(r.getTimestamp(2));
            assertNotNull(r.getTimestamp(3));
        }
    }
}
