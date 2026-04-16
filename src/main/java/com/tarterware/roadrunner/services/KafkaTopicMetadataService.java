package com.tarterware.roadrunner.services;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.common.config.ConfigResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

@Service
public class KafkaTopicMetadataService
{
    private final KafkaAdmin kafkaAdmin;
    private static final Logger logger = LoggerFactory.getLogger(KafkaTopicMetadataService.class);

    public KafkaTopicMetadataService(KafkaAdmin kafkaAdmin)
    {
        this.kafkaAdmin = kafkaAdmin;
    }

    public Duration getTopicRetention(String topicName)
    {
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties()))
        {
            ConfigResource resource = new ConfigResource(ConfigResource.Type.TOPIC, topicName);
            DescribeConfigsResult result = client.describeConfigs(Collections.singleton(resource));

            Config config = result.all().get(10, TimeUnit.SECONDS).get(resource);

            // "retention.ms" is the specific key for time-based retention
            String retentionMs = config.get("retention.ms").value();

            return Duration.ofMillis(Long.parseLong(retentionMs));
        }
        catch (Exception e)
        {
            // Fallback to a safe default if the topic doesn't exist or isn't accessible
            logger.warn("Error getting retention for topic: " + topicName +
                    "Falling back to 7 days", e);

            return Duration.ofDays(7);
        }
    }
}