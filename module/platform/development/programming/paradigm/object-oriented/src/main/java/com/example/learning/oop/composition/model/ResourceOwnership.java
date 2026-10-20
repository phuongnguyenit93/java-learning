package com.example.learning.oop.composition.model;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic fake AutoCloseable handle demonstrating explicit ownership;
 * no file, network connection or JVM-GC timing is implied.
 */
public final class ResourceOwnership {
    private ResourceOwnership() {
    }

    public static final class TrackedHandle implements AutoCloseable {
        private final List<String> trace;
        private boolean closed;
        private final boolean failWrite;

        public TrackedHandle(List<String> trace, boolean failWrite) {
            this.trace = Objects.requireNonNull(trace);
            this.failWrite = failWrite;
            trace.add("handle:open");
        }

        public boolean closed() {
            return closed;
        }

        public void write(String content) {
            if (closed) throw new IllegalStateException("Cannot write to a closed handle");
            trace.add("handle:write:" + content);
            if (failWrite) throw new IllegalStateException("Simulated write failure");
        }

        @Override
        public void close() {
            if (closed) throw new IllegalStateException("Handle already closed");
            closed = true;
            trace.add("handle:close");
        }
    }

    public static final class Exporter implements AutoCloseable {
        private final TrackedHandle handle;
        private final boolean ownsHandle;
        private final List<String> trace;
        private boolean closed;

        public Exporter(TrackedHandle handle, boolean ownsHandle, List<String> trace) {
            this.handle = Objects.requireNonNull(handle);
            this.ownsHandle = ownsHandle;
            this.trace = Objects.requireNonNull(trace);
            trace.add("exporter:created");
        }

        public void export(String text) {
            if (closed) throw new IllegalStateException("Exporter already closed");
            handle.write(text);
        }

        public boolean closed() {
            return closed;
        }

        @Override
        public void close() {
            if (closed) throw new IllegalStateException("Exporter already closed");
            closed = true;
            trace.add("exporter:close");
            if (ownsHandle) handle.close();
        }
    }
}
