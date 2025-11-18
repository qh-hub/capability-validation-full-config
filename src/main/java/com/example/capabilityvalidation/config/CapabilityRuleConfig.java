package com.example.capabilityvalidation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties(prefix = "app-access-application")
public class CapabilityRuleConfig {

    private List<CapabilityDefinition> capabilities = new ArrayList<>();

    public List<CapabilityDefinition> getCapabilities() {
        return capabilities;
    }

    public void setCapabilities(List<CapabilityDefinition> capabilities) {
        this.capabilities = capabilities;
    }

    public CapabilityDefinition getCapabilityDefinition(String type) {
        return capabilities.stream()
                .filter(c -> c.getType().equals(type))
                .findFirst()
                .orElse(null);
    }

    public static class CapabilityDefinition {
        private String type;
        private List<String> dependencies = new ArrayList<>();
        private List<FieldRule> fieldRules = new ArrayList<>();
        private String customValidator;
        // 新增：条件依赖列表
        private List<ConditionalDependency> conditionalDependencies;


        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public List<String> getDependencies() { return dependencies; }
        public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

        public List<FieldRule> getFieldRules() { return fieldRules; }
        public void setFieldRules(List<FieldRule> fieldRules) { this.fieldRules = fieldRules; }

        public String getCustomValidator() {
            return customValidator;
        }

        public void setCustomValidator(String customValidator) {
            this.customValidator = customValidator;
        }

        public List<ConditionalDependency> getConditionalDependencies() {
            return conditionalDependencies;
        }

        public void setConditionalDependencies(List<ConditionalDependency> conditionalDependencies) {
            this.conditionalDependencies = conditionalDependencies;
        }
    }

    public static class FieldRule {
        private String conditionField;      // 可为 null
        private Object expectedValue;       // 可为 null
        private List<String> requiredFields = new ArrayList<>();

        // getters & setters
        public String getConditionField() { return conditionField; }
        public void setConditionField(String conditionField) { this.conditionField = conditionField; }

        public Object getExpectedValue() { return expectedValue; }
        public void setExpectedValue(Object expectedValue) { this.expectedValue = expectedValue; }

        public List<String> getRequiredFields() { return requiredFields; }
        public void setRequiredFields(List<String> requiredFields) { this.requiredFields = requiredFields; }
    }

    public static class ConditionalDependency {
        private String conditionField;       // 如 "platform"
        private Object expectedValue;        // 如 "OSS"
        private List<String> requiredCapabilities; // 如 ["resource", "nacos"]

        public String getConditionField() {
            return conditionField;
        }

        public void setConditionField(String conditionField) {
            this.conditionField = conditionField;
        }

        public Object getExpectedValue() {
            return expectedValue;
        }

        public void setExpectedValue(Object expectedValue) {
            this.expectedValue = expectedValue;
        }

        public List<String> getRequiredCapabilities() {
            return requiredCapabilities;
        }

        public void setRequiredCapabilities(List<String> requiredCapabilities) {
            this.requiredCapabilities = requiredCapabilities;
        }
    }
}
