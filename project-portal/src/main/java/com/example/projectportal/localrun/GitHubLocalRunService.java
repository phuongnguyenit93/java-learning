package com.example.projectportal.localrun;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class GitHubLocalRunService {

    private static final String GITHUB_API_BASE = "https://api.github.com";
    private static final String GITHUB_API_VERSION = "2026-03-10";
    private static final Pattern MODULE_ID_PATTERN = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,63}$");
    private static final Pattern SOURCE_FINGERPRINT_PATTERN = Pattern.compile("^[0-9a-f]{40,64}$");

    private final ObjectMapper objectMapper;
    private final HttpClient apiClient;
    private final String token;
    private final String owner;
    private final String repository;
    private final String workflow;
    private final String releaseTag;

    public GitHubLocalRunService(
            ObjectMapper objectMapper,
            @Value("${local-run.github.token:}") String token,
            @Value("${local-run.github.owner}") String owner,
            @Value("${local-run.github.repository}") String repository,
            @Value("${local-run.github.workflow}") String workflow,
            @Value("${local-run.github.release-tag:local-run}") String releaseTag
    ) {
        this.objectMapper = objectMapper;
        this.token = resolveLocalToken(token);
        this.owner = owner;
        this.repository = repository;
        this.workflow = workflow;
        this.releaseTag = releaseTag;
        this.apiClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public BuildResult startBuild(String rawModuleId, String rawSourceFingerprint) {
        String moduleId = normalizeModuleId(rawModuleId);
        String sourceFingerprint = normalizeSourceFingerprint(rawSourceFingerprint);
        JsonNode response = sendJson(
                apiRequest("/repos/%s/%s/actions/workflows/%s/dispatches".formatted(owner, repository, workflow))
                        .POST(HttpRequest.BodyPublishers.ofString(writeJson(Map.of(
                                "ref", "main",
                                "inputs", Map.of(
                                        "moduleId", moduleId,
                                        "sourceFingerprint", sourceFingerprint
                                )
                        ))))
                        .header("Content-Type", "application/json")
                        .build()
        );

        JsonNode runIdNode = response.get("workflow_run_id");
        if (runIdNode == null || !runIdNode.canConvertToLong()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "GitHub accepted the build but did not return a workflow run id."
            );
        }

        return new BuildResult(
                Long.toString(runIdNode.asLong()),
                moduleId,
                "QUEUED"
        );
    }

    public StatusResult getStatus(String rawRunId, String rawModuleId) {
        String runId = normalizeRunId(rawRunId);
        String moduleId = normalizeModuleId(rawModuleId);
        JsonNode run = sendJson(
                apiRequest("/repos/%s/%s/actions/runs/%s".formatted(owner, repository, runId))
                        .GET()
                        .build()
        );

        String githubStatus = run.path("status").asText("");
        String conclusion = run.path("conclusion").isNull() ? "" : run.path("conclusion").asText("");
        String status;

        if ("completed".equals(githubStatus)) {
            status = "success".equals(conclusion) ? "SUCCESS" : "FAILED";
        } else if ("in_progress".equals(githubStatus)) {
            status = "BUILDING";
        } else {
            status = "QUEUED";
        }

        String message = "FAILED".equals(status)
                ? "GitHub Actions finished with conclusion: " + (conclusion.isBlank() ? "unknown" : conclusion) + "."
                : null;

        return new StatusResult(runId, moduleId, status, message);
    }

    public ArtifactResult getArtifact(String rawModuleId, String rawSourceFingerprint) {
        String moduleId = normalizeModuleId(rawModuleId);
        String sourceFingerprint = normalizeSourceFingerprint(rawSourceFingerprint);
        ReleaseAssetDescriptor asset = findReleaseAsset(moduleId);
        String fileName = jarFileName(moduleId);

        if (asset == null) {
            return new ArtifactResult(
                    moduleId,
                    false,
                    false,
                    fileName,
                    null
            );
        }

        boolean fresh = releaseAssetLabel(sourceFingerprint).equals(asset.label());

        return new ArtifactResult(
                moduleId,
                fresh,
                !fresh,
                fileName,
                fresh ? asset.browserDownloadUrl() : null
        );
    }

    public String jarFileName(String rawModuleId) {
        String moduleId = normalizeModuleId(rawModuleId);
        return moduleId.toLowerCase(Locale.ROOT).replace('_', '-') + ".jar";
    }

    private ReleaseAssetDescriptor findReleaseAsset(String moduleId) {
        JsonNode release = sendJsonAllowNotFound(
                apiRequest("/repos/%s/%s/releases/tags/%s".formatted(owner, repository, releaseTag))
                        .GET()
                        .build()
        );

        if (release == null) {
            return null;
        }

        long releaseId = release.path("id").asLong(0L);
        if (releaseId <= 0L) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub Local Run release has no release id.");
        }

        String expectedName = jarFileName(moduleId);
        for (int page = 1; page <= 10; page++) {
            JsonNode assets = sendJson(
                    apiRequest("/repos/%s/%s/releases/%d/assets?per_page=100&page=%d".formatted(
                                    owner,
                                    repository,
                                    releaseId,
                                    page
                            ))
                            .GET()
                            .build()
            );

            for (JsonNode asset : assets) {
                if (expectedName.equals(asset.path("name").asText())) {
                    return new ReleaseAssetDescriptor(
                            asset.path("label").isNull() ? "" : asset.path("label").asText(""),
                            asset.path("browser_download_url").asText("")
                    );
                }
            }

            if (assets.size() < 100) {
                break;
            }
        }

        return null;
    }

    private HttpRequest.Builder apiRequest(String path) {
        requireToken();
        return HttpRequest.newBuilder(URI.create(GITHUB_API_BASE + path))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + token)
                .header("X-GitHub-Api-Version", GITHUB_API_VERSION)
                .header("User-Agent", "java-learning-project-portal");
    }

    private JsonNode sendJson(HttpRequest request) {
        try {
            HttpResponse<String> response = apiClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = response.body() == null ? "" : response.body();
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "GitHub API returned " + response.statusCode() + ": " + body.substring(0, Math.min(body.length(), 300))
                );
            }

            String body = response.body();
            return body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub API request was interrupted.", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to call GitHub API.", exception);
        }
    }

    private JsonNode sendJsonAllowNotFound(HttpRequest request) {
        try {
            HttpResponse<String> response = apiClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return null;
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String body = response.body() == null ? "" : response.body();
                throw new ResponseStatusException(
                        HttpStatus.BAD_GATEWAY,
                        "GitHub API returned " + response.statusCode() + ": " + body.substring(0, Math.min(body.length(), 300))
                );
            }

            String body = response.body();
            return body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "GitHub API request was interrupted.", exception);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Unable to call GitHub API.", exception);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize Local Run request.", exception);
        }
    }

    private void requireToken() {
        if (token.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "GITHUB_ACTION_TOKEN is not configured for the local Project Portal."
            );
        }
    }

    private static String resolveLocalToken(String configuredToken) {
        if (configuredToken != null && !configuredToken.isBlank()) {
            return configuredToken.trim();
        }

        for (Path candidate : new Path[]{Path.of("project-portal", ".env"), Path.of(".env")}) {
            if (!Files.isRegularFile(candidate)) {
                continue;
            }

            try {
                for (String line : Files.readAllLines(candidate, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isBlank() || trimmed.startsWith("#")) {
                        continue;
                    }

                    int separator = trimmed.indexOf('=');
                    if (separator <= 0 || !"GITHUB_ACTION_TOKEN".equals(trimmed.substring(0, separator).trim())) {
                        continue;
                    }

                    String value = trimmed.substring(separator + 1).trim();
                    if (value.length() >= 2
                            && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                        value = value.substring(1, value.length() - 1);
                    }

                    return value;
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to read local Project Portal .env file.", exception);
            }
        }

        return "";
    }

    private static String normalizeModuleId(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "moduleId is required.");
        }

        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!MODULE_ID_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "moduleId is invalid.");
        }

        return normalized;
    }

    private static String normalizeRunId(String value) {
        if (value == null || !value.matches("^\\d+$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "runId is invalid.");
        }

        return value;
    }

    private static String normalizeSourceFingerprint(String value) {
        if (value == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceFingerprint is required.");
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!SOURCE_FINGERPRINT_PATTERN.matcher(normalized).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourceFingerprint is invalid.");
        }

        return normalized;
    }

    private static String releaseAssetLabel(String sourceFingerprint) {
        return "fingerprint:" + sourceFingerprint;
    }

    public record BuildResult(String runId, String moduleId, String status) {
    }

    public record StatusResult(String runId, String moduleId, String status, String message) {
    }

    public record ArtifactResult(
            String moduleId,
            boolean available,
            boolean stale,
            String fileName,
            String downloadUrl
    ) {
    }

    private record ReleaseAssetDescriptor(String label, String browserDownloadUrl) {
    }
}
