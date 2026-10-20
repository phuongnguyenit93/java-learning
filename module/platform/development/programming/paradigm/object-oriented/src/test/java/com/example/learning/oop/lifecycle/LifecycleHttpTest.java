package com.example.learning.oop.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.learning.oop.lifecycle.controller.LifecycleExperimentController;
import com.example.learning.oop.lifecycle.exception.LifecycleExperimentExceptionHandler;
import com.example.learning.oop.lifecycle.service.LifecycleExperimentService;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class LifecycleHttpTest {
    private MockMvc http;

    @BeforeEach
    void prepare() {
        http = MockMvcBuilders.standaloneSetup(
                new LifecycleExperimentController(new LifecycleExperimentService()))
                .setControllerAdvice(new LifecycleExperimentExceptionHandler())
                .build();
    }

    @Test
    void controllerRoutesBelongOnlyToLifecycleNamespace() {
        RequestMapping prefix = LifecycleExperimentController.class.getAnnotation(RequestMapping.class);
        assertNotNull(prefix);
        assertEquals("/oop/lifecycle", prefix.value()[0]);
        Set<String> routes = Arrays.stream(LifecycleExperimentController.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(PostMapping.class))
                .filter(mapping -> mapping != null)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .collect(Collectors.toSet());
        assertEquals(Set.of("/creation", "/references", "/resources", "/publication"), routes);
    }

    @Test
    void creationProducesSerializableDomainOutput() throws Exception {
        http.perform(post("/oop/lifecycle/creation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseCode":"OOP","capacity":3,
                                 "students":["S1","S2"],"style":"BUILDER"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseCode").value("OOP"))
                .andExpect(jsonPath("$.availableSeats").value(1))
                .andExpect(jsonPath("$.students[1]").value("S2"));
    }

    @Test
    void badInputsAndInvalidJsonReceiveScopedBadRequest() throws Exception {
        http.perform(post("/oop/lifecycle/creation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"courseCode":"OOP","capacity":0,"style":"CONSTRUCTOR"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        http.perform(post("/oop/lifecycle/creation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JSON"));
    }

    @Test
    void graphEndpointShowsRootReleaseNotGcCollection() throws Exception {
        http.perform(post("/oop/lifecycle/references")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nodes":[{"id":"A","links":["B"]},{"id":"B","links":["A"]}],
                                 "initialRoots":["A"],
                                 "steps":[{"action":"RELEASE","nodeId":"A"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshots[0].reachableFromRoots.length()").value(2))
                .andExpect(jsonPath("$.snapshots[1].notReachableFromRoots.length()").value(2));
    }

    @Test
    void resourceAndPublicationEndpointsAreObservableAndIsolated() throws Exception {
        http.perform(post("/oop/lifecycle/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bodyFails":true,"outerCloseFails":true,"innerCloseFails":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryFailure").value("body failed"))
                .andExpect(jsonPath("$.suppressedFailures[0]").value("close failed: inner"))
                .andExpect(jsonPath("$.closed.outer").value(true));
        http.perform(post("/oop/lifecycle/publication")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"READY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unsafeObservedOnRegister").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.safeObservedOnRegister").value("READY"));
    }
}
