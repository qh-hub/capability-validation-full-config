package com.example.capabilityvalidation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 配置类，用于加载和管理能力校验规则。
 * 该类通过读取前缀为 "app-access-application" 的配置属性，
 * 将其映射到内部的能力定义结构中，支持根据类型获取对应的能力定义信息。
 */
@Component
@ConfigurationProperties(prefix = "app-access-application")
public class CapabilityRuleConfig {

    /**
     * 存储所有能力定义的列表
     */
    private List<CapabilityDefinition> capabilities = new ArrayList<>();

    /**
     * 获取所有能力定义列表
     *
     * @return 能力定义列表
     */
    public List<CapabilityDefinition> getCapabilities() {
        return capabilities;
    }

    /**
     * 设置能力定义列表
     *
     * @param capabilities 要设置的能力定义列表
     */
    public void setCapabilities(List<CapabilityDefinition> capabilities) {
        this.capabilities = capabilities;
    }

    /**
     * 根据类型查找对应的能力定义
     *
     * @param type 能力类型标识符
     * @return 匹配的能力定义对象；如果未找到则返回 null
     */
    public CapabilityDefinition getCapabilityDefinition(String type) {
        return capabilities.stream()
                .filter(c -> c.getType().equals(type))
                .findFirst()
                .orElse(null);
    }

    /**
     * 表示一个具体的能力定义，包括其依赖、字段规则以及自定义验证器等信息
     */
    public static class CapabilityDefinition {
        /**
         * 能力类型标识
         */
        private String type;

        /**
         * 当前能力所依赖的基础能力列表
         */
        private List<String> dependencies = new ArrayList<>();

        /**
         * 字段级别的规则集合
         */
        private List<FieldRule> fieldRules = new ArrayList<>();

        /**
         * 自定义验证器类名（可选）
         */
        private String customValidator;

        /**
         * 条件性依赖列表（新增），表示在特定条件下才需要满足的额外能力要求
         */
        private List<ConditionalDependency> conditionalDependencies;

        /**
         * 获取能力类型标识
         *
         * @return 类型字符串
         */
        public String getType() { return type; }

        /**
         * 设置能力类型标识
         *
         * @param type 类型字符串
         */
        public void setType(String type) { this.type = type; }

        /**
         * 获取基础依赖列表
         *
         * @return 依赖项列表
         */
        public List<String> getDependencies() { return dependencies; }

        /**
         * 设置基础依赖列表
         *
         * @param dependencies 依赖项列表
         */
        public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

        /**
         * 获取字段规则列表
         *
         * @return 字段规则列表
         */
        public List<FieldRule> getFieldRules() { return fieldRules; }

        /**
         * 设置字段规则列表
         *
         * @param fieldRules 字段规则列表
         */
        public void setFieldRules(List<FieldRule> fieldRules) { this.fieldRules = fieldRules; }

        /**
         * 获取自定义验证器类名
         *
         * @return 验证器类名或 null
         */
        public String getCustomValidator() {
            return customValidator;
        }

        /**
         * 设置自定义验证器类名
         *
         * @param customValidator 验证器类名
         */
        public void setCustomValidator(String customValidator) {
            this.customValidator = customValidator;
        }

        /**
         * 获取条件依赖列表
         *
         * @return 条件依赖列表
         */
        public List<ConditionalDependency> getConditionalDependencies() {
            return conditionalDependencies;
        }

        /**
         * 设置条件依赖列表
         *
         * @param conditionalDependencies 条件依赖列表
         */
        public void setConditionalDependencies(List<ConditionalDependency> conditionalDependencies) {
            this.conditionalDependencies = conditionalDependencies;
        }
    }

    /**
     * 定义某个字段是否满足某种预期值时所需的必填字段规则
     */
    public static class FieldRule {
        /**
         * 判断条件基于的字段名称（可以为空）
         */
        private String conditionField;

        /**
         * 期望的字段值（可以为空）
         */
        private Object expectedValue;

        /**
         * 在满足上述条件时必须填写的字段列表
         */
        private List<String> requiredFields = new ArrayList<>();

        /**
         * 获取判断条件字段名
         *
         * @return 字段名或 null
         */
        public String getConditionField() { return conditionField; }

        /**
         * 设置判断条件字段名
         *
         * @param conditionField 字段名
         */
        public void setConditionField(String conditionField) { this.conditionField = conditionField; }

        /**
         * 获取期望值
         *
         * @return 期望值对象或 null
         */
        public Object getExpectedValue() { return expectedValue; }

        /**
         * 设置期望值
         *
         * @param expectedValue 期望值对象
         */
        public void setExpectedValue(Object expectedValue) { this.expectedValue = expectedValue; }

        /**
         * 获取所需字段列表
         *
         * @return 必须填写的字段列表
         */
        public List<String> getRequiredFields() { return requiredFields; }

        /**
         * 设置所需字段列表
         *
         * @param requiredFields 必须填写的字段列表
         */
        public void setRequiredFields(List<String> requiredFields) { this.requiredFields = requiredFields; }
    }

    /**
     * 描述一种带条件的能力依赖关系，在指定字段等于某值时才需具备某些能力
     */
    public static class ConditionalDependency {
        /**
         * 触发条件的字段名，如 "platform"
         */
        private String conditionField;

        /**
         * 所需匹配的字段值，如 "OSS"
         */
        private Object expectedValue;

        /**
         * 满足条件后所需的能力列表，例如 ["resource", "nacos"]
         */
        private List<String> requiredCapabilities;

        /**
         * 获取触发条件的字段名
         *
         * @return 字段名
         */
        public String getConditionField() {
            return conditionField;
        }

        /**
         * 设置触发条件的字段名
         *
         * @param conditionField 字段名
         */
        public void setConditionField(String conditionField) {
            this.conditionField = conditionField;
        }

        /**
         * 获取期望值
         *
         * @return 值对象
         */
        public Object getExpectedValue() {
            return expectedValue;
        }

        /**
         * 设置期望值
         *
         * @param expectedValue 值对象
         */
        public void setExpectedValue(Object expectedValue) {
            this.expectedValue = expectedValue;
        }

        /**
         * 获取所需能力列表
         *
         * @return 能力标识符列表
         */
        public List<String> getRequiredCapabilities() {
            return requiredCapabilities;
        }

        /**
         * 设置所需能力列表
         *
         * @param requiredCapabilities 能力标识符列表
         */
        public void setRequiredCapabilities(List<String> requiredCapabilities) {
            this.requiredCapabilities = requiredCapabilities;
        }
    }
}
