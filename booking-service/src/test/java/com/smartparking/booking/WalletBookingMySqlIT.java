package com.smartparking.booking;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="BOOKING_WALLET_MYSQL_IT_URL",matches=".+")
class WalletBookingMySqlIT {
    @Test void upgradesV9ToV10WithoutChangingBookingsAndAddsUniqueReceipts() throws Exception {
        String url=System.getenv("BOOKING_WALLET_MYSQL_IT_URL");
        String user=System.getenv().getOrDefault("DRIVER_WALLET_MYSQL_IT_USER","root");
        String password=System.getenv().getOrDefault("DRIVER_WALLET_MYSQL_IT_PASSWORD","");
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")) {
            r.next();assertEquals(0,r.getInt(1),"Requires an explicitly supplied empty test database");
        }
        Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").target("9").load().migrate();
        int before;
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM bookings")) { r.next();before=r.getInt(1); }
        Flyway.configure().dataSource(url,user,password).locations("classpath:db/migration").load().migrate();
        try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {
            try(var r=s.executeQuery("SELECT COUNT(*) FROM bookings WHERE refund_channel IS NULL")) {r.next();assertEquals(before,r.getInt(1));}
            // Receipt payment IDs are external references, never cross-database foreign keys.
            s.executeUpdate("INSERT INTO wallet_payment_receipts(payment_id,booking_id,accepted,created_at) VALUES(71,1,TRUE,CURRENT_TIMESTAMP)");
            assertThrows(SQLException.class,()->s.executeUpdate("INSERT INTO wallet_payment_receipts(payment_id,booking_id,accepted,created_at) VALUES(71,1,TRUE,CURRENT_TIMESTAMP)"));
        }
    }
}
