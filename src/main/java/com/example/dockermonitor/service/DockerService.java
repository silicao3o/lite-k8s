package com.example.dockermonitor.service;

import com.example.dockermonitor.model.ContainerDeathEvent;
import com.example.dockermonitor.model.ContainerInfo;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class DockerService {

    private final DockerClient dockerClient;

    @Value("${docker.monitor.log-tail-lines:50}")
    private int logTailLines;

    public ContainerDeathEvent buildDeathEvent(String containerId, String action) {
        try {
            InspectContainerResponse inspection = dockerClient.inspectContainerCmd(containerId).exec();
            InspectContainerResponse.ContainerState state = inspection.getState();

            String containerName = inspection.getName();
            if (containerName != null && containerName.startsWith("/")) {
                containerName = containerName.substring(1);
            }

            Long exitCode = state.getExitCodeLong();
            Boolean oomKilled = state.getOOMKilled();
            String finishedAt = state.getFinishedAt();

            LocalDateTime deathTime = parseDockerTime(finishedAt);
            String lastLogs = getContainerLogs(containerId);

            return ContainerDeathEvent.builder()
                    .containerId(containerId)
                    .containerName(containerName)
                    .imageName(inspection.getConfig().getImage())
                    .deathTime(deathTime)
                    .exitCode(exitCode)
                    .oomKilled(oomKilled != null && oomKilled)
                    .action(action)
                    .lastLogs(lastLogs)
                    .build();

        } catch (Exception e) {
            log.error("컨테이너 정보 조회 실패: {}", containerId, e);
            return ContainerDeathEvent.builder()
                    .containerId(containerId)
                    .containerName("Unknown")
                    .deathTime(LocalDateTime.now())
                    .action(action)
                    .lastLogs("로그 조회 실패: " + e.getMessage())
                    .build();
        }
    }

    public String getContainerLogs(String containerId) {
        try {
            List<String> logs = new ArrayList<>();

            LogContainerResultCallback callback = new LogContainerResultCallback() {
                @Override
                public void onNext(Frame frame) {
                    logs.add(new String(frame.getPayload()).trim());
                }
            };

            dockerClient.logContainerCmd(containerId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(logTailLines)
                    .withTimestamps(true)
                    .exec(callback)
                    .awaitCompletion(10, TimeUnit.SECONDS);

            return String.join("\n", logs);

        } catch (Exception e) {
            log.error("로그 조회 실패: {}", containerId, e);
            return "로그 조회 실패: " + e.getMessage();
        }
    }

    private LocalDateTime parseDockerTime(String dockerTime) {
        if (dockerTime == null || dockerTime.isEmpty() || dockerTime.equals("0001-01-01T00:00:00Z")) {
            return LocalDateTime.now();
        }
        try {
            Instant instant = Instant.parse(dockerTime);
            return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    public List<ContainerInfo> listContainers(boolean showAll) {
        List<Container> containers = dockerClient.listContainersCmd()
                .withShowAll(showAll)
                .exec();

        return containers.stream()
                .map(this::toContainerInfo)
                .toList();
    }

    public ContainerInfo getContainer(String containerId) {
        try {
            InspectContainerResponse inspection = dockerClient.inspectContainerCmd(containerId).exec();
            return toContainerInfo(inspection);
        } catch (Exception e) {
            log.error("컨테이너 조회 실패: {}", containerId, e);
            return null;
        }
    }

    private ContainerInfo toContainerInfo(Container container) {
        String name = container.getNames() != null && container.getNames().length > 0
                ? container.getNames()[0].replaceFirst("^/", "")
                : "unknown";

        List<ContainerInfo.PortMapping> ports = new ArrayList<>();
        if (container.getPorts() != null) {
            for (ContainerPort port : container.getPorts()) {
                ports.add(ContainerInfo.PortMapping.builder()
                        .privatePort(port.getPrivatePort() != null ? port.getPrivatePort() : 0)
                        .publicPort(port.getPublicPort() != null ? port.getPublicPort() : 0)
                        .type(port.getType() != null ? port.getType() : "tcp")
                        .build());
            }
        }

        return ContainerInfo.builder()
                .id(container.getId())
                .shortId(container.getId().substring(0, Math.min(12, container.getId().length())))
                .name(name)
                .image(container.getImage())
                .status(container.getStatus())
                .state(container.getState())
                .created(LocalDateTime.ofInstant(
                        Instant.ofEpochSecond(container.getCreated()),
                        ZoneId.systemDefault()))
                .ports(ports)
                .labels(container.getLabels())
                .build();
    }

    private ContainerInfo toContainerInfo(InspectContainerResponse inspection) {
        String name = inspection.getName();
        if (name != null && name.startsWith("/")) {
            name = name.substring(1);
        }

        InspectContainerResponse.ContainerState state = inspection.getState();

        return ContainerInfo.builder()
                .id(inspection.getId())
                .shortId(inspection.getId().substring(0, Math.min(12, inspection.getId().length())))
                .name(name)
                .image(inspection.getConfig().getImage())
                .status(state.getStatus())
                .state(state.getStatus())
                .created(parseDockerTime(inspection.getCreated()))
                .ports(new ArrayList<>())
                .labels(inspection.getConfig().getLabels())
                .build();
    }
}
