package com.example.dockermonitor.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class ContainerInfo {
    private String id;
    private String shortId;
    private String name;
    private String image;
    private String status;
    private String state;
    private LocalDateTime created;
    private List<PortMapping> ports;
    private Map<String, String> labels;

    // 자가치유 관련 필드
    private boolean healingEnabled;
    private int restartCount;
    private int maxRestarts;

    public String getStatusClass() {
        if (state == null) return "unknown";
        return switch (state.toLowerCase()) {
            case "running" -> "running";
            case "exited", "dead" -> "exited";
            case "paused" -> "paused";
            case "restarting" -> "restarting";
            case "created" -> "created";
            default -> "unknown";
        };
    }

    @Data
    @Builder
    public static class PortMapping {
        private int privatePort;
        private int publicPort;
        private String type;

        public String getDisplayString() {
            if (publicPort > 0) {
                return publicPort + ":" + privatePort + "/" + type;
            }
            return privatePort + "/" + type;
        }
    }
}
