package com.smartparking.booking.infrastructure.client;

import feign.*;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import java.io.IOException;
import java.io.UncheckedIOException;

/** Preserve HTTP 4xx for the existing domain-error decoder; protect transport/5xx failures. */
public final class ParkingLotResilienceCapability implements Capability {
    private final CircuitBreaker breaker;
    public ParkingLotResilienceCapability(CircuitBreaker breaker) { this.breaker = breaker; }
    @Override public Client enrich(Client delegate) {
        return (request, options) -> {
            try {
                return breaker.run(() -> {
                    try {
                        Response response = delegate.execute(request, options);
                        if (response.status() >= 500) {
                            var failure = FeignException.errorStatus("parking-lot-rpc", response);
                            response.close();
                            throw failure;
                        }
                        return response;
                    } catch (IOException failure) { throw new UncheckedIOException(failure); }
                }, failure -> {
                    if (failure instanceof RuntimeException runtime) throw runtime;
                    throw new IllegalStateException("Parking service temporarily unavailable", failure);
                });
            } catch (UncheckedIOException failure) { throw failure.getCause(); }
        };
    }
}
