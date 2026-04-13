package com.example.learning.component;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.actuate.beans.BeansEndpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.actuate.endpoint.web.annotation.EndpointWebExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@EndpointWebExtension(endpoint = BeansEndpoint.class)
public class CustomBeansEndpointExtension {

    private final BeansEndpoint delegate;
    private final ApplicationContext context;

    public CustomBeansEndpointExtension(BeansEndpoint delegate, ApplicationContext context) {
        this.delegate = delegate;
        this.context = context;
    }

    @ReadOperation
    public WebEndpointResponse<Map<String, Object>> getBeans() {
        Object descriptor = delegate.beans();
        Map<String, Object> modifiedResult = new HashMap<>();

        try {
            Field contextsField = descriptor.getClass().getDeclaredField("contexts");
            contextsField.setAccessible(true);
            Map<String, ?> contexts = (Map<String, ?>) contextsField.get(descriptor);

            contexts.forEach((contextId, contextData) -> {
                try {
                    Field beansField = contextData.getClass().getDeclaredField("beans");
                    beansField.setAccessible(true);
                    Map<String, ?> beans = (Map<String, ?>) beansField.get(contextData);

                    Map<String, Object> modifiedBeans = new HashMap<>();

                    beans.forEach((beanName, beanDescriptor) -> {
                        // 1. Copy thông tin cơ bản
                        Map<String, Object> descriptorMap = extractFieldsToMap(beanDescriptor);

                        try {
                            // 2. Lấy class của Bean hiện tại
                            Object beanInstance = context.getBean(beanName);
                            descriptorMap.put("className", beanInstance.getClass().getName());

                            // 3. XỬ LÝ DEPENDENCIES
                            Object depsObj = descriptorMap.get("dependencies");
                            if (depsObj != null) {
                                String[] deps = (depsObj instanceof String[]) ? (String[]) depsObj :
                                        (depsObj instanceof List) ? ((List<String>) depsObj).toArray(new String[0]) : new String[0];

                                if (deps.length > 0) {
                                    Map<String, String> depClasses = new HashMap<>();

                                    // Lấy BeanFactory để kiểm tra type khai báo
                                    ConfigurableListableBeanFactory beanFactory = (ConfigurableListableBeanFactory) context.getAutowireCapableBeanFactory();

                                    for (String depName : deps) {
                                        try {
                                            // Lấy Actual Class (Class lúc runtime)
                                            Object depInstance = context.getBean(depName);
                                            String actualClass = depInstance.getClass().getName();

                                            // Lấy Declared Type (Kiểu khai báo trong Spring Context)
                                            // getType() sẽ trả về Interface hoặc Base Class mà Spring định nghĩa cho bean này
                                            Class<?> declaredTypeClass = beanFactory.getType(depName);
                                            String declaredType = (declaredTypeClass != null) ? declaredTypeClass.getName() : "UnknownType";

                                            // Ghép lại theo format JS đang chờ: "DeclaredType | ActualClass"
                                            depClasses.put(depName, declaredType + " | " + actualClass);

                                        } catch (Exception e) {
                                            depClasses.put(depName, "Unknown | unknown_or_inner_component");
                                        }
                                    }
                                    descriptorMap.put("dependencyClasses", depClasses);
                                }
                            }

                        } catch (Exception e) {
                            e.printStackTrace();
                            descriptorMap.put("className", "unknown");
                        }
                        modifiedBeans.put(beanName, descriptorMap);
                    });

                    modifiedResult.put(contextId, Map.of("beans", modifiedBeans));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }

        return new WebEndpointResponse<>(Map.of("contexts", modifiedResult), 200);
    }

    // Hàm bổ trợ để copy toàn bộ field của một object sang Map
    private Map<String, Object> extractFieldsToMap(Object obj) {
        Map<String, Object> map = new HashMap<>();
        Field[] fields = obj.getClass().getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                map.put(field.getName(), field.get(obj));
            } catch (IllegalAccessException e) {
                // Skip nếu không lấy được
            }
        }
        return map;
    }
}
