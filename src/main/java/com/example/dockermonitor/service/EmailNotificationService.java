package com.example.dockermonitor.service;

import com.example.dockermonitor.model.ContainerDeathEvent;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final JavaMailSender mailSender;

    @Value("${docker.monitor.notification.email.to}")
    private String recipientEmail;

    @Value("${docker.monitor.notification.email.from:docker-monitor@localhost}")
    private String fromEmail;

    @Value("${docker.monitor.server-name:Unknown Server}")
    private String serverName;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Async
    public void sendAlert(ContainerDeathEvent event) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(recipientEmail.split(","));
            helper.setSubject(buildSubject(event));
            helper.setText(buildHtmlContent(event), true);

            mailSender.send(message);
            log.info("알림 이메일 전송 완료: {}", event.getContainerName());

        } catch (Exception e) {
            log.error("이메일 전송 실패: {}", event.getContainerName(), e);
        }
    }

    private String buildSubject(ContainerDeathEvent event) {
        String emoji = event.isOomKilled() ? "[OOM]" : "[DOWN]";
        return String.format("%s 컨테이너 종료 알림: %s (%s)",
                emoji, event.getContainerName(), serverName);
    }

    private String buildHtmlContent(ContainerDeathEvent event) {
        String severityColor = getSeverityColor(event);
        String truncatedLogs = truncateLogs(event.getLastLogs(), 100);

        return String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; margin: 0; padding: 20px; background: #f5f5f5; }
                    .container { max-width: 700px; margin: 0 auto; background: white; border-radius: 8px; box-shadow: 0 2px 10px rgba(0,0,0,0.1); overflow: hidden; }
                    .header { background: %s; color: white; padding: 20px; text-align: center; }
                    .header h1 { margin: 0; font-size: 24px; }
                    .content { padding: 20px; }
                    .info-table { width: 100%%; border-collapse: collapse; margin-bottom: 20px; }
                    .info-table th { text-align: left; padding: 10px; background: #f8f9fa; border-bottom: 1px solid #dee2e6; width: 30%%; }
                    .info-table td { padding: 10px; border-bottom: 1px solid #dee2e6; }
                    .reason-box { background: #fff3cd; border: 1px solid #ffc107; border-radius: 4px; padding: 15px; margin-bottom: 20px; }
                    .reason-box h3 { margin-top: 0; color: #856404; }
                    .log-box { background: #1e1e1e; color: #d4d4d4; padding: 15px; border-radius: 4px; font-family: 'Consolas', monospace; font-size: 12px; white-space: pre-wrap; word-wrap: break-word; max-height: 400px; overflow-y: auto; }
                    .footer { background: #f8f9fa; padding: 15px; text-align: center; color: #6c757d; font-size: 12px; }
                    .badge { display: inline-block; padding: 4px 8px; border-radius: 4px; font-size: 12px; font-weight: bold; }
                    .badge-danger { background: #dc3545; color: white; }
                    .badge-warning { background: #ffc107; color: black; }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header">
                        <h1>Docker 컨테이너 종료 알림</h1>
                    </div>
                    <div class="content">
                        <table class="info-table">
                            <tr>
                                <th>서버</th>
                                <td>%s</td>
                            </tr>
                            <tr>
                                <th>컨테이너 이름</th>
                                <td><strong>%s</strong></td>
                            </tr>
                            <tr>
                                <th>컨테이너 ID</th>
                                <td><code>%s</code></td>
                            </tr>
                            <tr>
                                <th>이미지</th>
                                <td>%s</td>
                            </tr>
                            <tr>
                                <th>종료 시간</th>
                                <td>%s</td>
                            </tr>
                            <tr>
                                <th>Exit Code</th>
                                <td><span class="badge %s">%s</span></td>
                            </tr>
                            <tr>
                                <th>이벤트 타입</th>
                                <td>%s</td>
                            </tr>
                            <tr>
                                <th>OOM Killed</th>
                                <td>%s</td>
                            </tr>
                        </table>

                        <div class="reason-box">
                            <h3>종료 원인 분석</h3>
                            <pre style="margin: 0; white-space: pre-wrap;">%s</pre>
                        </div>

                        <h3>마지막 로그 (최근 %d줄)</h3>
                        <div class="log-box">%s</div>
                    </div>
                    <div class="footer">
                        Docker Monitor Service | 자동 생성된 알림입니다
                    </div>
                </div>
            </body>
            </html>
            """,
                severityColor,
                escapeHtml(serverName),
                escapeHtml(event.getContainerName()),
                escapeHtml(event.getContainerId() != null ? event.getContainerId().substring(0, Math.min(12, event.getContainerId().length())) : "N/A"),
                escapeHtml(event.getImageName() != null ? event.getImageName() : "N/A"),
                event.getDeathTime() != null ? event.getDeathTime().format(DATE_FORMAT) : "N/A",
                event.getExitCode() != null && event.getExitCode() != 0 ? "badge-danger" : "badge-warning",
                event.getExitCode() != null ? event.getExitCode() : "N/A",
                escapeHtml(event.getAction() != null ? event.getAction().toUpperCase() : "UNKNOWN"),
                event.isOomKilled() ? "<span class='badge badge-danger'>YES</span>" : "NO",
                escapeHtml(event.getDeathReason() != null ? event.getDeathReason() : "분석 정보 없음"),
                100,
                escapeHtml(truncatedLogs)
        );
    }

    private String getSeverityColor(ContainerDeathEvent event) {
        if (event.isOomKilled()) {
            return "#dc3545"; // 빨간색 - OOM
        }
        Long exitCode = event.getExitCode();
        if (exitCode == null || exitCode == 0 || exitCode == 143) {
            return "#ffc107"; // 노란색 - 정상/예상된 종료
        }
        return "#dc3545"; // 빨간색 - 비정상 종료
    }

    private String truncateLogs(String logs, int maxLines) {
        if (logs == null || logs.isEmpty()) {
            return "로그 없음";
        }
        String[] lines = logs.split("\n");
        if (lines.length <= maxLines) {
            return logs;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("... (").append(lines.length - maxLines).append("줄 생략) ...\n\n");
        for (int i = lines.length - maxLines; i < lines.length; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
