package com.example.projectbuild.executioncontext.servlet;

import com.example.projectbuild.executioncontext.model.ExecutionSummary;
import com.example.projectbuild.executioncontext.model.ExperimentContext;
import com.example.projectbuild.executioncontext.query.ExecutionQuery;
import com.example.projectbuild.executioncontext.query.ExecutionQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/execution-context/api")
public class ExecutionContextQueryController {

    private final ExecutionQueryService executionQueryService;

    public ExecutionContextQueryController(ExecutionQueryService executionQueryService) {
        this.executionQueryService = executionQueryService;
    }

    @GetMapping("/executions")
    public List<ExecutionSummary> findExecutions(
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long from,
            @RequestParam(required = false) Long to,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String httpMethod,
            @RequestParam(required = false) String path,
            @RequestParam(required = false) String controller,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Boolean failed,
            @RequestParam(required = false) Long minDuration,
            @RequestParam(required = false) Long maxDuration
    ) {
        return executionQueryService.find(
                new ExecutionQuery(
                        limit,
                        from,
                        to,
                        search,
                        httpMethod,
                        path,
                        controller,
                        method,
                        status,
                        failed,
                        minDuration,
                        maxDuration
                )
        );
    }

    @GetMapping("/executions/latest")
    public ResponseEntity<ExperimentContext> latest() {
        return ResponseEntity.of(executionQueryService.findLatest());
    }

    @GetMapping("/executions/{executionId}")
    public ResponseEntity<ExperimentContext> byId(@PathVariable String executionId) {
        return ResponseEntity.of(executionQueryService.findById(executionId));
    }
}
