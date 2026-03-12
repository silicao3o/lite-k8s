package com.example.dockermonitor.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class HealingEvent {
    private String containerId;
    private String containerName;
    private LocalDateTime timestamp;
    private boolean success;
    private int restartCount;
    private String message;
}
