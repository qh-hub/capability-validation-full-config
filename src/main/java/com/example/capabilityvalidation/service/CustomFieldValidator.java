package com.example.capabilityvalidation.service;

import java.util.Map;

@FunctionalInterface
public interface CustomFieldValidator {
    void validate(String capabilityType, Map<String, Object> configData);
}