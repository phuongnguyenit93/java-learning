package com.example.projectbuild.swagger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SwaggerReadmeMetadataResolverTest {

    private final ObjectMapper yamlMapper =
            new ObjectMapper(new YAMLFactory());

    @Test
    void resolvesControllerChapterAndMethodAnchorFromConfiguredRelationship() throws Exception {
        String markdown = """
                # Spring AOP Proxy Mental Model

                ## <a id="proxy-mental-model">1. Mental model</a>

                Text.

                ## <a id="proxy-demo">2. Demo trong module</a>

                Demo.
                """;

        SwaggerReadmeMetadataResolver resolver =
                new SwaggerReadmeMetadataResolver(
                        resourceLoader(Map.of(
                                "classpath:META-INF/swagger/readme/vi/menu/3.Proxy/Proxy.md",
                                markdown
                        ))
                );

        JsonNode controller = yamlMapper.readTree("""
                readmeRelated:
                  file: 3.Proxy/Proxy.md
                """);

        JsonNode method = yamlMapper.readTree("""
                readmeRelated:
                  file: ""
                  anchor: proxy-demo
                """);

        SwaggerReadmeMetadataResolver.ReadmeReference controllerReference =
                resolver.resolveController(controller, "vi");

        SwaggerReadmeMetadataResolver.ReadmeReference methodReference =
                resolver.resolveMethod(
                        method,
                        controllerReference,
                        "vi"
                );

        assertEquals(
                SwaggerReadmeMetadataResolver.STATUS_LINKED,
                controllerReference.status()
        );
        assertEquals(3, controllerReference.chapterOrder());
        assertEquals(
                "Spring AOP Proxy Mental Model",
                controllerReference.chapterTitle()
        );
        assertEquals(
                "Chapter 03 · Spring AOP Proxy Mental Model",
                controllerReference.displayText()
        );
        assertEquals(
                "readme/vi/menu/3.Proxy/Proxy.md",
                controllerReference.href()
        );

        assertEquals(
                SwaggerReadmeMetadataResolver.STATUS_LINKED,
                methodReference.status()
        );
        assertEquals("proxy-demo", methodReference.anchor());
        assertEquals("2. Demo trong module", methodReference.sectionTitle());
        assertEquals(
                "README · Chapter 03 · 2. Demo trong module",
                methodReference.displayText()
        );
        assertEquals(
                "readme/vi/menu/3.Proxy/Proxy.md#proxy-demo",
                methodReference.href()
        );
    }

    @Test
    void marksMissingOrMalformedConfiguredFilesAsInvalid() throws Exception {
        SwaggerReadmeMetadataResolver resolver =
                new SwaggerReadmeMetadataResolver(
                        resourceLoader(Map.of())
                );

        JsonNode malformed = yamlMapper.readTree("""
                readmeRelated:
                  file: Proxy/Proxy.md
                """);

        JsonNode missing = yamlMapper.readTree("""
                readmeRelated:
                  file: 3.Proxy/Proxy.md
                """);

        assertEquals(
                SwaggerReadmeMetadataResolver.STATUS_INVALID_FILE,
                resolver.resolveController(malformed, "vi").status()
        );

        assertEquals(
                SwaggerReadmeMetadataResolver.STATUS_INVALID_FILE,
                resolver.resolveController(missing, "vi").status()
        );
    }

    @Test
    void marksMissingMethodAnchorAsInvalidButBlankAnchorAsUnlinked() throws Exception {
        String markdown = """
                # Advice Lifecycle

                ## <a id="success-demo">1. Success</a>
                """;

        SwaggerReadmeMetadataResolver resolver =
                new SwaggerReadmeMetadataResolver(
                        resourceLoader(Map.of(
                                "classpath:META-INF/swagger/readme/en/menu/5.Advice/Advice.md",
                                markdown
                        ))
                );

        JsonNode controller = yamlMapper.readTree("""
                readmeRelated:
                  file: 5.Advice/Advice.md
                """);

        SwaggerReadmeMetadataResolver.ReadmeReference controllerReference =
                resolver.resolveController(controller, "en");

        JsonNode missingAnchor = yamlMapper.readTree("""
                readmeRelated:
                  anchor: failure-demo
                """);

        JsonNode blankAnchor = yamlMapper.readTree("""
                readmeRelated:
                  anchor: ""
                """);

        assertEquals(
                SwaggerReadmeMetadataResolver.STATUS_INVALID_ANCHOR,
                resolver.resolveMethod(
                        missingAnchor,
                        controllerReference,
                        "en"
                ).status()
        );

        SwaggerReadmeMetadataResolver.ReadmeReference unlinked =
                resolver.resolveMethod(
                        blankAnchor,
                        controllerReference,
                        "en"
                );

        assertEquals(
                SwaggerReadmeMetadataResolver.STATUS_UNLINKED,
                unlinked.status()
        );
        assertEquals(
                "This method does not have README content yet",
                unlinked.displayText()
        );
        assertNull(unlinked.href());
    }

    @Test
    void methodFileOverrideResolvesIndependentlyFromControllerFile() throws Exception {
        SwaggerReadmeMetadataResolver resolver =
                new SwaggerReadmeMetadataResolver(
                        resourceLoader(Map.of(
                                "classpath:META-INF/swagger/readme/vi/menu/3.Proxy/Proxy.md",
                                "# Proxy\n## <a id=\"proxy-demo\">1. Proxy demo</a>\n",
                                "classpath:META-INF/swagger/readme/vi/menu/11.ProxyFactory/ProxyFactory.md",
                                "# ProxyFactory\n## <a id=\"proxy-factory-demo\">3. Demo trong module</a>\n"
                        ))
                );

        JsonNode controller = yamlMapper.readTree("""
                readmeRelated:
                  file: 3.Proxy/Proxy.md
                """);

        JsonNode method = yamlMapper.readTree("""
                readmeRelated:
                  file: 11.ProxyFactory/ProxyFactory.md
                  anchor: proxy-factory-demo
                """);

        SwaggerReadmeMetadataResolver.ReadmeReference reference =
                resolver.resolveMethod(
                        method,
                        resolver.resolveController(controller, "vi"),
                        "vi"
                );

        assertEquals(11, reference.chapterOrder());
        assertEquals("ProxyFactory", reference.chapterTitle());
        assertEquals(
                "3. Demo trong module",
                reference.sectionTitle()
        );
    }

    private static ResourceLoader resourceLoader(
            Map<String, String> resources
    ) {
        return new ResourceLoader() {
            @Override
            public Resource getResource(String location) {
                String content = resources.get(location);

                if (content == null) {
                    return new MissingResource(location);
                }

                return new ByteArrayResource(
                        content.getBytes(StandardCharsets.UTF_8),
                        location
                );
            }

            @Override
            public ClassLoader getClassLoader() {
                return SwaggerReadmeMetadataResolverTest.class.getClassLoader();
            }
        };
    }

    private static final class MissingResource extends ByteArrayResource {

        private MissingResource(String description) {
            super(new byte[0], description);
        }

        @Override
        public boolean exists() {
            return false;
        }
    }
}
