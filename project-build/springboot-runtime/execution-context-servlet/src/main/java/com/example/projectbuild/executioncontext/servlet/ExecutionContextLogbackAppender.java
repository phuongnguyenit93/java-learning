package com.example.projectbuild.executioncontext.servlet;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.example.projectbuild.executioncontext.context.ExecutionContextHolder;
import com.example.projectbuild.executioncontext.model.ExecutionLogEntry;
import com.example.projectbuild.executioncontext.service.ExecutionContextService;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

public class ExecutionContextLogbackAppender extends AppenderBase<ILoggingEvent>
        implements InitializingBean, DisposableBean {

    private final ExecutionContextService executionContextService;
    private Logger rootLogger;

    public ExecutionContextLogbackAppender(ExecutionContextService executionContextService) {
        this.executionContextService = executionContextService;
        setName("execution-context-capture");
    }

    @Override
    public void afterPropertiesSet() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext loggerContext)) {
            return;
        }

        setContext(loggerContext);
        start();
        rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        rootLogger.addAppender(this);
    }

    @Override
    protected void append(ILoggingEvent event) {
        String executionId = event.getMDCPropertyMap().get(ExecutionContextCaptureFilter.EXECUTION_ID_MDC_KEY);
        if (executionId == null || executionId.isBlank()) {
            executionId = ExecutionContextHolder.currentExecutionId();
        }
        if (executionId == null || executionId.isBlank()) {
            return;
        }

        Level level = event.getLevel();
        executionContextService.addLog(
                executionId,
                new ExecutionLogEntry(
                        event.getTimeStamp(),
                        level == null ? "" : level.levelStr,
                        event.getLoggerName(),
                        event.getThreadName(),
                        event.getFormattedMessage()
                )
        );
    }

    @Override
    public void destroy() {
        if (rootLogger != null) {
            rootLogger.detachAppender(this);
        }
        stop();
    }
}
