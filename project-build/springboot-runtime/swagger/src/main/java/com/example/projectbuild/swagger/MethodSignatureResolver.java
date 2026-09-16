package com.example.projectbuild.swagger;

import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

final class MethodSignatureResolver {

    private static final Pattern QUALIFIED_CLASS_NAME =
            Pattern.compile(
                    "(?<![\\w$])(?:[a-z_$][\\w$]*\\.)+([A-Z_$][\\w$]*(?:\\.[A-Z_$][\\w$]*)*)"
            );

    private MethodSignatureResolver() {
    }

    static String resolve(Method method) {
        Type[] parameterTypes = method.getGenericParameterTypes();

        List<String> renderedParameters =
                Arrays.stream(parameterTypes)
                        .map(MethodSignatureResolver::renderType)
                        .collect(Collectors.toList());

        if (method.isVarArgs() && !renderedParameters.isEmpty()) {
            int lastIndex = renderedParameters.size() - 1;
            String lastParameter = renderedParameters.get(lastIndex);

            if (lastParameter.endsWith("[]")) {
                lastParameter =
                        lastParameter.substring(0, lastParameter.length() - 2) + "...";
            }

            renderedParameters.set(lastIndex, lastParameter);
        }

        String parameters = String.join(",", renderedParameters);

        return method.getName() + "(" + parameters + ")";
    }

    static String normalize(String signature) {
        if (signature == null) {
            return null;
        }

        String compact = signature.replaceAll("\\s+", "");
        return QUALIFIED_CLASS_NAME.matcher(compact).replaceAll("$1");
    }

    private static String renderType(Type type) {
        if (type instanceof Class<?> clazz) {
            if (clazz.isArray()) {
                return renderType(clazz.getComponentType()) + "[]";
            }

            if (clazz.getEnclosingClass() != null) {
                return renderType(clazz.getEnclosingClass()) + "." + clazz.getSimpleName();
            }

            return clazz.getSimpleName();
        }

        if (type instanceof ParameterizedType parameterizedType) {
            String rawType = renderType(parameterizedType.getRawType());
            String arguments =
                    Arrays.stream(parameterizedType.getActualTypeArguments())
                            .map(MethodSignatureResolver::renderType)
                            .collect(Collectors.joining(","));
            return rawType + "<" + arguments + ">";
        }

        if (type instanceof GenericArrayType genericArrayType) {
            return renderType(genericArrayType.getGenericComponentType()) + "[]";
        }

        if (type instanceof TypeVariable<?> typeVariable) {
            return typeVariable.getName();
        }

        if (type instanceof WildcardType wildcardType) {
            Type[] lowerBounds = wildcardType.getLowerBounds();
            if (lowerBounds.length > 0) {
                return "?super" + renderType(lowerBounds[0]);
            }

            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length > 0 && upperBounds[0] != Object.class) {
                return "?extends" + renderType(upperBounds[0]);
            }

            return "?";
        }

        return type.getTypeName()
                .replaceAll("(?:[a-zA-Z_$][\\w$]*\\.)+([A-Z_$][\\w$]*)", "$1")
                .replaceAll("\\s+", "");
    }
}
