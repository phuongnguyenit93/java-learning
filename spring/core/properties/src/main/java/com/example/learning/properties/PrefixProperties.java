package com.example.learning.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Getter @Setter
@Configuration
@ConfigurationProperties(prefix = "prefix")
public class PrefixProperties {
    private String single;
    private List listing;
}
