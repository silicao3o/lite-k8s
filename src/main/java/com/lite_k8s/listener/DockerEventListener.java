package com.lite_k8s.listener;

import com.lite_k8s.analyzer.ExitCodeAnalyzer;
import com.lite_k8s.config.MonitorProperties;
import com.lite_k8s.model.ContainerDeathEvent;
import com.lite_k8s.service.AlertDeduplicationService;
import com.lite_k8s.service.ContainerFilterService;
import com.lite_k8s.service.DockerService;
import com.lite_k8s.service.EmailNotificationService;
import com.lite_k8s.service.SelfHealingService;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Event;
import com.github.dockerjava.api.model.EventType;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class DockerEventListener {

    private final DockerClient dockerClient;
    private final DockerService dockerService;
    private final ExitCodeAnalyzer exitCodeAnalyzer;
    private final EmailNotificationService notificationService;
    private final MonitorProperties monitorProperties;
    private final ContainerFilterService containerFilterService;
    private final AlertDeduplicationService deduplicationService;
    private final SelfHealingService selfHealingService;

    private Closeable eventStream;
    private final AtomicInteger retryCount = new AtomicInteger(0);

    // 모니터링할 이벤트 액션들
    private static final Set<String> DEATH_ACTIONS = Set.of("die", "kill", "oom");

    @PostConstruct
    public void startListening() {
        log.info("Docker 이벤트 리스너 시작...");

        eventStream = dockerClient.eventsCmd()
                .withEventTypeFilter(EventType.CONTAINER)
                .exec(new ResultCallback.Adapter<Event>() {

                    @Override
                    public void onNext(Event event) {
                        handleEvent(event);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        log.error("Docker 이벤트 스트림 에러 발생", throwable);
                        // 연결 끊어지면 재연결 시도
                        reconnect();
                    }
                });

        log.info("Docker 이벤트 리스너 시작 완료");
    }

    private void handleEvent(Event event) {
        String action = event.getAction();
        String containerId = event.getId();

        log.debug("Docker 이벤트 수신: action={}, containerId={}", action, containerId);

        // die, kill, oom 이벤트만 처리
        if (action != null && DEATH_ACTIONS.contains(action.toLowerCase())) {
            log.info("컨테이너 종료 감지: containerId={}, action={}", containerId, action);

            // 중복 알림 체크
            if (!deduplicationService.shouldAlert(containerId, action)) {
                log.info("중복 알림 스킵: containerId={}, action={}", containerId, action);
                return;
            }

            try {
                // 컨테이너 정보 수집
                ContainerDeathEvent deathEvent = dockerService.buildDeathEvent(containerId, action);

                // 필터링 체크
                if (!containerFilterService.shouldMonitor(deathEvent.getContainerName(), deathEvent.getImageName())) {
                    log.info("컨테이너 필터링으로 알림 제외: {}", deathEvent.getContainerName());
                    return;
                }

                // Exit Code 분석하여 종료 원인 설정
                String deathReason = exitCodeAnalyzer.analyze(deathEvent);
                deathEvent.setDeathReason(deathReason);

                // 이메일 알림 전송
                notificationService.sendAlert(deathEvent);

                // 자가치유 시도
                selfHealingService.handleContainerDeath(deathEvent);

                log.info("컨테이너 종료 알림 전송 완료: {}", deathEvent.getContainerName());

            } catch (Exception e) {
                log.error("컨테이너 종료 이벤트 처리 실패: {}", containerId, e);
            }
        }
    }

    private void reconnect() {
        MonitorProperties.Reconnect config = monitorProperties.getReconnect();
        int currentRetry = retryCount.incrementAndGet();
        int maxRetries = config.getMaxRetries();

        // 최대 재시도 횟수 초과 체크 (0이면 무제한)
        if (maxRetries > 0 && currentRetry > maxRetries) {
            log.error("최대 재시도 횟수({}) 초과. Docker 이벤트 리스너 중단.", maxRetries);
            log.error("수동으로 서비스를 재시작하거나 Docker 데몬 상태를 확인하세요.");
            return;
        }

        // 지수 백오프 계산: initialDelay * (multiplier ^ (retry-1))
        long delay = (long) (config.getInitialDelayMs() * Math.pow(config.getMultiplier(), currentRetry - 1));
        delay = Math.min(delay, config.getMaxDelayMs()); // 최대 대기 시간 제한

        log.info("Docker 이벤트 스트림 재연결 시도 {}/{}, {}초 후...",
                currentRetry,
                maxRetries > 0 ? maxRetries : "무제한",
                delay / 1000);

        try {
            Thread.sleep(delay);
            startListening();
            // 연결 성공 시 재시도 카운트 리셋
            retryCount.set(0);
            log.info("Docker 이벤트 스트림 재연결 성공");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("재연결 중 인터럽트 발생", e);
        }
    }

    @PreDestroy
    public void stopListening() {
        log.info("Docker 이벤트 리스너 종료...");
        if (eventStream != null) {
            try {
                eventStream.close();
            } catch (IOException e) {
                log.error("이벤트 스트림 종료 실패", e);
            }
        }
    }
}
