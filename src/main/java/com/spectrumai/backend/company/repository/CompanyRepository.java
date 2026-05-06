package com.spectrumai.backend.company.repository;

import com.spectrumai.backend.company.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
}
