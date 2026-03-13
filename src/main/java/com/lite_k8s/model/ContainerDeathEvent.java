package com.lite_k8s.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ContainerDeathEvent {
    private String containerId;
    private String containerName;
    private String imageName;
    private LocalDateTime deathTime;
    private Long exitCode;
    private boolean oomKilled;
    private String deathReason;
    private String lastLogs;
    private String action; // die, kill, stop, oom
    private Map<String, String> labels;
}
