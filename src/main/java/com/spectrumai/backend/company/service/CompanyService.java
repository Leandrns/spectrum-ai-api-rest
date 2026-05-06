package com.spectrumai.backend.company.service;

import com.spectrumai.backend.company.model.Company;

import java.util.UUID;

public interface CompanyService {

    Company getById(UUID id);

    Company create(String name, String taxId);
}
