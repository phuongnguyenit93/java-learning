package com.example.learning.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;

import java.util.List;
import java.util.Map;

@PropertySources({
        @PropertySource({"classpath:prop/other.properties"}),
        @PropertySource({"classpath:prop/another.properties"})
})
@Configuration
@ConfigurationProperties(prefix = "source")
@Getter @Setter
public class ResourceProperties {
    List<Map<String,String>> tagList;
    Map<String,String> tag;
}
