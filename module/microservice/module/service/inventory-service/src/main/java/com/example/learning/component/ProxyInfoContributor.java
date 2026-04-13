package com.example.learning.component;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class ProxyInfoContributor implements InfoContributor {
    @Autowired
    private ApplicationContext context;

    @Override
    public void contribute(Info.Builder builder) {
        Map<String, String> importantProxyClasses = new HashMap<>();
        // Bạn có thể lọc ra các Repository hoặc Service quan trọng ở đây
        String[] beanNames = context.getBeanDefinitionNames();
        for (String name : beanNames) {
            if (name.contains("Repository")) {
                importantProxyClasses.put(name, context.getBean(name).getClass().getName());
            }
        }
        builder.withDetail("bean-proxies", importantProxyClasses);
    }

}
