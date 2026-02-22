package com.example.learning.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class TopicCreation {

    public NewTopic createCompactTopic(String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(6)
                .replicas(3)
                .compact()
                .config(TopicConfig.DELETE_RETENTION_MS_CONFIG, "10000")
                .build();
    }

    public NewTopic createDeleteTopic(String topicName) {
        return TopicBuilder.name(topicName)
                .partitions(6)
                .replicas(3)
                .config(TopicConfig.RETENTION_MS_CONFIG, "2592000000") //30 day
                .build();
    }



    @Bean
    public NewTopic depositMoneyTopic() {
        return createDeleteTopic("deposit-money-event");
    }

    @Bean
    public NewTopic withdrawMoneyTopic() {
        return createDeleteTopic("withdraw-money-event");
    }

    @Bean
    public NewTopic generalTopic() {
        return createDeleteTopic("general-message");
    }

    @Bean
    public NewTopic consumerControlTopic() {
        return createCompactTopic("consumer-control");
    }
}
