package com.example.learning.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/java/core/reflection/generic")
public class GenericReflectionController {

    static final class Repository<T extends Number & Comparable<T>> {
        List<? extends T> values;
    }

    @GetMapping("/generic-signature")
    public Map<String, Object> genericSignature() throws NoSuchFieldException {
        Field field = Repository.class.getDeclaredField("values");
        Type rawFieldType = field.getType();
        ParameterizedType parameterized = (ParameterizedType) field.getGenericType();
        WildcardType wildcard = (WildcardType) parameterized.getActualTypeArguments()[0];
        TypeVariable<Class<Repository>> variable = Repository.class.getTypeParameters()[0];

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rawFieldType", rawFieldType.getTypeName());
        result.put("genericFieldType", field.getGenericType().getTypeName());
        result.put("parameterizedRawType", parameterized.getRawType().getTypeName());
        result.put("wildcardUpperBounds", Arrays.stream(wildcard.getUpperBounds()).map(Type::getTypeName).toList());
        result.put("typeVariable", variable.getName());
        result.put("typeVariableBounds", Arrays.stream(variable.getBounds()).map(Type::getTypeName).toList());
        return result;
    }
}
