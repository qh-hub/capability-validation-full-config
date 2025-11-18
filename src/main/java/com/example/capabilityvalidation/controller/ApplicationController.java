package com.example.capabilityvalidation.controller;

import com.example.capabilityvalidation.dto.SystemApplicationDTO;
import com.example.capabilityvalidation.exception.ValidationException;
import com.example.capabilityvalidation.service.RuleBasedValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/application")
public class ApplicationController {

    @Autowired
    private RuleBasedValidator validator;

    @PostMapping("/submit")
    public ResponseEntity<String> submit(@RequestBody SystemApplicationDTO dto) {
        try {
            validator.validate(dto);
        } catch (ValidationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        return ResponseEntity.ok("✅ 提交成功！");
    }
}
