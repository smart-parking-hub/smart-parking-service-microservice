package com.smartparking.booking.infrastructure.client;
import feign.*;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.util.Map;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
class ParkingLotResilienceCapabilityTest {
    private final CircuitBreaker breaker=new CircuitBreaker() {
        @Override public <T> T run(Supplier<T> action, Function<Throwable,T> fallback) {
            try {return action.get();}catch(Throwable failure){return fallback.apply(failure);}
        }
    };
    private final Request request=Request.create(Request.HttpMethod.GET,"http://parking.test/availability",Map.of(),null,java.nio.charset.StandardCharsets.UTF_8,null);
    @Test void hasBoundedNetworkTimeoutsAndPreservesConflictResponses() throws Exception {
        var options=new ParkingLotClient.InternalConfig().parkingLotRequestOptions();
        assertEquals(3000,options.connectTimeoutMillis());assertEquals(5000,options.readTimeoutMillis());
        var expected=Response.builder().request(request).status(409).reason("Conflict").headers(Map.of()).build();
        var client=new ParkingLotResilienceCapability(breaker).enrich((Client)(r,o)->expected);
        assertSame(expected,client.execute(request,options));
    }
    @Test void reportsTransportFailuresAndServerErrorsWithoutInventingAvailability() {
        var capability=new ParkingLotResilienceCapability(breaker);var options=new ParkingLotClient.InternalConfig().parkingLotRequestOptions();
        var network=capability.enrich((Client)(r,o)->{throw new IOException("Network unavailable");});
        assertThrows(IOException.class,()->network.execute(request,options));
        var server=capability.enrich((Client)(r,o)->Response.builder().request(request).status(503).reason("Unavailable").headers(Map.of()).build());
        assertThrows(FeignException.class,()->server.execute(request,options));
    }
    public interface Probe { @RequestLine("GET /availability") String available(); }
    @Test void capabilityInstallsInFeignWithoutChangingDomainConflictErrors() {
        var probe=Feign.builder().client((r,o)->Response.builder().request(r).status(409).reason("Conflict").headers(Map.of()).build())
                .addCapability(new ParkingLotResilienceCapability(breaker)).target(Probe.class,"http://parking.test");
        assertThrows(FeignException.Conflict.class,probe::available);
    }
}
