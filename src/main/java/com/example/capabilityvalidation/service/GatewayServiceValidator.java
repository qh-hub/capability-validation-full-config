package com.example.capabilityvalidation.service;

import com.example.capabilityvalidation.exception.ValidationException;
import io.micrometer.common.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 网关服务能力验证器，实现自定义字段校验接口。
 * 负责对网关服务能力配置进行合法性检查，包括订阅服务与发布服务的完整性、格式及业务规则验证。
 */
@Component("gatewayServiceValidator")
public class GatewayServiceValidator implements CustomFieldValidator {

    // 更严格的 URL 正则（支持 http/https，域名或 IP，可选端口和路径）
    private static final Pattern URL_PATTERN = Pattern.compile(
            "^https?://" +
                    "(?:" +
                    "(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}|" +  // 域名
                    "(?:[0-9]{1,3}\\.){3}[0-9]{1,3}" +                                       // IPv4
                    ")" +
                    "(?::[0-9]{1,5})?" +      // 可选端口
                    "(?:/.*)?$"               // 可选路径
    );

    /**
     * 对指定能力类型的配置数据进行校验。
     *
     * @param capabilityType 能力类型名称，用于构建异常信息上下文
     * @param configData     配置数据映射表，包含订阅服务列表和发布服务列表等字段
     * @throws ValidationException 当配置不符合规范时抛出该异常
     */
    @Override
    public void validate(String capabilityType, Map<String, Object> configData) {
        if (configData == null) {
            throw new ValidationException("能力 [" + capabilityType + "] 缺少配置数据");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subscribed = (List<Map<String, Object>>) configData.get("subscribedServices");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> published = (List<Map<String, Object>>) configData.get("publishedServices");

        boolean hasSubscribed = subscribed != null && !subscribed.isEmpty();
        boolean hasPublished = published != null && !published.isEmpty();

        if (!hasSubscribed && !hasPublished) {
            throw new ValidationException(
                    "能力 [" + capabilityType + "] 必须至少配置一条订阅服务或发布服务"
            );
        }

        // 校验并收集 serviceCode 用于去重
        Set<String> subscribedCodes = new HashSet<>();
        Set<String> publishedCodes = new HashSet<>();

        if (subscribed != null) {
            for (int i = 0; i < subscribed.size(); i++) {
                Map<String, Object> service = subscribed.get(i);
                String prefix = "subscribedServices[" + i + "]";
                validateRequiredFields(service, prefix, "serviceCode", "systemCode", "serviceName");

                String serviceCode = getStringValue(service, "serviceCode", prefix);
                checkDuplicate(subscribedCodes, serviceCode, prefix + ".serviceCode");

                // 可选：校验 serviceCode 格式（如大写+下划线）
                /*if (!serviceCode.matches("^[A-Z][A-Z0-9.]*$")) {
                    throw new ValidationException(prefix + ".serviceCode 格式错误，应为大写字母开头，仅包含大写字母、数字和点");
                }*/
                if(StringUtils.isBlank(serviceCode)){
                    throw new ValidationException(prefix + ".serviceCode 不能为空");
                }
            }
        }

        if (published != null) {
            for (int i = 0; i < published.size(); i++) {
                Map<String, Object> service = published.get(i);
                String prefix = "publishedServices[" + i + "]";
                validateRequiredFields(service, prefix, "serviceCode", "serviceName", "gatewayUrl", "timeoutMs");

                String serviceCode = getStringValue(service, "serviceCode", prefix);
                checkDuplicate(publishedCodes, serviceCode, prefix + ".serviceCode");

                // 可选：校验 serviceCode 格式（如大写+下划线）
                /*if (!serviceCode.matches("^[A-Z][A-Z0-9.]*$")) {
                    throw new ValidationException(prefix + ".serviceCode 格式错误，应为大写字母开头，仅包含大写字母、数字和点");
                }*/
                if(StringUtils.isBlank(serviceCode)){
                    throw new ValidationException(prefix + ".serviceCode 不能为空");
                }

                // === URL 校验 ===
                String gatewayUrl = getStringValue(service, "gatewayUrl", prefix);
                if (!URL_PATTERN.matcher(gatewayUrl).matches()) {
                    throw new ValidationException(prefix + ".gatewayUrl 必须是有效的 HTTP/HTTPS URL（例如：https://api.example.com/path）");
                }

                // === timeoutMs 校验 ===
                Object timeoutObj = service.get("timeoutMs");
                long timeout;
                if (timeoutObj instanceof Number) {
                    timeout = ((Number) timeoutObj).longValue();
                } else if (timeoutObj instanceof String) {
                    try {
                        timeout = Long.parseLong((String) timeoutObj);
                    } catch (NumberFormatException e) {
                        throw new ValidationException(prefix + ".timeoutMs 格式错误，应为正整数");
                    }
                } else {
                    throw new ValidationException(prefix + ".timeoutMs 必须是数字或数字字符串");
                }

                if (timeout <= 0 || timeout > 30000) {
                    throw new ValidationException(prefix + ".timeoutMs 必须在 1 ~ 30000 毫秒之间");
                }
            }
        }
    }

    /**
     * 获取对象中某个字段的字符串值，并做基本非空判断。
     *
     * @param obj    数据对象
     * @param field  字段名
     * @param prefix 错误提示前缀路径
     * @return 字符串值
     * @throws ValidationException 若字段不存在、不是字符串或为空时抛出异常
     */
    private String getStringValue(Map<String, Object> obj, String field, String prefix) {
        Object value = obj.get(field);
        if (value == null) {
            throw new ValidationException(prefix + "." + field + " 不能为空");
        }
        if (!(value instanceof String)) {
            throw new ValidationException(prefix + "." + field + " 必须是字符串");
        }
        String str = (String) value;
        if (str.isBlank()) {
            throw new ValidationException(prefix + "." + field + " 不能为空");
        }
        return str;
    }

    /**
     * 验证给定对象是否包含所有必需字段且不为空。
     *
     * @param obj           待验证的对象
     * @param prefix        异常消息中的字段路径前缀
     * @param requiredFields 所有必须存在的字段名数组
     * @throws ValidationException 若任一必填字段缺失或为空时抛出异常
     */
    private void validateRequiredFields(Map<String, Object> obj, String prefix, String... requiredFields) {
        if (obj == null) {
            throw new ValidationException(prefix + " 不能为 null");
        }
        for (String field : requiredFields) {
            Object value = obj.get(field);
            if (value == null) {
                throw new ValidationException(prefix + "." + field + " 为必填项");
            }
            if (value instanceof String && ((String) value).isBlank()) {
                throw new ValidationException(prefix + "." + field + " 不能为空");
            }
        }
    }

    /**
     * 检查指定编码是否已存在集合中，防止重复。
     *
     * @param seen       已经记录过的编码集合
     * @param code       当前待检查的编码
     * @param fieldPath  出错时显示的字段路径
     * @throws ValidationException 若发现重复编码则抛出异常
     */
    private void checkDuplicate(Set<String> seen, String code, String fieldPath) {
        if (!seen.add(code)) {
            throw new ValidationException(fieldPath + " 重复：'" + code + "'");
        }
    }
}
