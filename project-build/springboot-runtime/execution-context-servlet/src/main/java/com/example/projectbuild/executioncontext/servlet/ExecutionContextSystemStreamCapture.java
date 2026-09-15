package com.example.projectbuild.executioncontext.servlet;

import com.example.projectbuild.executioncontext.context.ExecutionContextHolder;
import com.example.projectbuild.executioncontext.model.ExecutionLogEntry;
import com.example.projectbuild.executioncontext.service.ExecutionContextService;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Tees {@code System.out}/{@code System.err} to their original streams while
 * recording complete lines that belong to the current execution.
 *
 * <p>The execution id comes from {@link ExecutionContextHolder}, therefore
 * newly-created child threads inherit the correlation by default. Executor
 * threads that already existed before the request require a separate context
 * propagation strategy and are intentionally not guessed here.</p>
 */
public class ExecutionContextSystemStreamCapture
        implements InitializingBean, DisposableBean {

    private final ExecutionContextService executionContextService;

    private PrintStream originalOut;
    private PrintStream originalErr;
    private PrintStream capturedOut;
    private PrintStream capturedErr;

    public ExecutionContextSystemStreamCapture(
            ExecutionContextService executionContextService
    ) {
        this.executionContextService = executionContextService;
    }

    @Override
    public void afterPropertiesSet() {
        originalOut = System.out;
        originalErr = System.err;

        capturedOut = createCapturingStream(
                originalOut,
                "STDOUT",
                "System.out"
        );
        capturedErr = createCapturingStream(
                originalErr,
                "STDERR",
                "System.err"
        );

        System.setOut(capturedOut);
        System.setErr(capturedErr);
    }

    @Override
    public void destroy() {
        if (capturedOut != null) {
            capturedOut.flush();
        }
        if (capturedErr != null) {
            capturedErr.flush();
        }

        if (capturedOut != null && System.out == capturedOut && originalOut != null) {
            System.setOut(originalOut);
        }
        if (capturedErr != null && System.err == capturedErr && originalErr != null) {
            System.setErr(originalErr);
        }
    }

    private PrintStream createCapturingStream(
            PrintStream delegate,
            String level,
            String logger
    ) {
        return new PrintStream(
                new CorrelatedOutputStream(
                        delegate,
                        executionContextService,
                        level,
                        logger
                ),
                true,
                StandardCharsets.UTF_8
        );
    }

    private static final class CorrelatedOutputStream extends OutputStream {

        private final PrintStream delegate;
        private final ExecutionContextService executionContextService;
        private final String level;
        private final String logger;
        private final ThreadLocal<LineBuffer> buffers =
                ThreadLocal.withInitial(LineBuffer::new);

        private CorrelatedOutputStream(
                PrintStream delegate,
                ExecutionContextService executionContextService,
                String level,
                String logger
        ) {
            this.delegate = delegate;
            this.executionContextService = executionContextService;
            this.level = level;
            this.logger = logger;
        }

        @Override
        public void write(int value) {
            delegate.write(value);
            captureByte((byte) value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            delegate.write(bytes, offset, length);
            for (int index = offset; index < offset + length; index++) {
                captureByte(bytes[index]);
            }
        }

        @Override
        public void flush() {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            flush();
        }

        private void captureByte(byte value) {
            String executionId = ExecutionContextHolder.currentExecutionId();
            LineBuffer lineBuffer = buffers.get();

            if (executionId == null || executionId.isBlank()) {
                lineBuffer.reset();
                return;
            }

            if (!executionId.equals(lineBuffer.executionId)) {
                lineBuffer.reset();
                lineBuffer.executionId = executionId;
            }

            if (value == '\n') {
                publish(lineBuffer);
                lineBuffer.reset();
                return;
            }

            lineBuffer.bytes.write(value);
        }

        private void publish(LineBuffer lineBuffer) {
            byte[] raw = lineBuffer.bytes.toByteArray();
            int length = raw.length;
            if (length > 0 && raw[length - 1] == '\r') {
                length--;
            }

            String message = new String(
                    raw,
                    0,
                    length,
                    StandardCharsets.UTF_8
            );

            if (message.isEmpty()) {
                return;
            }

            try {
                executionContextService.addLog(
                        lineBuffer.executionId,
                        new ExecutionLogEntry(
                                System.currentTimeMillis(),
                                level,
                                logger,
                                Thread.currentThread().getName(),
                                message
                        )
                );
            } catch (RuntimeException ignored) {
                // Capturing diagnostics must never break the application output path.
            }
        }

        private static final class LineBuffer {
            private String executionId;
            private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            private void reset() {
                executionId = null;
                bytes.reset();
            }
        }
    }
}
