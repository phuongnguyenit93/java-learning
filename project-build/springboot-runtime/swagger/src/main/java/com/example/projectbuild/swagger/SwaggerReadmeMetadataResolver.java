package com.example.projectbuild.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SwaggerReadmeMetadataResolver {

    static final String STATUS_LINKED = "LINKED";
    static final String STATUS_UNLINKED = "UNLINKED";
    static final String STATUS_INVALID_FILE = "INVALID_FILE";
    static final String STATUS_INVALID_ANCHOR = "INVALID_ANCHOR";

    private static final Pattern CHAPTER_FOLDER_PATTERN =
            Pattern.compile("^(\\d+)\\.([^/]+)$");

    private static final Pattern FIRST_H1_PATTERN =
            Pattern.compile("(?m)^#\\s+(.+?)\\s*$");

    private final ResourceLoader resourceLoader;

    SwaggerReadmeMetadataResolver(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    ReadmeReference resolveController(
            JsonNode controllerNode,
            String language
    ) {
        String file = readText(
                controllerNode == null
                        ? null
                        : controllerNode.path("readmeRelated").path("file")
        );

        if (file == null || file.isBlank()) {
            return ReadmeReference.unlinked(
                    controllerUnlinkedText(language)
            );
        }

        return resolveFile(
                file,
                null,
                language,
                false
        );
    }

    ReadmeReference resolveMethod(
            JsonNode methodNode,
            ReadmeReference controllerReference,
            String language
    ) {
        JsonNode readmeRelated =
                methodNode == null
                        ? null
                        : methodNode.path("readmeRelated");

        String anchor = readText(
                readmeRelated == null
                        ? null
                        : readmeRelated.path("anchor")
        );

        String overrideFile = readText(
                readmeRelated == null
                        ? null
                        : readmeRelated.path("file")
        );

        if (anchor == null || anchor.isBlank()) {
            return ReadmeReference.unlinked(
                    methodUnlinkedText(language)
            );
        }

        String resolvedFile =
                overrideFile != null && !overrideFile.isBlank()
                        ? overrideFile
                        : controllerReference == null
                                ? null
                                : controllerReference.file();

        if (resolvedFile == null || resolvedFile.isBlank()) {
            return ReadmeReference.invalidFile(
                    resolvedFile,
                    anchor,
                    invalidFileText(language)
            );
        }

        return resolveFile(
                resolvedFile,
                anchor,
                language,
                true
        );
    }

    private ReadmeReference resolveFile(
            String configuredFile,
            String anchor,
            String language,
            boolean resolveAnchor
    ) {
        String file = normalizeRelativeFile(configuredFile);

        ChapterIdentity chapterIdentity =
                resolveChapterIdentity(file);

        if (chapterIdentity == null) {
            return ReadmeReference.invalidFile(
                    file,
                    anchor,
                    invalidFileText(language)
            );
        }

        Resource resource = resourceLoader.getResource(
                "classpath:" + classpathReadmePath(language, file)
        );

        if (!resource.exists()) {
            return ReadmeReference.invalidFile(
                    file,
                    anchor,
                    invalidFileText(language)
            );
        }

        String markdown;

        try {
            markdown = resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            return ReadmeReference.invalidFile(
                    file,
                    anchor,
                    invalidFileText(language)
            );
        }

        String chapterTitle = resolveChapterTitle(
                markdown,
                chapterIdentity.fallbackTitle()
        );

        String href = browserReadmeHref(
                language,
                file,
                anchor
        );

        if (!resolveAnchor) {
            return ReadmeReference.linkedController(
                    file,
                    chapterIdentity.order(),
                    chapterTitle,
                    href,
                    chapterDisplayText(
                            chapterIdentity.order(),
                            chapterTitle
                    )
            );
        }

        AnchorMatch anchorMatch = findAnchorHeading(
                markdown,
                anchor
        );

        if (anchorMatch == null) {
            return ReadmeReference.invalidAnchor(
                    file,
                    anchor,
                    chapterIdentity.order(),
                    chapterTitle,
                    href,
                    invalidAnchorText(language)
            );
        }

        return ReadmeReference.linkedMethod(
                file,
                anchor,
                chapterIdentity.order(),
                chapterTitle,
                anchorMatch.position(),
                anchorMatch.heading(),
                href,
                methodDisplayText(
                        chapterIdentity.order(),
                        anchorMatch.heading()
                )
        );
    }

    private static String normalizeRelativeFile(String configuredFile) {
        if (configuredFile == null) {
            return null;
        }

        String normalized = configuredFile
                .trim()
                .replace('\\', '/');

        if (normalized.startsWith("/") ||
                normalized.contains("../") ||
                normalized.equals("..")) {
            return null;
        }

        return normalized;
    }

    private static ChapterIdentity resolveChapterIdentity(String file) {
        if (file == null || file.isBlank()) {
            return null;
        }

        int separator = file.indexOf('/');

        if (separator <= 0 || separator == file.length() - 1) {
            return null;
        }

        String folder = file.substring(0, separator);
        Matcher matcher = CHAPTER_FOLDER_PATTERN.matcher(folder);

        if (!matcher.matches()) {
            return null;
        }

        int order;

        try {
            order = Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException exception) {
            return null;
        }

        return new ChapterIdentity(
                order,
                matcher.group(2)
        );
    }

    private static String resolveChapterTitle(
            String markdown,
            String fallbackTitle
    ) {
        Matcher matcher = FIRST_H1_PATTERN.matcher(markdown == null ? "" : markdown);

        if (!matcher.find()) {
            return fallbackTitle;
        }

        String title = stripHtml(matcher.group(1)).trim();

        return title.isBlank()
                ? fallbackTitle
                : title;
    }

    private static AnchorMatch findAnchorHeading(
            String markdown,
            String anchor
    ) {
        if (markdown == null ||
                anchor == null ||
                anchor.isBlank()) {
            return null;
        }

        Pattern anchorPattern = Pattern.compile(
                "(?m)^#{1,6}\\s+<a\\s+[^>]*\\bid\\s*=\\s*([\\\"'])" +
                        Pattern.quote(anchor) +
                        "\\1[^>]*>(.*?)</a>.*$",
                Pattern.CASE_INSENSITIVE
        );

        Matcher matcher = anchorPattern.matcher(markdown);

        if (!matcher.find()) {
            return null;
        }

        String heading = stripHtml(matcher.group(2)).trim();

        if (heading.isBlank()) {
            heading = anchor;
        }

        return new AnchorMatch(
                matcher.start(),
                heading
        );
    }

    private static String stripHtml(String value) {
        if (value == null) {
            return "";
        }

        return value.replaceAll("<[^>]+>", "");
    }

    private static String readText(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        return node.asText(null);
    }

    private static String normalizeLanguage(String language) {
        if (language == null || language.isBlank()) {
            return "en";
        }

        return language.trim().toLowerCase();
    }

    private static String classpathReadmePath(
            String language,
            String file
    ) {
        return "META-INF/swagger/readme/" +
                normalizeLanguage(language) +
                "/menu/" +
                file;
    }

    private static String browserReadmeHref(
            String language,
            String file,
            String anchor
    ) {
        String href = "readme/" +
                normalizeLanguage(language) +
                "/menu/" +
                file;

        if (anchor != null && !anchor.isBlank()) {
            href += "#" + anchor;
        }

        return href;
    }

    private static String chapterDisplayText(
            int chapterOrder,
            String chapterTitle
    ) {
        return String.format(
                "Chapter %02d · %s",
                chapterOrder,
                chapterTitle
        );
    }

    private static String methodDisplayText(
            int chapterOrder,
            String sectionTitle
    ) {
        return String.format(
                "README · Chapter %02d · %s",
                chapterOrder,
                sectionTitle
        );
    }

    private static String controllerUnlinkedText(String language) {
        return isVietnamese(language)
                ? "Chưa có tài liệu tương ứng trong README"
                : "No related documentation in README yet";
    }

    private static String methodUnlinkedText(String language) {
        return isVietnamese(language)
                ? "Method này chưa có nội dung README"
                : "This method does not have README content yet";
    }

    private static String invalidFileText(String language) {
        return isVietnamese(language)
                ? "Liên kết README không hợp lệ: file hoặc chapter không tồn tại"
                : "Invalid README mapping: file or chapter does not exist";
    }

    private static String invalidAnchorText(String language) {
        return isVietnamese(language)
                ? "Liên kết README không hợp lệ: không tìm thấy anchor"
                : "Invalid README mapping: anchor was not found";
    }

    private static boolean isVietnamese(String language) {
        return "vi".equals(normalizeLanguage(language));
    }

    record ReadmeReference(
            String status,
            String file,
            String anchor,
            Integer chapterOrder,
            String chapterTitle,
            Integer sectionOrder,
            String sectionTitle,
            String href,
            String displayText
    ) {

        static ReadmeReference unlinked(String displayText) {
            return new ReadmeReference(
                    STATUS_UNLINKED,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    displayText
            );
        }

        static ReadmeReference invalidFile(
                String file,
                String anchor,
                String displayText
        ) {
            return new ReadmeReference(
                    STATUS_INVALID_FILE,
                    file,
                    anchor,
                    null,
                    null,
                    null,
                    null,
                    null,
                    displayText
            );
        }

        static ReadmeReference invalidAnchor(
                String file,
                String anchor,
                Integer chapterOrder,
                String chapterTitle,
                String href,
                String displayText
        ) {
            return new ReadmeReference(
                    STATUS_INVALID_ANCHOR,
                    file,
                    anchor,
                    chapterOrder,
                    chapterTitle,
                    null,
                    null,
                    href,
                    displayText
            );
        }

        static ReadmeReference linkedController(
                String file,
                Integer chapterOrder,
                String chapterTitle,
                String href,
                String displayText
        ) {
            return new ReadmeReference(
                    STATUS_LINKED,
                    file,
                    null,
                    chapterOrder,
                    chapterTitle,
                    null,
                    null,
                    href,
                    displayText
            );
        }

        static ReadmeReference linkedMethod(
                String file,
                String anchor,
                Integer chapterOrder,
                String chapterTitle,
                Integer sectionOrder,
                String sectionTitle,
                String href,
                String displayText
        ) {
            return new ReadmeReference(
                    STATUS_LINKED,
                    file,
                    anchor,
                    chapterOrder,
                    chapterTitle,
                    sectionOrder,
                    sectionTitle,
                    href,
                    displayText
            );
        }

        boolean isLinked() {
            return STATUS_LINKED.equals(status);
        }

        Map<String, Object> toExtension() {
            Map<String, Object> extension = new LinkedHashMap<>();
            extension.put("status", status);
            extension.put("displayText", displayText);

            putIfPresent(extension, "file", file);
            putIfPresent(extension, "anchor", anchor);
            putIfPresent(extension, "chapterOrder", chapterOrder);
            putIfPresent(extension, "chapterTitle", chapterTitle);
            putIfPresent(extension, "sectionOrder", sectionOrder);
            putIfPresent(extension, "sectionTitle", sectionTitle);
            putIfPresent(extension, "href", href);

            return extension;
        }

        private static void putIfPresent(
                Map<String, Object> target,
                String key,
                Object value
        ) {
            if (value != null) {
                target.put(key, value);
            }
        }
    }

    private record ChapterIdentity(
            int order,
            String fallbackTitle
    ) {
    }

    private record AnchorMatch(
            int position,
            String heading
    ) {
    }
}
