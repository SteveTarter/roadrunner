package com.tarterware.roadrunner.services;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.DescribeConfigsResult;
import org.apache.kafka.clients.admin.DescribeTopicsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.RecordsToDelete;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
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

    public void truncateTopic(String topicName)
    {
        try (AdminClient admin = AdminClient.create(kafkaAdmin.getConfigurationProperties()))
        {
            // Find the current end offsets for all partitions
            Map<TopicPartition, OffsetSpec> request = new HashMap<>();

            // Describe the topic to find how many partitions it has
            DescribeTopicsResult desc = admin.describeTopics(Collections.singleton(topicName));
            TopicDescription topicDesc = desc.allTopicNames().get().get(topicName);

            for (TopicPartitionInfo info : topicDesc.partitions())
            {
                request.put(new TopicPartition(topicName, info.partition()), OffsetSpec.latest());
            }

            Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latestOffsets = admin.listOffsets(request)
                    .all().get();

            // Tell Kafka to delete everything before those offsets
            Map<TopicPartition, RecordsToDelete> truncationMap = new HashMap<>();
            latestOffsets.forEach((tp, info) -> truncationMap.put(tp, RecordsToDelete.beforeOffset(info.offset())));

            admin.deleteRecords(truncationMap).all().get();

            logger.info("Topic {} truncated successfully, removing all items.", topicName);
        }
        catch (Exception e)
        {
            logger.error("Failed to truncate topic {}", topicName, e);
        }
    }
}