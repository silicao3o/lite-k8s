package com.example.dockermonitor.controller;

import com.example.dockermonitor.config.SelfHealingProperties;
import com.example.dockermonitor.model.ContainerInfo;
import com.example.dockermonitor.model.HealingEvent;
import com.example.dockermonitor.repository.HealingEventRepository;
import com.example.dockermonitor.model.ContainerMetrics;
import com.example.dockermonitor.service.ContainerLabelReader;
import com.example.dockermonitor.service.DockerService;
import com.example.dockermonitor.service.HealingRuleMatcher;
import com.example.dockermonitor.service.LogSearchService;
import com.example.dockermonitor.service.MetricsCollector;
import com.example.dockermonitor.service.RestartTracker;
import com.example.dockermonitor.model.LogSearchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DockerService dockerService;
    private final SelfHealingProperties selfHealingProperties;
    private final ContainerLabelReader labelReader;
    private final HealingRuleMatcher ruleMatcher;
    private final RestartTracker restartTracker;
    private final HealingEventRepository healingEventRepository;
    private final MetricsCollector metricsCollector;
    private final LogSearchService logSearchService;

    @GetMapping("/")
    public String dashboard(Model model,
                           @RequestParam(defaultValue = "true") boolean showAll) {
        List<ContainerInfo> containers = dockerService.listContainers(showAll);

        // 자가치유 상태 및 메트릭 설정
        containers.forEach(this::setContainerInfo);

        long runningCount = containers.stream()
                .filter(c -> "running".equalsIgnoreCase(c.getState()))
                .count();
        long stoppedCount = containers.stream()
                .filter(c -> "exited".equalsIgnoreCase(c.getState()))
                .count();

        model.addAttribute("containers", containers);
        model.addAttribute("totalCount", containers.size());
        model.addAttribute("runningCount", runningCount);
        model.addAttribute("stoppedCount", stoppedCount);
        model.addAttribute("showAll", showAll);
        model.addAttribute("healingEnabled", selfHealingProperties.isEnabled());

        return "dashboard";
    }

    private void setContainerInfo(ContainerInfo container) {
        setHealingInfo(container);
        setMetrics(container);
    }

    private void setMetrics(ContainerInfo container) {
        if (!"running".equalsIgnoreCase(container.getState())) {
            return;
        }
        metricsCollector.collectMetrics(container.getId(), container.getName())
                .ifPresent(metrics -> {
                    container.setCpuPercent(metrics.getCpuPercent());
                    container.setMemoryUsage(metrics.getMemoryUsage());
                    container.setMemoryLimit(metrics.getMemoryLimit());
                    container.setMemoryPercent(metrics.getMemoryPercent());
                    container.setNetworkRxBytes(metrics.getNetworkRxBytes());
                    container.setNetworkTxBytes(metrics.getNetworkTxBytes());
                });
    }

    private void setHealingInfo(ContainerInfo container) {
        // 라벨에서 설정 확인
        var labelConfig = labelReader.readHealingConfig(container.getLabels());
        if (labelConfig.isPresent()) {
            container.setHealingEnabled(true);
            container.setMaxRestarts(labelConfig.get().getMaxRestarts());
        } else {
            // yml 규칙에서 설정 확인
            var ruleConfig = ruleMatcher.findMatchingRule(container.getName());
            if (ruleConfig.isPresent()) {
                container.setHealingEnabled(true);
                container.setMaxRestarts(ruleConfig.get().getMaxRestarts());
            } else {
                container.setHealingEnabled(false);
            }
        }

        // 재시작 횟수 설정
        container.setRestartCount(restartTracker.getRestartCount(container.getId()));
    }

    @GetMapping("/containers/{id}")
    public String containerDetail(@PathVariable String id, Model model) {
        ContainerInfo container = dockerService.getContainer(id);
        if (container == null) {
            return "redirect:/";
        }

        setHealingInfo(container);
        String logs = dockerService.getContainerLogs(id);
        List<HealingEvent> healingEvents = healingEventRepository.findByContainerId(id);

        model.addAttribute("container", container);
        model.addAttribute("logs", logs);
        model.addAttribute("healingEvents", healingEvents);

        return "container-detail";
    }

    @GetMapping("/api/containers/{id}/logs")
    @ResponseBody
    public String getContainerLogs(@PathVariable String id) {
        return dockerService.getContainerLogs(id);
    }

    @GetMapping("/api/containers/{id}/logs/search")
    @ResponseBody
    public LogSearchResult searchContainerLogs(
            @PathVariable String id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) List<String> levels) {

        java.time.LocalDateTime fromTime = null;
        java.time.LocalDateTime toTime = null;

        if (from != null && !from.isEmpty()) {
            fromTime = java.time.LocalDateTime.parse(from);
        }
        if (to != null && !to.isEmpty()) {
            toTime = java.time.LocalDateTime.parse(to);
        }

        return logSearchService.search(id, keyword, fromTime, toTime, levels);
    }

    @GetMapping("/healing-logs")
    public String healingLogs(Model model,
                              @RequestParam(required = false) Boolean success) {
        List<HealingEvent> events;
        if (success == null) {
            events = healingEventRepository.findAll();
        } else {
            events = healingEventRepository.findBySuccess(success);
        }
        model.addAttribute("events", events);
        model.addAttribute("healingEnabled", selfHealingProperties.isEnabled());
        model.addAttribute("successFilter", success);
        return "healing-logs";
    }

    @GetMapping("/api/healing-logs")
    @ResponseBody
    public List<HealingEvent> getHealingLogs() {
        return healingEventRepository.findAll();
    }
}
