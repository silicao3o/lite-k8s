package com.example.dockermonitor.controller;

import com.example.dockermonitor.model.ContainerInfo;
import com.example.dockermonitor.service.DockerService;
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

    @GetMapping("/")
    public String dashboard(Model model,
                           @RequestParam(defaultValue = "true") boolean showAll) {
        List<ContainerInfo> containers = dockerService.listContainers(showAll);

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

        return "dashboard";
    }

    @GetMapping("/containers/{id}")
    public String containerDetail(@PathVariable String id, Model model) {
        ContainerInfo container = dockerService.getContainer(id);
        if (container == null) {
            return "redirect:/";
        }

        String logs = dockerService.getContainerLogs(id);

        model.addAttribute("container", container);
        model.addAttribute("logs", logs);

        return "container-detail";
    }

    @GetMapping("/api/containers/{id}/logs")
    @ResponseBody
    public String getContainerLogs(@PathVariable String id) {
        return dockerService.getContainerLogs(id);
    }
}
