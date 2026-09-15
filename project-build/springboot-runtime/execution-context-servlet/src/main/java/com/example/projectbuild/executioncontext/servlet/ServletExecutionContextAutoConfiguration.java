package com.example.projectbuild.executioncontext.servlet;

import com.example.projectbuild.executioncontext.service.DefaultExecutionContextService;
import com.example.projectbuild.executioncontext.service.ExecutionContextService;
import com.example.projectbuild.executioncontext.query.DefaultExecutionQueryService;
import com.example.projectbuild.executioncontext.query.ExecutionQueryService;
import com.example.projectbuild.executioncontext.source.ClasspathExecutionSourceContextRepository;
import com.example.projectbuild.executioncontext.source.ExecutionSourceContextRepository;
import com.example.projectbuild.executioncontext.store.ExecutionStore;
import com.example.projectbuild.executioncontext.store.InMemoryExecutionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "execution-context", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ExecutionContextProperties.class)
public class ServletExecutionContextAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ExecutionStore executionStore(ExecutionContextProperties properties) {
        return new InMemoryExecutionStore(properties.getMaxHistory());
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionSourceContextRepository executionSourceContextRepository(ObjectMapper objectMapper) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = ServletExecutionContextAutoConfiguration.class.getClassLoader();
        }
        return new ClasspathExecutionSourceContextRepository(objectMapper, classLoader);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionContextService executionContextService(
            ExecutionStore executionStore,
            ExecutionSourceContextRepository sourceContextRepository
    ) {
        return new DefaultExecutionContextService(executionStore, sourceContextRepository);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionQueryService executionQueryService(
            ExecutionContextService executionContextService
    ) {
        return new DefaultExecutionQueryService(executionContextService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionContextExportService executionContextExportService(ObjectMapper objectMapper) {
        return new ExecutionContextExportService(objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "execution-context", name = "query-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public ExecutionContextQueryController executionContextQueryController(
            ExecutionQueryService executionQueryService
    ) {
        return new ExecutionContextQueryController(executionQueryService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "execution-context", name = "export-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public ExecutionContextExportController executionContextExportController(
            ExecutionQueryService executionQueryService,
            ExecutionContextExportService exportService
    ) {
        return new ExecutionContextExportController(executionQueryService, exportService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionContextCaptureFilter executionContextCaptureFilter(
            ExecutionContextService executionContextService,
            ExecutionContextProperties properties
    ) {
        return new ExecutionContextCaptureFilter(executionContextService, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionContextHandlerInterceptor executionContextHandlerInterceptor(
            ExecutionContextService executionContextService
    ) {
        return new ExecutionContextHandlerInterceptor(executionContextService);
    }

    @Bean
    @ConditionalOnMissingBean
    public ExecutionContextWebMvcConfiguration executionContextWebMvcConfiguration(
            ExecutionContextHandlerInterceptor interceptor
    ) {
        return new ExecutionContextWebMvcConfiguration(interceptor);
    }

    @Bean
    @ConditionalOnClass(name = "ch.qos.logback.classic.Logger")
    @ConditionalOnProperty(prefix = "execution-context", name = "capture-logs", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public ExecutionContextLogbackAppender executionContextLogbackAppender(
            ExecutionContextService executionContextService
    ) {
        return new ExecutionContextLogbackAppender(executionContextService);
    }

    @Bean
    @ConditionalOnProperty(prefix = "execution-context", name = "capture-system-streams", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public ExecutionContextSystemStreamCapture executionContextSystemStreamCapture(
            ExecutionContextService executionContextService
    ) {
        return new ExecutionContextSystemStreamCapture(executionContextService);
    }
}
