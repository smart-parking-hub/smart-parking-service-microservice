package com.smartparking.identity.application.service.verification;

import java.time.Instant;

public interface BusinessTaxRegistry {
    boolean isEnabled();

    Result lookup(String taxCode);

    enum Outcome { FOUND, NOT_FOUND, UNAVAILABLE }

    record Business(String taxCode, String name, String status, String source, boolean trial) {}

    record Result(Outcome outcome, Business business, Instant retryAt) {
        public static Result found(Business business) {
            return new Result(Outcome.FOUND, business, null);
        }

        public static Result notFound() {
            return new Result(Outcome.NOT_FOUND, null, null);
        }

        public static Result unavailable(Instant retryAt) {
            return new Result(Outcome.UNAVAILABLE, null, retryAt);
        }
    }
}
