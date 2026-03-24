package com.alibaba.himarket.service.hicoding.session;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 后端解析后的完整会话配置 DTO。
 * 由 CliSessionConfig（纯标识符）经后端解析服务填充而成，供 CliConfigGenerator 使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResolvedSessionConfig {

    /** 解析后的完整模型配置（可能为 null） */
    private CustomModelConfig customModelConfig;

    /** 解析后的 MCP Server 列表（含完整连接信息） */
    private List<ResolvedMcpEntry> mcpServers;

    /** 解析后的 Skill 列表（含坐标+凭证） */
    private List<ResolvedSkillEntry> skills;

    /** 认证凭据（直接透传） */
    private String authToken;

    public CustomModelConfig getCustomModelConfig() {
        return customModelConfig;
    }

    public void setCustomModelConfig(CustomModelConfig customModelConfig) {
        this.customModelConfig = customModelConfig;
    }

    public List<ResolvedMcpEntry> getMcpServers() {
        return mcpServers;
    }

    public void setMcpServers(List<ResolvedMcpEntry> mcpServers) {
        this.mcpServers = mcpServers;
    }

    public List<ResolvedSkillEntry> getSkills() {
        return skills;
    }

    public void setSkills(List<ResolvedSkillEntry> skills) {
        this.skills = skills;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolvedMcpEntry {
        /** MCP 服务名称 */
        private String name;

        /** MCP 端点 URL */
        private String url;

        /** 传输协议类型：sse 或 streamable-http */
        private String transportType;

        /** 认证请求头（可能为 null） */
        private Map<String, String> headers;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getTransportType() {
            return transportType;
        }

        public void setTransportType(String transportType) {
            this.transportType = transportType;
        }

        public Map<String, String> getHeaders() {
            return headers;
        }

        public void setHeaders(Map<String, String> headers) {
            this.headers = headers;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResolvedSkillEntry {
        /** Skill 名称 */
        private String name;

        // Skill 坐标
        private String nacosId;
        private String namespace;
        private String skillName;

        // Nacos 凭证
        private String serverAddr;
        private String username;
        private String password;
        private String accessKey;
        private String secretKey;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNacosId() {
            return nacosId;
        }

        public void setNacosId(String nacosId) {
            this.nacosId = nacosId;
        }

        public String getNamespace() {
            return namespace;
        }

        public void setNamespace(String namespace) {
            this.namespace = namespace;
        }

        public String getSkillName() {
            return skillName;
        }

        public void setSkillName(String skillName) {
            this.skillName = skillName;
        }

        public String getServerAddr() {
            return serverAddr;
        }

        public void setServerAddr(String serverAddr) {
            this.serverAddr = serverAddr;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }
    }
}
