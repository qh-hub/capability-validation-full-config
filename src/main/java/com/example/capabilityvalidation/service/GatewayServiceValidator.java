package com.example.capabilityvalidation.service;

import com.example.capabilityvalidation.exception.ValidationException;
import io.micrometer.common.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

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

    private void checkDuplicate(Set<String> seen, String code, String fieldPath) {
        if (!seen.add(code)) {
            throw new ValidationException(fieldPath + " 重复：'" + code + "'");
        }
    }
}