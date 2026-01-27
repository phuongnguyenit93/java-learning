package com.example.learning.configuration;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
public class TopicCreation {
    @Autowired
    private KafkaAdmin kafkaAdmin;

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

    public void updateRetention(String topicName,String retentionTime) throws Exception {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {

            ConfigResource resource =
                    new ConfigResource(ConfigResource.Type.TOPIC, topicName);

            ConfigEntry retention =
                    new ConfigEntry(TopicConfig.RETENTION_MS_CONFIG, retentionTime);

            Map<ConfigResource, Collection<AlterConfigOp>> configs = Map.of(
                    resource,
                    List.of(new AlterConfigOp(retention, AlterConfigOp.OpType.SET))
            );

            client.incrementalAlterConfigs(configs).all().get();
        }
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
