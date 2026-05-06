package com.spectrumai.backend.company.controller;

import com.spectrumai.backend.company.service.CompanyService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Companies", description = "Gestão de empresas (tenants)")
@RestController
@RequestMapping("/v1/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
}
