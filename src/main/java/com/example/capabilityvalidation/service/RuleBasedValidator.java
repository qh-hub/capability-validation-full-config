package com.example.capabilityvalidation.service;

import com.example.capabilityvalidation.config.CapabilityRuleConfig;
import com.example.capabilityvalidation.dto.SystemApplicationDTO;
import com.example.capabilityvalidation.exception.ValidationException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RuleBasedValidator {

    @Autowired
    private CapabilityRuleConfig ruleConfig;

    @Autowired
    private ApplicationContext applicationContext;

    private Map<String, CustomFieldValidator> customValidatorMap;

    @PostConstruct
    public void initCustomValidators() {
        customValidatorMap = applicationContext.getBeansOfType(CustomFieldValidator.class);
    }

    public void validate(SystemApplicationDTO dto) {
        List<String> selectedCaps = dto.getCapabilities();
        if (selectedCaps == null || selectedCaps.isEmpty()) {
            throw new ValidationException("至少选择一个分布式平台能力");
        }

        Set<String> selectedSet = new HashSet<>(selectedCaps);

        // 构建规则 Map
        Map<String, CapabilityRuleConfig.CapabilityDefinition> ruleMap = ruleConfig.getCapabilities().stream()
                .collect(Collectors.toMap(CapabilityRuleConfig.CapabilityDefinition::getType, r -> r));

        // === 第一步：能力依赖校验 ===
        for (String cap : selectedCaps) {
            var def = ruleMap.get(cap);
            if (def == null) {
                throw new ValidationException("不支持的能力类型: " + cap);
            }
            for (String dep : def.getDependencies()) {
                if (!selectedSet.contains(dep)) {
                    throw new ValidationException(
                        String.format("启用能力 [%s] 前，必须先启用依赖能力 [%s]", cap, dep)
                    );
                }
            }
        }

        // === 第二步：字段级规则校验 ===
        Map<String, Object> configData = dto.getConfigData() != null ? dto.getConfigData() : Collections.emptyMap();

        for (String cap : selectedCaps) {
            if (!ruleMap.containsKey(cap)) continue;

            Object configBlockObj = configData.get(cap);
            if (configBlockObj == null) {
                // 如果该能力有字段规则，则必须提供 configData
                if (!ruleMap.get(cap).getFieldRules().isEmpty()) {
                    throw new ValidationException("能力 [" + cap + "] 缺少配置数据");
                }
                continue;
            }

            if (!(configBlockObj instanceof Map)) {
                throw new ValidationException("能力 [" + cap + "] 的配置数据格式错误");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> configBlock = (Map<String, Object>) configBlockObj;
            validateFieldRules(cap, configBlock, ruleMap.get(cap));
        }
    }

    private void validateFieldRules(String capabilityType,
                                    Map<String, Object> configBlock,
                                    CapabilityRuleConfig.CapabilityDefinition capDef) {
        for (var rule : capDef.getFieldRules()) {
            // 判断是否为“无条件必填”规则
            if (rule.getConditionField() == null || rule.getConditionField().isEmpty()) {
                // 无条件：直接校验 required_fields
                for (String field : rule.getRequiredFields()) {
                    if (!hasNonBlankValue(configBlock, field)) {
                        throw new ValidationException(
                                String.format("能力 [%s] 的字段 [%s] 为必填项", capabilityType, field)
                        );
                    }
                }
            } else {
                // 有条件：按原逻辑校验
                Object actualValue = configBlock.get(rule.getConditionField());
                if (Objects.equals(actualValue, rule.getExpectedValue())) {
                    for (String field : rule.getRequiredFields()) {
                        if (!hasNonBlankValue(configBlock, field)) {
                            throw new ValidationException(
                                    String.format("能力 [%s] 启用了 [%s]，请填写 [%s]",
                                            capabilityType, rule.getConditionField(), field)
                            );
                        }
                    }
                }
            }
        }
    }

    // 辅助方法：判断字段是否存在且非空（支持 String / 非空对象）
    private boolean hasNonBlankValue(Map<String, Object> map, String field) {
        Object val = map.get(field);
        if (val == null) return false;
        if (val instanceof String) return !((String) val).isBlank();
        if (val instanceof List) return !CollectionUtils.isEmpty((List)val);
        return true; // 其他类型（如数字、布尔值）只要存在即视为有效
    }
}
