package com.example.learning;

import java.lang.reflect.Field;
import java.util.HashMap;

public class HashMapUtils {
    public static void mappingHashMapToEntity(HashMap<String, ?> params, Object instance) {
        Class <?> instanceClass = instance.getClass();

        for (String key : params.keySet()) {
            try {
                Field field = instanceClass.getDeclaredField(key);
                field.setAccessible(true);
                field.set(instance,params.get(key));
            } catch (NoSuchFieldException e) {
            } catch (IllegalAccessException e) {
            }
        }
    }
}
