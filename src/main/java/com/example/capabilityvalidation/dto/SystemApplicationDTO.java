package com.example.capabilityvalidation.dto;

import java.util.List;
import java.util.Map;

public class SystemApplicationDTO {
    private String systemCode;
    private String dept;
    private String applicant;
    private List<String> capabilities;
    private Map<String, Object> configData;

    public String getSystemCode() { return systemCode; }
    public void setSystemCode(String systemCode) { this.systemCode = systemCode; }

    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }

    public String getApplicant() { return applicant; }
    public void setApplicant(String applicant) { this.applicant = applicant; }

    public List<String> getCapabilities() { return capabilities; }
    public void setCapabilities(List<String> capabilities) { this.capabilities = capabilities; }

    public Map<String, Object> getConfigData() { return configData; }
    public void setConfigData(Map<String, Object> configData) { this.configData = configData; }
}
