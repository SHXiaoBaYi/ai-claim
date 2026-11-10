package com.base.admin.service;

import com.base.admin.config.DingTalkProperties;
import com.base.admin.domain.dto.DingTalkAppDTO;
import com.base.admin.domain.vo.DingTalkAppVO;
import com.base.admin.domain.vo.DingTalkLoginConfigVO;
import com.base.admin.exception.BusinessException;
import com.base.admin.util.SecurityUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class DingTalkAppService {

    private final JdbcTemplate jdbc;
    private final DingTalkProperties dingTalkProperties;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public DingTalkAppVO get() {
        Stored stored = loadStored();
        DingTalkAppVO vo = new DingTalkAppVO();
        if (stored == null) {
            vo.setAppId("");
            vo.setAgentId("");
            vo.setClientId("");
            vo.setCorpId(blank(dingTalkProperties.getCorpId()));
            vo.setClientSecretMasked("");
            vo.setHasClientSecret(false);
            vo.setEnabled(0);
            return vo;
        }
        vo.setAppId(stored.appId());
        vo.setAgentId(stored.agentId());
        vo.setClientId(stored.clientId());
        vo.setCorpId(stored.corpId());
        if (!StringUtils.hasText(vo.getCorpId())) {
            vo.setCorpId(blank(dingTalkProperties.getCorpId()));
        }
        vo.setHasClientSecret(StringUtils.hasText(stored.clientSecret()));
        vo.setClientSecretMasked(mask(stored.clientSecret()));
        vo.setEnabled(stored.enabled());
        return vo;
    }

    /** 登录页公开配置：不返回密钥。 */
    public DingTalkLoginConfigVO loginConfig() {
        DingTalkLoginConfigVO vo = new DingTalkLoginConfigVO();
        Stored stored = loadStored();
        if (stored == null || stored.enabled() == null || stored.enabled() != 1
                || !StringUtils.hasText(stored.clientId()) || !StringUtils.hasText(stored.clientSecret())) {
            vo.setEnabled(0);
            vo.setClientId("");
            vo.setCorpId("");
            vo.setExclusiveLogin(false);
            vo.setMessage("钉钉扫码登录未配置，请联系管理员在「系统管理 → 钉钉应用配置」启用");
            return vo;
        }
        vo.setEnabled(1);
        vo.setClientId(stored.clientId().trim());
        String corpId = stored.corpId() == null ? "" : stored.corpId().trim();
        if (!StringUtils.hasText(corpId)) {
            corpId = blank(dingTalkProperties.getCorpId());
        }
        vo.setCorpId(corpId);
        vo.setExclusiveLogin(StringUtils.hasText(corpId));
        vo.setMessage("请使用钉钉扫码登录");
        return vo;
    }

    /** 未启用或缺少凭证时返回 null。 */
    public Credential credential() {
        Stored stored = loadStored();
        if (stored == null || stored.enabled() == null || stored.enabled() != 1) {
            return null;
        }
        if (!StringUtils.hasText(stored.clientId()) || !StringUtils.hasText(stored.clientSecret())) {
            return null;
        }
        return new Credential(stored.clientId().trim(), stored.clientSecret().trim());
    }

    public String corpId() {
        Stored stored = loadStored();
        if (stored != null && StringUtils.hasText(stored.corpId())) {
            return stored.corpId().trim();
        }
        return blank(dingTalkProperties.getCorpId());
    }

    /** 测试连接：密钥留空时用已保存的值。 */
    public Credential resolveForTest(DingTalkAppDTO dto) {
        String clientId = dto.getClientId().trim();
        String secret = StringUtils.hasText(dto.getClientSecret()) ? dto.getClientSecret().trim() : storedSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException("请填写 Client Secret");
        }
        return new Credential(clientId, secret);
    }

    public void testConnection(String clientId, String clientSecret) {
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("appKey", clientId);
            body.put("appSecret", clientSecret);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = objectMapper.readTree(response.body());
            String token = json.path("accessToken").asText("");
            if (!StringUtils.hasText(token)) {
                String message = json.path("message").asText(response.body());
                throw new BusinessException("获取钉钉访问凭证失败：" + message);
            }
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("连接钉钉失败：" + ex.getMessage());
        }
    }

    @Transactional
    public void save(DingTalkAppDTO dto) {
        String secret = StringUtils.hasText(dto.getClientSecret()) ? dto.getClientSecret().trim() : storedSecret();
        if (!StringUtils.hasText(secret)) {
            throw new BusinessException("请填写 Client Secret");
        }
        String corpId = dto.getCorpId() == null ? "" : dto.getCorpId().trim();
        if (!StringUtils.hasText(corpId)) {
            throw new BusinessException("请填写企业 CorpId（钉钉开放平台「应用信息 / 企业信息」里的 CorpId，不是 ClientId）");
        }
        String user = SecurityUtils.getCurrentUsername();
        Stored current = loadStored();
        if (current == null) {
            jdbc.update("""
                    INSERT INTO sys_dingtalk_app
                      (id, app_id, agent_id, client_id, client_secret, corp_id, enabled, create_by, is_active)
                    VALUES (1, ?, ?, ?, ?, ?, ?, ?, 1)
                    """, dto.getAppId().trim(), dto.getAgentId().trim(), dto.getClientId().trim(), secret, corpId,
                    dto.getEnabled(), user);
            return;
        }
        jdbc.update("""
                UPDATE sys_dingtalk_app
                SET app_id = ?, agent_id = ?, client_id = ?, client_secret = ?, corp_id = ?,
                    enabled = ?, update_by = ?, is_active = 1
                WHERE id = 1
                """, dto.getAppId().trim(), dto.getAgentId().trim(), dto.getClientId().trim(), secret, corpId,
                dto.getEnabled(), user);
    }

    private String storedSecret() {
        Stored stored = loadStored();
        return stored == null || stored.clientSecret() == null ? "" : stored.clientSecret();
    }

    private Stored loadStored() {
        return jdbc.query("""
                SELECT app_id, agent_id, client_id, client_secret, corp_id, enabled
                FROM sys_dingtalk_app WHERE id = 1 AND is_active = 1
                """, rs -> {
            if (!rs.next()) {
                return null;
            }
            String corpId = "";
            try {
                corpId = rs.getString("corp_id");
            } catch (Exception ignored) {
                // 旧库尚未迁移列
            }
            return new Stored(
                    rs.getString("app_id"),
                    rs.getString("agent_id"),
                    rs.getString("client_id"),
                    rs.getString("client_secret"),
                    corpId == null ? "" : corpId,
                    rs.getInt("enabled"));
        });
    }

    static String mask(String secret) {
        if (!StringUtils.hasText(secret)) {
            return "";
        }
        String value = secret.trim();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }

    private static String blank(String raw) {
        return StringUtils.hasText(raw) ? raw.trim() : "";
    }

    public record Stored(
            String appId,
            String agentId,
            String clientId,
            String clientSecret,
            String corpId,
            Integer enabled) {
    }

    public record Credential(String clientId, String clientSecret) {
    }
}
