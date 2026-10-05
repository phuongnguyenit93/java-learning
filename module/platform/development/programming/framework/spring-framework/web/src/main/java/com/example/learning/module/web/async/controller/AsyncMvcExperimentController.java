package com.example.learning.module.web.async.controller;

import com.example.learning.module.web.async.filter.AsyncDispatchTraceFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@RestController
@RequestMapping("/spring-web/async")
public class AsyncMvcExperimentController {

    /**
     * README: readme/en/menu/9.AsyncMvcStreaming/AsyncMvcStreaming.md#deferred-single-result
     * Purpose: Observe that Callable work runs on the configured MVC async executor.
     */
    @GetMapping("/callable")
    public Callable<Map<String, Object>> callable() {
        HttpServletRequest request =
                ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes())
                        .getRequest();
        List<String> trace = AsyncDispatchTraceFilter.trace(request);
        String requestThread = Thread.currentThread().getName();
        trace.add("controller:REQUEST:" + requestThread);

        return () -> {
            String workerThread = Thread.currentThread().getName();
            trace.add("callable-worker:" + workerThread);
            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("requestThread", requestThread);
            evidence.put("workerThread", workerThread);
            evidence.put("differentThread", !requestThread.equals(workerThread));
            evidence.put("configuredExecutorThread", workerThread.startsWith("spring-web-mvc-"));
            evidence.put("dispatchTrace", trace);
            return evidence;
        };
    }
}
