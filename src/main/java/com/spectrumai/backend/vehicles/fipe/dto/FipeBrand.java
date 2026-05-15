package com.spectrumai.backend.vehicles.fipe.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FipeBrand(String code, String name) {}
