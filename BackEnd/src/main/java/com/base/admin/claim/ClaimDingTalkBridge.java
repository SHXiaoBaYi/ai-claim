package com.base.admin.claim;

import com.base.admin.claim.domain.ClaimRobot;
import com.base.admin.claim.dto.ClaimRobotSaveDTO;
import com.base.admin.claim.dto.ClaimRobotVO;
import com.base.admin.claim.mapper.ClaimRobotMapper;
import com.base.admin.claim.service.ClaimConversationService;
import com.base.admin.claim.service.ClaimConversationService.ClaimFile;
import com.base.admin.claim.service.ClaimConversationService.ClaimInbound;
import com.base.admin.claim.support.ClaimTexts;
import com.base.admin.exception.BusinessException;
import com.dingtalk.open.app.api.OpenDingTalkClient;
import com.dingtalk.open.app.api.OpenDingTalkStreamClientBuilder;
import com.dingtalk.open.app.api.chatbot.BotReplier;
import com.dingtalk.open.app.api.models.bot.ChatbotMessage;
import com.dingtalk.open.app.api.models.bot.MessageContent;
import com.dingtalk.open.app.api.security.AuthClientCredential;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 50)
@RequiredArgsConstructor
public class ClaimDingTalkBridge implements ApplicationRunner {

    private static final String BOT_TOPIC = "/v1.0/im/bot/messages/get";

    private final ClaimRobotMapper robotMapper;
    private final ClaimConversationService conversationService;
    private final ObjectMapper objectMapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ExecutorService executor = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "claim-dingtalk");
        thread.setDaemon(true);
        return thread;
    });
    private final Set<String> seenMessageIds = ConcurrentHashMap.newKeySet();
    private final Object lock = new Object();
    private volatile OpenDingTalkClient client;
    private volatile String accessToken = "";
    private volatile long accessTokenUntil;
    private volatile String accessTokenKey = "";

    @Override
    public void run(ApplicationArguments args) {
        reload();
    }

    @PreDestroy
    public void shutdown() {
        stopQuietly();
        executor.shutdownNow();
    }

    public ClaimRobotVO get() {
        ClaimRobot robot = load();
        ClaimRobotVO vo = new ClaimRobotVO();
        vo.setClientId(ClaimTexts.empty(robot.getClientId()));
        vo.setRobotCode(ClaimTexts.empty(robot.getRobotCode()));
        vo.setEnabled(robot.getEnabled() == null ? 0 : robot.getEnabled());
        vo.setHasClientSecret(!ClaimTexts.blank(robot.getClientSecret()));
        vo.setClientSecretMasked(mask(robot.getClientSecret()));
        vo.setConnected(client != null);
        return vo;
    }

    public void save(ClaimRobotSaveDTO dto) {
        ClaimRobot robot = load();
        robot.setClientId(ClaimTexts.empty(dto.getClientId()));
        if (!ClaimTexts.blank(dto.getClientSecret())) {
            robot.setClientSecret(dto.getClientSecret().trim());
        }
        robot.setRobotCode(ClaimTexts.empty(dto.getRobotCode()));
        robot.setEnabled(dto.getEnabled() != null && dto.getEnabled() == 1 ? 1 : 0);
        robotMapper.updateById(robot);
        accessToken = "";
        reload();
    }

    public void test(ClaimRobotSaveDTO dto) {
        ClaimRobot robot = load();
        String clientId = ClaimTexts.blank(dto.getClientId()) ? robot.getClientId() : dto.getClientId().trim();
        String secret = ClaimTexts.blank(dto.getClientSecret()) ? robot.getClientSecret() : dto.getClientSecret().trim();
        if (ClaimTexts.blank(clientId) || ClaimTexts.blank(secret)) {
            throw new BusinessException("请先填写 Client ID 和 Client Secret");
        }
        requestToken(clientId, secret);
    }

    public void reload() {
        synchronized (lock) {
            stopQuietly();
            ClaimRobot robot = load();
            if (robot.getEnabled() == null || robot.getEnabled() != 1
                    || ClaimTexts.blank(robot.getClientId()) || ClaimTexts.blank(robot.getClientSecret())) {
                log.info("理赔机器人未启用，跳过 Stream 连接");
                return;
            }
            try {
                client = OpenDingTalkStreamClientBuilder.custom()
                        .credential(new AuthClientCredential(robot.getClientId(), robot.getClientSecret()))
                        .registerCallbackListener(BOT_TOPIC, this::onCallback)
                        .build();
                client.start();
                log.info("理赔机器人 Stream 已连接");
            } catch (Exception ex) {
                client = null;
                log.error("理赔机器人 Stream 连接失败: {}", ex.getMessage());
            }
        }
    }

    private Void onCallback(ChatbotMessage message) {
        if (message != null && !ClaimTexts.blank(message.getMsgId()) && !seenMessageIds.add(message.getMsgId())) {
            return null;
        }
        if (seenMessageIds.size() > 2000) {
            seenMessageIds.clear();
        }
        executor.execute(() -> safeHandle(message));
        return null;
    }

    private void safeHandle(ChatbotMessage message) {
        try {
            String reply = conversationService.handle(toInbound(message));
            send(message.getSessionWebhook(), reply);
        } catch (Exception ex) {
            log.error("处理理赔单聊失败", ex);
            send(message == null ? "" : message.getSessionWebhook(), "这张投诉暂时没处理成功，请再发一次。");
        }
    }

    private ClaimInbound toInbound(ChatbotMessage message) {
        List<ClaimFile> files = new ArrayList<>();
        collectFiles(message.getContent(), files);
        if ("richText".equals(message.getMsgtype()) && message.getContent() != null && message.getContent().getRichText() != null) {
            for (MessageContent part : message.getContent().getRichText()) {
                collectFiles(part, files);
            }
        }
        return new ClaimInbound(
                ClaimTexts.empty(message.getConversationId()),
                ClaimTexts.empty(message.getConversationType()),
                ClaimTexts.empty(message.getSenderStaffId()),
                ClaimTexts.empty(message.getSenderNick()),
                textOf(message),
                ClaimTexts.empty(message.getMsgtype()),
                files);
    }

    private void collectFiles(MessageContent content, List<ClaimFile> files) {
        if (content == null) {
            return;
        }
        String code = first(content.getDownloadCode(), content.getPictureDownloadCode());
        if (ClaimTexts.blank(code)) {
            return;
        }
        String kind = content.getVideoType() != null ? "video" : (content.getFileName() != null ? "file" : "image");
        String name = ClaimTexts.blank(content.getFileName()) ? kind : content.getFileName();
        try {
            files.add(new ClaimFile(kind, name, download(code), ""));
        } catch (Exception ex) {
            files.add(new ClaimFile(kind, name, null, ex.getMessage()));
        }
    }

    private byte[] download(String downloadCode) throws Exception {
        ClaimRobot robot = load();
        if (ClaimTexts.blank(robot.getRobotCode())) {
            throw new BusinessException("已收到文件，但后台还没填写 RobotCode，文件还不能保存。请用文字补充，或先配置 RobotCode。");
        }
        String token = token(robot);
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "downloadCode", downloadCode,
                "robotCode", robot.getRobotCode()));
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.dingtalk.com/v1.0/robot/messageFiles/download"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header("x-acs-dingtalk-access-token", token)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode node = objectMapper.readTree(response.body());
        String url = node.path("downloadUrl").asText("");
        if (url.isBlank()) {
            throw new BusinessException("钉钉文件下载失败：" + ClaimTexts.clip(response.body(), 180));
        }
        HttpResponse<byte[]> file = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).GET().build(),
                HttpResponse.BodyHandlers.ofByteArray());
        if (file.statusCode() >= 300) {
            throw new BusinessException("钉钉文件内容下载失败");
        }
        return file.body();
    }

    private String token(ClaimRobot robot) {
        String key = robot.getClientId() + "|" + robot.getClientSecret();
        if (key.equals(accessTokenKey) && System.currentTimeMillis() < accessTokenUntil && !accessToken.isBlank()) {
            return accessToken;
        }
        JsonNode node = requestToken(robot.getClientId(), robot.getClientSecret());
        accessToken = node.path("accessToken").asText("");
        accessTokenKey = key;
        accessTokenUntil = System.currentTimeMillis() + Math.max(60, node.path("expireIn").asLong(7200) - 120) * 1000;
        return accessToken;
    }

    private JsonNode requestToken(String clientId, String secret) {
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of("appKey", clientId, "appSecret", secret));
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.dingtalk.com/v1.0/oauth2/accessToken"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode node = objectMapper.readTree(response.body());
            if (node.path("accessToken").asText("").isBlank()) {
                throw new BusinessException("钉钉连接失败：" + ClaimTexts.clip(response.body(), 180));
            }
            return node;
        } catch (BusinessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BusinessException("钉钉连接失败：" + ex.getMessage());
        }
    }

    private String textOf(ChatbotMessage message) {
        if (message.getText() != null && !ClaimTexts.blank(message.getText().getContent())) {
            return message.getText().getContent().trim();
        }
        StringBuilder text = new StringBuilder();
        if (message.getContent() != null && message.getContent().getRichText() != null) {
            for (MessageContent part : message.getContent().getRichText()) {
                if (!ClaimTexts.blank(part.getText())) {
                    text.append(part.getText());
                }
            }
        }
        if (text.isEmpty() && message.getContent() != null && !ClaimTexts.blank(message.getContent().getRecognition())) {
            return message.getContent().getRecognition().trim();
        }
        return text.toString().trim();
    }

    private void send(String webhook, String text) {
        if (ClaimTexts.blank(webhook)) {
            log.warn("理赔机器人没有 sessionWebhook，无法回复");
            return;
        }
        String content = ClaimTexts.blank(text) ? "已收到。" : text;
        try {
            int size = 1500;
            for (int i = 0; i < content.length(); i += size) {
                BotReplier.fromWebhook(webhook).replyText(content.substring(i, Math.min(content.length(), i + size)));
            }
        } catch (Exception ex) {
            log.error("理赔机器人回复失败: {}", ex.getMessage());
        }
    }

    private ClaimRobot load() {
        ClaimRobot robot = robotMapper.selectById(1L);
        if (robot == null) {
            throw new BusinessException("理赔机器人配置不存在");
        }
        return robot;
    }

    private void stopQuietly() {
        if (client == null) {
            return;
        }
        try {
            client.stop();
        } catch (Exception ex) {
            log.warn("停止理赔机器人 Stream 失败: {}", ex.getMessage());
        }
        client = null;
    }

    private String first(String... values) {
        for (String value : values) {
            if (!ClaimTexts.blank(value)) {
                return value;
            }
        }
        return "";
    }

    private String mask(String secret) {
        if (ClaimTexts.blank(secret)) {
            return "";
        }
        if (secret.length() <= 6) {
            return "******";
        }
        return secret.substring(0, 3) + "****" + secret.substring(secret.length() - 3);
    }
}
