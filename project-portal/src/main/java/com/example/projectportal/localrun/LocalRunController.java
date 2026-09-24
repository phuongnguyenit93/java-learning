package com.example.projectportal.localrun;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
@RestController
@RequestMapping("/api/local-run")
public class LocalRunController {

    private final GitHubLocalRunService service;

    public LocalRunController(GitHubLocalRunService service) {
        this.service = service;
    }

    @PostMapping("/build")
    public GitHubLocalRunService.BuildResult build(@RequestBody BuildRequest request) {
        return service.startBuild(request.moduleId(), request.sourceFingerprint());
    }

    @GetMapping("/status")
    public GitHubLocalRunService.StatusResult status(
            @RequestParam String runId,
            @RequestParam String moduleId
    ) {
        return service.getStatus(runId, moduleId);
    }

    @GetMapping("/artifact")
    public GitHubLocalRunService.ArtifactResult artifact(
            @RequestParam String moduleId,
            @RequestParam String sourceFingerprint
    ) {
        return service.getArtifact(moduleId, sourceFingerprint);
    }

    public record BuildRequest(String moduleId, String sourceFingerprint) {
    }
}
