package com.spectrumai.backend.vehicles.fipe.service;

public class ImportCancelledException extends RuntimeException {
    ImportCancelledException() {
        super("Import cancelado pelo operador.");
    }
}
