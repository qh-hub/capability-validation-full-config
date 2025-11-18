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

/**
 * 基于规则的能力验证服务类。
 * <p>
 * 该类负责根据预定义的能力规则配置，对用户提交的系统应用 DTO 进行合法性校验，
 * 包括能力间的依赖关系检查以及各能力对应字段的规则校验。
 */
@Service
public class RuleBasedValidator {

    @Autowired
    private CapabilityRuleConfig ruleConfig;

    @Autowired
    private ApplicationContext applicationContext;

    private Map<String, CustomFieldValidator> customValidatorMap;

    /**
     * 初始化自定义字段验证器映射表。
     * 在 Spring 容器初始化完成后自动调用，获取所有实现了 {@link CustomFieldValidator} 接口的 Bean 实例。
     */
    @PostConstruct
    public void initCustomValidators() {
        customValidatorMap = applicationContext.getBeansOfType(CustomFieldValidator.class);
    }

    /**
     * 对传入的系统应用 DTO 进行全面的能力规则校验。
     *
     * @param dto 待校验的系统应用数据传输对象，包含所选能力和对应的配置信息
     * @throws ValidationException 当校验失败时抛出异常，携带具体的错误描述信息
     */
    public void validate(SystemApplicationDTO dto) {
        List<String> selectedCaps = dto.getCapabilities();
        if (selectedCaps == null || selectedCaps.isEmpty()) {
            throw new ValidationException("至少选择一个分布式平台能力");
        }

        // 构建规则 Map
        Map<String, CapabilityRuleConfig.CapabilityDefinition> ruleMap = ruleConfig.getCapabilities().stream()
                .collect(Collectors.toMap(CapabilityRuleConfig.CapabilityDefinition::getType, r -> r));

        Map<String, Object> configData = dto.getConfigData() != null ? dto.getConfigData() : Collections.emptyMap();

        // 先收集所有实际需要的能力（静态 + 条件）
        Set<String> requiredCapabilities = new HashSet<>();

        // 1. 添加静态依赖
        for (String cap : selectedCaps) {
            CapabilityRuleConfig.CapabilityDefinition def = ruleMap.get(cap);
            if (def == null) continue;
            requiredCapabilities.addAll(def.getDependencies());
        }

        // 2. 检查条件依赖
        for (String cap : selectedCaps) {
            CapabilityRuleConfig.CapabilityDefinition def = ruleMap.get(cap);
            if (def == null || def.getConditionalDependencies() == null) continue;

            Map<String, Object> capConfig = Optional.ofNullable(configData)
                    .map(m -> (Map<String, Object>) m.get(cap))
                    .orElse(Collections.emptyMap());

            for (CapabilityRuleConfig.ConditionalDependency condDep : def.getConditionalDependencies()) {
                Object actualValue = capConfig.get(condDep.getConditionField());
                if (Objects.equals(actualValue, condDep.getExpectedValue())) {
                    // 条件满足 → 添加所需能力
                    requiredCapabilities.addAll(condDep.getRequiredCapabilities());
                }
            }
        }

        // 3. 检查是否所有 requiredCapabilities 都在 selectedCaps 中
        for (String requiredCap : requiredCapabilities) {
            if (!selectedCaps.contains(requiredCap)) {
                throw new ValidationException(
                        "能力 [" + String.join(", ", selectedCaps) + "] 的配置触发了对能力 [" + requiredCap + "] 的依赖，但未启用该能力"
                );
            }
        }

        // === 第二步：字段级规则校验 ===

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

    /**
     * 校验指定能力类型的字段规则。
     *
     * @param capabilityType 能力类型标识符
     * @param configBlock    当前能力的配置块数据
     * @param capDef         当前能力的定义信息，包括其字段规则列表
     * @throws ValidationException 若字段不符合规则要求则抛出异常
     */
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

    /**
     * 判断给定字段在配置中是否存在且具有非空白的有效值。
     *
     * @param map   字段所在的配置映射表
     * @param field 待判断的字段名
     * @return 如果字段存在并且值不为空或空白字符串，则返回 true；否则返回 false
     */
    private boolean hasNonBlankValue(Map<String, Object> map, String field) {
        Object val = map.get(field);
        if (val == null) return false;
        if (val instanceof String) return !((String) val).isBlank();
        if (val instanceof List) return !CollectionUtils.isEmpty((List<?>) val);
        return true; // 其他类型（如数字、布尔值）只要存在即视为有效
    }
}

