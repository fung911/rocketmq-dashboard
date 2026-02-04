/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.rocketmq.dashboard.service.impl;

import com.google.common.collect.Sets;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.MessageView;
import org.apache.rocketmq.dashboard.support.AutoCloseConsumerWrapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Partition Testing for MessageService.queryMessageByTopic
 * 
 * This test class demonstrates systematic functional testing using partition
 * testing methodology
 * for the message query function, which is one of the core features of the
 * RocketMQ Dashboard.
 * 
 * Feature Under Test: MessageService.queryMessageByTopic(String topic, long
 * begin, long end)
 * 
 * Description:
 * This function allows users to query messages within a specified time range
 * for a given topic.
 * It is used for message tracking, problem troubleshooting, and business
 * verification.
 */
public class MessageQueryPartitionTest {

    @InjectMocks
    private MessageServiceImpl messageService;

    @Mock
    private AutoCloseConsumerWrapper autoCloseConsumerWrapper;

    @Mock
    private RMQConfigure configure;

    @Mock
    private DefaultMQPullConsumer consumer;

    // Test data timestamps
    private static final long VALID_BEGIN_TIME = 1000000000000L;
    private static final long VALID_END_TIME = 1000000100000L;
    private static final long MESSAGE_TIME_BEFORE_RANGE = 999999900000L;
    private static final long MESSAGE_TIME_IN_RANGE = 1000000050000L;
    private static final long MESSAGE_TIME_AFTER_RANGE = 1000000200000L;
    private static final long MESSAGE_TIME_ON_BEGIN = VALID_BEGIN_TIME;
    private static final long MESSAGE_TIME_ON_END = VALID_END_TIME;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        // Configure mock behavior for RMQConfigure
        when(configure.getAccessKey()).thenReturn("");
        when(configure.getSecretKey()).thenReturn("");
        when(configure.isUseTLS()).thenReturn(false);
    }

    /**
     * Test Case 1: Query existing topic with valid time range and messages exist
     * Partitions: P1 (Existing Topic) + P4 (Valid Time Range) + P7 (With Messages)
     * Representative Values: topic="topic_test", begin=1000000000000L,
     * end=1000000100000L
     * Expected: Returns list of MessageView objects for messages in the time range
     */
    @Test
    public void testQueryMessageByTopic_ExistingTopicWithValidRangeAndMessages() throws Exception {
        // Arrange
        String topic = "topic_test";
        long begin = VALID_BEGIN_TIME;
        long end = VALID_END_TIME;

        // Mock consumer behavior
        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);

        // Mock message queue
        Set<MessageQueue> messageQueues = Sets.newHashSet(
                new MessageQueue(topic, "broker-a", 0));
        when(consumer.fetchSubscribeMessageQueues(topic)).thenReturn(messageQueues);

        MessageQueue mq = messageQueues.iterator().next();
        when(consumer.searchOffset(eq(mq), eq(begin))).thenReturn(100L);
        when(consumer.searchOffset(eq(mq), eq(end))).thenReturn(200L);

        // Mock pull result with message in range
        MessageExt messageInRange = createMessageExt(topic, MESSAGE_TIME_IN_RANGE);
        List<MessageExt> msgList = new ArrayList<>();
        msgList.add(messageInRange);

        PullResult pullResult = mock(PullResult.class);
        when(pullResult.getPullStatus()).thenReturn(PullStatus.FOUND);
        when(pullResult.getMsgFoundList()).thenReturn(msgList);
        when(pullResult.getNextBeginOffset()).thenReturn(201L);

        when(consumer.pull(eq(mq), eq("*"), anyLong(), eq(32))).thenReturn(pullResult);

        // Act
        List<MessageView> result = messageService.queryMessageByTopic(topic, begin, end);

        // Assert
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Should return at least one message", result.size() > 0);
        Assert.assertTrue("All messages should be in time range",
                result.stream().allMatch(msg -> msg.getStoreTimestamp() >= begin && msg.getStoreTimestamp() <= end));
    }

    /**
     * Test Case 2: Query non-existent topic
     * Partitions: P2 (Non-existent Topic) + P4 (Valid Time Range)
     * Representative Values: topic="non_existent_topic", begin=1000000000000L,
     * end=1000000100000L
     * Expected: Should handle gracefully, may throw exception or return empty list
     */
    @Test
    public void testQueryMessageByTopic_NonExistentTopic() throws Exception {
        // Arrange
        String topic = "non_existent_topic";
        long begin = VALID_BEGIN_TIME;
        long end = VALID_END_TIME;

        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);

        // Mock non-existent topic - no message queues
        when(consumer.fetchSubscribeMessageQueues(topic))
                .thenThrow(new MQClientException("Topic not exist", null));

        // Act & Assert
        try {
            List<MessageView> result = messageService.queryMessageByTopic(topic, begin, end);
            // If no exception, result could be empty
            Assert.fail("Should throw RuntimeException for non-existent topic");
        } catch (RuntimeException e) {
            // Expected behavior - exception thrown for non-existent topic
            Assert.assertTrue("Exception message should contain error info",
                    e.getCause() instanceof MQClientException);
        }
    }

    /**
     * Test Case 3: Query with null topic name (boundary test)
     * Partitions: P3 (Empty Topic Name) - Null value
     * Representative Values: topic=null, begin=1000000000000L, end=1000000100000L
     * Expected: Should throw exception for null topic
     */
    @Test
    public void testQueryMessageByTopic_NullTopicName() throws Exception {
        // Arrange
        String topic = null;
        long begin = VALID_BEGIN_TIME;
        long end = VALID_END_TIME;

        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);
        when(consumer.fetchSubscribeMessageQueues(topic))
                .thenThrow(new MQClientException("Topic cannot be null", null));

        // Act & Assert
        try {
            messageService.queryMessageByTopic(topic, begin, end);
            Assert.fail("Should throw exception for null topic");
        } catch (RuntimeException e) {
            // Expected behavior
            Assert.assertNotNull("Exception should be thrown", e);
        }
    }

    /**
     * Test Case 4: Query with empty topic name (boundary test)
     * Partitions: P3 (Empty Topic Name) - Empty string
     * Representative Values: topic="", begin=1000000000000L, end=1000000100000L
     * Expected: Should throw exception or return empty list for empty topic
     */
    @Test
    public void testQueryMessageByTopic_EmptyTopicName() throws Exception {
        // Arrange
        String topic = "";
        long begin = VALID_BEGIN_TIME;
        long end = VALID_END_TIME;

        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);
        when(consumer.fetchSubscribeMessageQueues(topic))
                .thenThrow(new MQClientException("Topic cannot be empty", null));

        // Act & Assert
        try {
            messageService.queryMessageByTopic(topic, begin, end);
            Assert.fail("Should throw exception for empty topic");
        } catch (RuntimeException e) {
            // Expected behavior
            Assert.assertNotNull("Exception should be thrown", e);
        }
    }

    /**
     * Test Case 5: Query with invalid time range (begin > end)
     * Partitions: P1 (Existing Topic) + P5 (Invalid Time Range)
     * Representative Values: topic="topic_test", begin=1000000100000L,
     * end=1000000000000L
     * Expected: Should return empty list or no messages (begin > end is logically
     * invalid)
     */
    @Test
    public void testQueryMessageByTopic_InvalidTimeRange_BeginGreaterThanEnd() throws Exception {
        // Arrange
        String topic = "topic_test";
        long begin = VALID_END_TIME; // Swap begin and end
        long end = VALID_BEGIN_TIME;

        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);

        Set<MessageQueue> messageQueues = Sets.newHashSet(
                new MessageQueue(topic, "broker-a", 0));
        when(consumer.fetchSubscribeMessageQueues(topic)).thenReturn(messageQueues);

        MessageQueue mq = messageQueues.iterator().next();
        // With begin > end, searchOffset may return minOffset >= maxOffset
        when(consumer.searchOffset(eq(mq), eq(begin))).thenReturn(200L);
        when(consumer.searchOffset(eq(mq), eq(end))).thenReturn(100L);

        // Act
        List<MessageView> result = messageService.queryMessageByTopic(topic, begin, end);

        // Assert
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Should return empty list for invalid time range",
                result.isEmpty());
    }

    /**
     * Test Case 6: Query with equal begin and end times (boundary test)
     * Partitions: P1 (Existing Topic) + P5 (Invalid Time Range) - begin == end
     * Representative Values: topic="topic_test", begin=1000000000000L,
     * end=1000000000000L
     * Expected: Should return messages exactly at this timestamp or empty list
     */
    @Test
    public void testQueryMessageByTopic_EqualBeginAndEndTime() throws Exception {
        // Arrange
        String topic = "topic_test";
        long begin = VALID_BEGIN_TIME;
        long end = VALID_BEGIN_TIME; // Same as begin

        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);

        Set<MessageQueue> messageQueues = Sets.newHashSet(
                new MessageQueue(topic, "broker-a", 0));
        when(consumer.fetchSubscribeMessageQueues(topic)).thenReturn(messageQueues);

        MessageQueue mq = messageQueues.iterator().next();
        when(consumer.searchOffset(eq(mq), eq(begin))).thenReturn(100L);
        when(consumer.searchOffset(eq(mq), eq(end))).thenReturn(100L);

        // Act
        List<MessageView> result = messageService.queryMessageByTopic(topic, begin, end);

        // Assert
        Assert.assertNotNull("Result should not be null", result);
        // When begin == end, should return messages exactly at this timestamp
        // or empty if no such message exists
    }

    /**
     * Test Case 7: Query with negative begin timestamp (boundary test)
     * Partitions: P1 (Existing Topic) + P6 (Negative Timestamp)
     * Representative Values: topic="topic_test", begin=-1L, end=1000000100000L
     * Expected: May throw exception or handle as invalid input
     */
    @Test
    public void testQueryMessageByTopic_NegativeBeginTimestamp() throws Exception {
        // Arrange
        String topic = "topic_test";
        long begin = -1L; // Negative timestamp (invalid)
        long end = VALID_END_TIME;

        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);

        Set<MessageQueue> messageQueues = Sets.newHashSet(
                new MessageQueue(topic, "broker-a", 0));
        when(consumer.fetchSubscribeMessageQueues(topic)).thenReturn(messageQueues);

        MessageQueue mq = messageQueues.iterator().next();
        // searchOffset with negative time may return 0 or throw exception
        when(consumer.searchOffset(eq(mq), eq(begin))).thenReturn(0L);
        when(consumer.searchOffset(eq(mq), eq(end))).thenReturn(200L);

        // Act
        try {
            List<MessageView> result = messageService.queryMessageByTopic(topic, begin, end);
            // If no exception, verify result
            Assert.assertNotNull("Result should not be null", result);
            // Negative timestamp is handled, may return all messages from start
        } catch (Exception e) {
            // Alternative: system may reject negative timestamp
            Assert.assertNotNull("Exception may be thrown for negative timestamp", e);
        }
    }

    /**
     * Test Case 8: Query with negative end timestamp (boundary test)
     * Partitions: P1 (Existing Topic) + P6 (Negative Timestamp)
     * Representative Values: topic="topic_test", begin=1000000000000L, end=-1L
     * Expected: May throw exception or handle as invalid input
     */
    @Test
    public void testQueryMessageByTopic_NegativeEndTimestamp() throws Exception {
        // Arrange
        String topic = "topic_test";
        long begin = VALID_BEGIN_TIME;
        long end = -1L; // Negative timestamp (invalid)

        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);

        Set<MessageQueue> messageQueues = Sets.newHashSet(
                new MessageQueue(topic, "broker-a", 0));
        when(consumer.fetchSubscribeMessageQueues(topic)).thenReturn(messageQueues);

        MessageQueue mq = messageQueues.iterator().next();
        when(consumer.searchOffset(eq(mq), eq(begin))).thenReturn(100L);
        when(consumer.searchOffset(eq(mq), eq(end))).thenReturn(0L);

        // Act
        List<MessageView> result = messageService.queryMessageByTopic(topic, begin, end);

        // Assert
        Assert.assertNotNull("Result should not be null", result);
        // With end < begin, should return empty list
        Assert.assertTrue("Should return empty list for negative end time",
                result.isEmpty());
    }

    /**
     * Test Case 9: Query existing topic with valid range but no messages
     * Partitions: P1 (Existing Topic) + P4 (Valid Time Range) + P8 (Without
     * Messages)
     * Representative Values: topic="topic_test", begin=1000000000000L,
     * end=1000000100000L
     * Expected: Returns empty list (no messages in the time range)
     */
    @Test
    public void testQueryMessageByTopic_ValidRangeNoMessages() throws Exception {
        // Arrange
        String topic = "topic_test";
        long begin = VALID_BEGIN_TIME;
        long end = VALID_END_TIME;

        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);

        Set<MessageQueue> messageQueues = Sets.newHashSet(
                new MessageQueue(topic, "broker-a", 0));
        when(consumer.fetchSubscribeMessageQueues(topic)).thenReturn(messageQueues);

        MessageQueue mq = messageQueues.iterator().next();
        when(consumer.searchOffset(eq(mq), eq(begin))).thenReturn(100L);
        when(consumer.searchOffset(eq(mq), eq(end))).thenReturn(100L); // Same offset means no new messages

        // Mock pull result with no messages
        PullResult pullResult = mock(PullResult.class);
        when(pullResult.getPullStatus()).thenReturn(PullStatus.NO_NEW_MSG);
        when(consumer.pull(eq(mq), eq("*"), anyLong(), eq(32))).thenReturn(pullResult);

        // Act
        List<MessageView> result = messageService.queryMessageByTopic(topic, begin, end);

        // Assert
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Should return empty list when no messages in range",
                result.isEmpty());
    }

    /**
     * Test Case 10: Query with message timestamp exactly on begin boundary
     * Partitions: P1 (Existing Topic) + P4 (Valid Time Range) + P9 (Boundary
     * Messages)
     * Representative Values: topic="topic_test", message.timestamp = begin =
     * 1000000000000L
     * Expected: Message with timestamp == begin should be included (inclusive
     * boundary)
     */
    @Test
    public void testQueryMessageByTopic_MessageOnBeginBoundary() throws Exception {
        // Arrange
        String topic = "topic_test";
        long begin = VALID_BEGIN_TIME;
        long end = VALID_END_TIME;

        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);

        Set<MessageQueue> messageQueues = Sets.newHashSet(
                new MessageQueue(topic, "broker-a", 0));
        when(consumer.fetchSubscribeMessageQueues(topic)).thenReturn(messageQueues);

        MessageQueue mq = messageQueues.iterator().next();
        when(consumer.searchOffset(eq(mq), eq(begin))).thenReturn(100L);
        when(consumer.searchOffset(eq(mq), eq(end))).thenReturn(200L);

        // Mock message with timestamp exactly on begin boundary
        MessageExt messageOnBoundary = createMessageExt(topic, MESSAGE_TIME_ON_BEGIN);
        List<MessageExt> msgList = new ArrayList<>();
        msgList.add(messageOnBoundary);

        PullResult pullResult = mock(PullResult.class);
        when(pullResult.getPullStatus()).thenReturn(PullStatus.FOUND);
        when(pullResult.getMsgFoundList()).thenReturn(msgList);
        when(pullResult.getNextBeginOffset()).thenReturn(201L);

        when(consumer.pull(eq(mq), eq("*"), anyLong(), eq(32))).thenReturn(pullResult);

        // Act
        List<MessageView> result = messageService.queryMessageByTopic(topic, begin, end);

        // Assert
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Should include message on begin boundary", result.size() > 0);
        // Verify that message on begin boundary is included
        Assert.assertTrue("Message at begin timestamp should be included",
                result.stream().anyMatch(msg -> msg.getStoreTimestamp() == begin));
    }

    /**
     * Test Case 11: Query with message timestamp exactly on end boundary
     * Partitions: P1 (Existing Topic) + P4 (Valid Time Range) + P9 (Boundary
     * Messages)
     * Representative Values: topic="topic_test", message.timestamp = end =
     * 1000000100000L
     * Expected: Message with timestamp == end should be included (inclusive
     * boundary)
     */
    @Test
    public void testQueryMessageByTopic_MessageOnEndBoundary() throws Exception {
        // Arrange
        String topic = "topic_test";
        long begin = VALID_BEGIN_TIME;
        long end = VALID_END_TIME;

        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);

        Set<MessageQueue> messageQueues = Sets.newHashSet(
                new MessageQueue(topic, "broker-a", 0));
        when(consumer.fetchSubscribeMessageQueues(topic)).thenReturn(messageQueues);

        MessageQueue mq = messageQueues.iterator().next();
        when(consumer.searchOffset(eq(mq), eq(begin))).thenReturn(100L);
        when(consumer.searchOffset(eq(mq), eq(end))).thenReturn(200L);

        // Mock message with timestamp exactly on end boundary
        MessageExt messageOnBoundary = createMessageExt(topic, MESSAGE_TIME_ON_END);
        List<MessageExt> msgList = new ArrayList<>();
        msgList.add(messageOnBoundary);

        PullResult pullResult = mock(PullResult.class);
        when(pullResult.getPullStatus()).thenReturn(PullStatus.FOUND);
        when(pullResult.getMsgFoundList()).thenReturn(msgList);
        when(pullResult.getNextBeginOffset()).thenReturn(201L);

        when(consumer.pull(eq(mq), eq("*"), anyLong(), eq(32))).thenReturn(pullResult);

        // Act
        List<MessageView> result = messageService.queryMessageByTopic(topic, begin, end);

        // Assert
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Should include message on end boundary", result.size() > 0);
        // Verify that message on end boundary is included
        Assert.assertTrue("Message at end timestamp should be included",
                result.stream().anyMatch(msg -> msg.getStoreTimestamp() == end));
    }

    /**
     * Test Case 12: Query with messages before, in, and after the time range
     * (filter test)
     * Partitions: P1 (Existing Topic) + P4 (Valid Time Range) + P7+P9 (Mixed
     * Messages)
     * Representative Values: Multiple messages with different timestamps
     * Expected: Only messages within [begin, end] range should be returned
     */
    @Test
    public void testQueryMessageByTopic_FilterMessagesOutsideRange() throws Exception {
        // Arrange
        String topic = "topic_test";
        long begin = VALID_BEGIN_TIME;
        long end = VALID_END_TIME;

        when(autoCloseConsumerWrapper.getConsumer(any(), anyBoolean())).thenReturn(consumer);

        Set<MessageQueue> messageQueues = Sets.newHashSet(
                new MessageQueue(topic, "broker-a", 0));
        when(consumer.fetchSubscribeMessageQueues(topic)).thenReturn(messageQueues);

        MessageQueue mq = messageQueues.iterator().next();
        when(consumer.searchOffset(eq(mq), eq(begin))).thenReturn(100L);
        when(consumer.searchOffset(eq(mq), eq(end))).thenReturn(200L);

        // Create messages with different timestamps
        List<MessageExt> msgList = new ArrayList<>();
        msgList.add(createMessageExt(topic, MESSAGE_TIME_BEFORE_RANGE)); // Before range
        msgList.add(createMessageExt(topic, MESSAGE_TIME_IN_RANGE)); // In range
        msgList.add(createMessageExt(topic, MESSAGE_TIME_AFTER_RANGE)); // After range
        msgList.add(createMessageExt(topic, MESSAGE_TIME_ON_BEGIN)); // On begin boundary
        msgList.add(createMessageExt(topic, MESSAGE_TIME_ON_END)); // On end boundary

        PullResult pullResult = mock(PullResult.class);
        when(pullResult.getPullStatus()).thenReturn(PullStatus.FOUND);
        when(pullResult.getMsgFoundList()).thenReturn(msgList);
        when(pullResult.getNextBeginOffset()).thenReturn(201L);

        when(consumer.pull(eq(mq), eq("*"), anyLong(), eq(32))).thenReturn(pullResult);

        // Act
        List<MessageView> result = messageService.queryMessageByTopic(topic, begin, end);

        // Assert
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Should have filtered messages", result.size() > 0);

        // Verify all returned messages are in range [begin, end]
        for (MessageView msg : result) {
            Assert.assertTrue(
                    String.format("Message timestamp %d should be >= begin %d",
                            msg.getStoreTimestamp(), begin),
                    msg.getStoreTimestamp() >= begin);
            Assert.assertTrue(
                    String.format("Message timestamp %d should be <= end %d",
                            msg.getStoreTimestamp(), end),
                    msg.getStoreTimestamp() <= end);
        }

        // Verify messages outside range are filtered out
        Assert.assertFalse("Should not contain messages before range",
                result.stream().anyMatch(msg -> msg.getStoreTimestamp() < begin));
        Assert.assertFalse("Should not contain messages after range",
                result.stream().anyMatch(msg -> msg.getStoreTimestamp() > end));
    }

    // Helper method to create MessageExt for testing
    private MessageExt createMessageExt(String topic, long timestamp) {
        MessageExt msg = new MessageExt();
        msg.setTopic(topic);
        msg.setStoreTimestamp(timestamp);
        msg.setBody("Test message".getBytes());
        msg.setMsgId("MSG_ID_" + timestamp);
        msg.setBornTimestamp(timestamp);
        msg.setQueueId(0);
        msg.setQueueOffset(100L);
        return msg;
    }
}
