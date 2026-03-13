package com.lite_k8s.playbook;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Safety Gate 설정
 *
 * application.yml에서 서비스 중요도 및 위험도 관련 설정을 읽어옴
 */
@Data
@Component
@ConfigurationProperties(prefix = "docker.monitor.safety-gate")
public class SafetyGateProperties {

    /**
     * Safety Gate 활성화 여부
     */
    private boolean enabled = true;

    /**
     * 기본 서비스 중요도
     */
    private ServiceCriticality defaultCriticality = ServiceCriticality.NORMAL;

    /**
     * 서비스 중요도 규칙 목록
     */
    private List<ServiceCriticalityConfig> serviceCriticality = new ArrayList<>();

    /**
     * ServiceCriticalityConfig를 ServiceCriticalityRule로 변환
     */
    public List<ServiceCriticalityRule> toServiceCriticalityRules() {
        return serviceCriticality.stream()
                .map(config -> new ServiceCriticalityRule(
                        config.getNamePattern(),
                        config.getCriticality()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 서비스 중요도 설정
     */
    @Data
    public static class ServiceCriticalityConfig {
        /**
         * 컨테이너 이름 패턴 (와일드카드 지원)
         */
        private String namePattern;

        /**
         * 서비스 중요도
         */
        private ServiceCriticality criticality = ServiceCriticality.NORMAL;
    }
}
