package com.spectrumai.backend.vehicles.fipe.client;

public class FipeApiException extends RuntimeException {

    public FipeApiException(String message) {
        super(message);
    }

    public FipeApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
