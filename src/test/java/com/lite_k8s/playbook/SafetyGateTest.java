package com.lite_k8s.playbook;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SafetyGateTest {

    @Mock
    private SafetyGateProperties properties;

    private SafetyGate safetyGate;

    @BeforeEach
    void setUp() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.toServiceCriticalityRules()).thenReturn(List.of(
                new ServiceCriticalityRule("db-*", ServiceCriticality.CRITICAL),
                new ServiceCriticalityRule("api-*", ServiceCriticality.HIGH)
        ));

        RiskAssessor riskAssessor = new RiskAssessor();
        ServiceCriticalityResolver criticalityResolver = new ServiceCriticalityResolver(
                properties.toServiceCriticalityRules()
        );

        safetyGate = new SafetyGate(properties, riskAssessor, criticalityResolver);
    }

    @Test
    @DisplayName("LOW 위험도 Playbook은 실행 허용")
    void shouldAllowLowRiskPlaybook() {
        // given
        Playbook playbook = Playbook.builder()
                .name("notify")
                .riskLevel(RiskLevel.LOW)
                .actions(List.of(
                        Action.builder().type("email").build()
                ))
                .build();

        String containerName = "worker-email";
        Map<String, String> labels = Map.of();

        // when
        SafetyGateResult result = safetyGate.evaluate(playbook, containerName, labels);

        // then
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.getFinalRiskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("CRITICAL 위험도 Playbook은 실행 차단")
    void shouldBlockCriticalRiskPlaybook() {
        // given
        Playbook playbook = Playbook.builder()
                .name("force-remove")
                .riskLevel(RiskLevel.CRITICAL)
                .actions(List.of(
                        Action.builder().type("container.remove").build()
                ))
                .build();

        String containerName = "db-postgres";
        Map<String, String> labels = Map.of();

        // when
        SafetyGateResult result = safetyGate.evaluate(playbook, containerName, labels);

        // then
        assertThat(result.isAllowed()).isFalse();
        assertThat(result.getFinalRiskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.getReason()).contains("CRITICAL");
    }

    @Test
    @DisplayName("Safety Gate가 비활성화되면 모든 Playbook 허용")
    void shouldAllowAllWhenDisabled() {
        // given
        when(properties.isEnabled()).thenReturn(false);

        Playbook criticalPlaybook = Playbook.builder()
                .name("dangerous")
                .riskLevel(RiskLevel.CRITICAL)
                .build();

        // when
        SafetyGateResult result = safetyGate.evaluate(criticalPlaybook, "test", Map.of());

        // then
        assertThat(result.isAllowed()).isTrue();
    }
}
