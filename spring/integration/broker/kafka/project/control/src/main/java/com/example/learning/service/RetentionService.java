package com.example.learning.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Service
public class RetentionService {
    @Autowired
    private KafkaAdmin kafkaAdmin;

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
}
