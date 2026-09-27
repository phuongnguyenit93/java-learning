package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/java/core/annotation/built-in")
public class BuiltInAnnotationController {

    interface ParentContract {
        String name();
    }

    static final class ChildImplementation implements ParentContract {
        @Override
        public String name() {
            return "child";
        }
    }

    @FunctionalInterface
    interface Transformer {
        String transform(String value);

        default boolean enabled() {
            return true;
        }
    }

    @GetMapping("/compiler-contracts")
    public Map<String, Object> compilerContracts() {
        ParentContract child = new ChildImplementation();
        Transformer transformer = String::toUpperCase;

        return Map.of(
                "overrideExampleCompiled", true,
                "overrideDispatchResult", child.name(),
                "functionalInterfaceExampleCompiled", true,
                "functionalInterfaceResult", transformer.transform("annotation"),
                "compileTimeNote",
                "Removing the valid override/SAM relationship while keeping the corresponding annotation would make compilation fail."
        );
    }
}
