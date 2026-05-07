package com.spectrumai.backend.company.service;

import com.spectrumai.backend.company.model.Company;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Stub temporário — substituir por implementação real quando o domínio
 * de empresas exigir endpoints próprios.
 */
@Service
public class CompanyServiceImpl implements CompanyService {

    @Override
    public Company getById(UUID id) {
        throw new UnsupportedOperationException("CompanyService.getById ainda não implementado");
    }

    @Override
    public Company create(String name, String taxId) {
        throw new UnsupportedOperationException("CompanyService.create ainda não implementado");
    }
}
