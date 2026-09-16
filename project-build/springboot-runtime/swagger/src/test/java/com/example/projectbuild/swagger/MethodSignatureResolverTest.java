package com.example.projectbuild.swagger;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MethodSignatureResolverTest {

    @Test
    void resolvesSourceCompatibleSignatureForCommonJavaTypes() throws Exception {
        Method method = Fixture.class.getDeclaredMethod(
                "example",
                String.class,
                int[].class,
                List.class,
                String[].class
        );

        assertEquals(
                "example(String,int[],List<Map<String,?extendsNumber>>,String...)",
                MethodSignatureResolver.resolve(method)
        );
    }

    @Test
    void preservesTypeVariablesAndGenericArrays() throws Exception {
        Method method = Fixture.class.getDeclaredMethod(
                "genericArray",
                Object[].class
        );

        assertEquals(
                "genericArray(T[])",
                MethodSignatureResolver.resolve(method)
        );
    }

    @Test
    void normalizesQualifiedSourceTypesWithoutFlatteningNestedClasses() {
        assertEquals(
                "example(List<Map.Entry<String,Number>>,Outer.Inner[])",
                MethodSignatureResolver.normalize(
                        "example(java.util.List<java.util.Map.Entry<java.lang.String, java.lang.Number>>, com.acme.Outer.Inner[])"
                )
        );
    }

    private static class Fixture {

        @SuppressWarnings("unused")
        void example(
                String value,
                int[] numbers,
                List<Map<String, ? extends Number>> values,
                String... names
        ) {
        }

        @SuppressWarnings("unused")
        <T> void genericArray(T[] values) {
        }
    }
}
