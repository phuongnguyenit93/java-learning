package com.example.learning.module.basic.service;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class DaemonThreadService {

    public Map<String, Object> inspectDaemonRules() {
        Thread requestThread = Thread.currentThread();

        Thread childThread = new Thread(
                () -> {
                    // Không cần chạy trong demo này.
                },
                "daemon-inspection-child"
        );

        boolean inheritedDaemonStatus = childThread.isDaemon();

        childThread.setDaemon(true);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requestThreadName", requestThread.getName());
        result.put("requestThreadDaemon", requestThread.isDaemon());
        result.put("childInheritedDaemon", inheritedDaemonStatus);
        result.put("childAfterSetDaemonTrue", childThread.isDaemon());
        result.put(
                "note",
                "HTTP request kết thúc không đồng nghĩa JVM kết thúc. "
                        + "Dùng DaemonJvmExitDemo.main() để quan sát JVM lifecycle."
        );

        return result;
    }
}
