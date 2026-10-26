package com.example.projectbuild.executioncontext.servlet;

import com.example.projectbuild.executioncontext.service.ExecutionContextService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class ServletExecutionContextRuntimeToggleTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withConfiguration(AutoConfigurations.of(ServletExecutionContextAutoConfiguration.class));

    @Test
    void disabledByDefaultMeansNoCaptureOrQueryAndNoPublicStaticExplorer() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ExecutionContextService.class);
            assertThat(context).doesNotHaveBean(ExecutionContextCaptureFilter.class);
            assertThat(context).doesNotHaveBean(ExecutionContextQueryController.class);
            assertThat(context).doesNotHaveBean(ExecutionContextWebMvcConfiguration.class);
        });
        assertThat(new ClassPathResource("static/execution-context/explorer.html").exists()).isFalse();
    }

    @Test
    void explicitFalseStaysOff() {
        contextRunner.withPropertyValues("execution-context.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(ExecutionContextService.class));
    }

    @Test
    void explicitTrueActivatesRuntimeFeaturesAndExplorerHandler() {
        contextRunner.withPropertyValues("execution-context.enabled=true",
                        "execution-context.capture-system-streams=false",
                        "execution-context.capture-logs=false")
                .run(context -> {
                    assertThat(context).hasSingleBean(ExecutionContextService.class);
                    assertThat(context).hasSingleBean(ExecutionContextCaptureFilter.class);
                    assertThat(context).hasSingleBean(ExecutionContextWebMvcConfiguration.class);
                    assertThat(new ClassPathResource("META-INF/execution-context/ui/explorer.html").exists()).isTrue();
                });
    }
}
