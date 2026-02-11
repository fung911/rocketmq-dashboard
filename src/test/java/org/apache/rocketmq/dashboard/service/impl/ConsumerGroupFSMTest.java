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

import com.google.common.collect.Lists;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.ConsumerGroupRollBackStat;
import org.apache.rocketmq.dashboard.model.GroupConsumeInfo;
import org.apache.rocketmq.dashboard.model.request.ConsumerConfigInfo;
import org.apache.rocketmq.dashboard.model.request.DeleteSubGroupRequest;
import org.apache.rocketmq.dashboard.model.request.ResetOffsetRequest;
import org.apache.rocketmq.dashboard.service.ClusterInfoService;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.RollbackStats;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * FSM-based tests for Consumer Group Service.
 * 
 * This test class validates the Consumer Group finite state machine:
 * 
 * States:
 * - NON_EXISTENT: Consumer group does not exist
 * - EXISTING: Consumer group is configured
 * - OFFLINE: No active consumers (count = 0)
 * - ONLINE: At least one active consumer (count > 0)
 * 
 * Transitions:
 * - T1: NON_EXISTENT → EXISTING (createAndUpdate)
 * - T2: EXISTING → EXISTING (update)
 * - T3: EXISTING → NON_EXISTENT (delete)
 * - T4: OFFLINE → ONLINE (client connects)
 * - T5: ONLINE → OFFLINE (client disconnects)
 * - T6: resetOffset when ONLINE (new method)
 * - T7: resetOffset when OFFLINE (old method fallback)
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ConsumerGroupFSMTest {

    @InjectMocks
    @Spy
    private ConsumerServiceImpl consumerService;

    @Mock
    private MQAdminExt mqAdminExt;

    @Mock
    private ClusterInfoService clusterInfoService;

    @Mock
    private RMQConfigure configure;

    @Before
    public void setUp() throws Exception {
        // Setup cluster info mock
        ClusterInfo mockClusterInfo = createClusterInfo();
        when(clusterInfoService.get()).thenReturn(mockClusterInfo);

        // Setup subscription group wrapper
        SubscriptionGroupWrapper wrapper = MockObjectUtil.createSubscriptionGroupWrapper();
        when(mqAdminExt.getAllSubscriptionGroup(anyString(), anyLong())).thenReturn(wrapper);

        // Setup consume stats
        ConsumeStats stats = MockObjectUtil.createConsumeStats();
        when(mqAdminExt.examineConsumeStats(anyString())).thenReturn(stats);

        // Setup subscription group config mock
        SubscriptionGroupConfig config = new SubscriptionGroupConfig();
        config.setGroupName("test-group");
        when(mqAdminExt.examineSubscriptionGroupConfig(anyString(), anyString())).thenReturn(config);

        // Setup namesrv address
        when(configure.getNamesrvAddr()).thenReturn("localhost:9876");
    }

    @After
    public void tearDown() {
        // Clean up if needed
    }

    // ==========================================================================
    // FSM Transition T1: NON_EXISTENT → EXISTING (Create)
    // ==========================================================================

    /**
     * TC1: Create a new consumer group
     * Transition: T1 (NON_EXISTENT → EXISTING)
     */
    @Test
    public void testT1_CreateConsumerGroup_NonExistentToExisting() throws Exception {
        // Arrange
        doNothing().when(mqAdminExt).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        ConsumerConfigInfo configInfo = createTestConsumerConfig("new-consumer-group");

        // Act
        boolean result = consumerService.createAndUpdateSubscriptionGroupConfig(configInfo);

        // Assert - Transition T1 should succeed
        assertTrue("T1: Creating new consumer group should return true", result);
        verify(mqAdminExt, atLeastOnce()).createAndUpdateSubscriptionGroupConfig(anyString(), any());
    }

    // ==========================================================================
    // FSM Transition T2: EXISTING → EXISTING (Update)
    // ==========================================================================

    /**
     * TC2: Update an existing consumer group configuration
     * Transition: T2 (EXISTING → EXISTING)
     */
    @Test
    public void testT2_UpdateConsumerGroup_ExistingToExisting() throws Exception {
        // Arrange
        doNothing().when(mqAdminExt).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        ConsumerConfigInfo configInfo = createTestConsumerConfig("existing-group");

        // Step 1: First create the consumer group (T1: NON_EXISTENT → EXISTING)
        consumerService.createAndUpdateSubscriptionGroupConfig(configInfo);

        // Step 2: Modify config and update (T2: EXISTING → EXISTING)
        configInfo.getSubscriptionGroupConfig().setRetryMaxTimes(20);

        // Act
        boolean result = consumerService.createAndUpdateSubscriptionGroupConfig(configInfo);

        // Assert - Transition T2 should succeed
        assertTrue("T2: Updating existing consumer group should return true", result);
    }

    // ==========================================================================
    // FSM Transition T3: EXISTING → NON_EXISTENT (Delete)
    // ==========================================================================

    /**
     * TC3: Delete an existing consumer group
     * Transition: T3 (EXISTING → NON_EXISTENT)
     */
    @Test
    public void testT3_DeleteConsumerGroup_ExistingToNonExistent() throws Exception {
        // Arrange
        doNothing().when(mqAdminExt).deleteSubscriptionGroup(anyString(), anyString(), anyBoolean());
        doNothing().when(mqAdminExt).deleteTopicInBroker(any(), anyString());
        doNothing().when(mqAdminExt).deleteTopicInNameServer(any(), anyString());

        DeleteSubGroupRequest request = new DeleteSubGroupRequest();
        request.setGroupName("group-to-delete");
        request.setBrokerNameList(Lists.newArrayList("broker-a"));

        // Act
        boolean result = consumerService.deleteSubGroup(request);

        // Assert - Transition T3 should succeed
        assertTrue("T3: Deleting consumer group should return true", result);
        verify(mqAdminExt, atLeastOnce()).deleteSubscriptionGroup(anyString(), anyString(), anyBoolean());
    }

    // ==========================================================================
    // FSM Transition T4/T5: Connection State (OFFLINE ↔ ONLINE)
    // ==========================================================================

    /**
     * TC4: Verify ONLINE state (connection count > 0)
     * State Check: ONLINE state is observable
     */
    @Test
    public void testT4_VerifyOnlineState_ConnectionCountGreaterThanZero() throws Exception {
        // Arrange - Mock consumer with active connections
        ConsumerConnection connection = MockObjectUtil.createConsumerConnection();
        when(mqAdminExt.examineConsumerConnectionInfo(anyString())).thenReturn(connection);

        // Act
        GroupConsumeInfo info = consumerService.queryGroup("online-group", null);

        // Assert - ONLINE state: count > 0
        assertTrue("T4: Online group should have count > 0", info.getCount() > 0);
    }

    /**
     * TC5: Verify OFFLINE state (connection count = 0)
     * State Check: OFFLINE state is observable
     */
    @Test
    public void testT5_VerifyOfflineState_ConnectionCountZero() throws Exception {
        // Arrange - Mock consumer with no connections
        ConsumerConnection emptyConnection = new ConsumerConnection();
        emptyConnection.setConnectionSet(new HashSet<>());
        when(mqAdminExt.examineConsumerConnectionInfo(anyString())).thenReturn(emptyConnection);

        // Act
        GroupConsumeInfo info = consumerService.queryGroup("offline-group", null);

        // Assert - OFFLINE state: count = 0
        assertEquals("T5: Offline group should have count = 0", 0, info.getCount());
    }

    // ==========================================================================
    // FSM Transition T6: Reset Offset when ONLINE (new method)
    // ==========================================================================

    /**
     * TC6: Reset offset when consumer is ONLINE
     * Transition: T6 (uses new resetOffsetByTimestamp method)
     */
    @Test
    public void testT6_ResetOffsetWhenOnline_UsesNewMethod() throws Exception {
        // Arrange
        Map<MessageQueue, Long> rollbackResult = new HashMap<>();
        rollbackResult.put(new MessageQueue("topic_test", "broker-a", 0), 100L);
        when(mqAdminExt.resetOffsetByTimestamp(anyString(), anyString(), anyLong(), anyBoolean()))
                .thenReturn(rollbackResult);

        ResetOffsetRequest request = createResetOffsetRequest("online-group");

        // Act
        Map<String, ConsumerGroupRollBackStat> result = consumerService.resetOffset(request);

        // Assert - T6: New method should be used, returns success
        assertNotNull("T6: Result should not be null", result);
        assertTrue("T6: Online reset should succeed", result.get("online-group").isStatus());

        // Verify new method was called
        verify(mqAdminExt).resetOffsetByTimestamp(anyString(), anyString(), anyLong(), anyBoolean());
    }

    // ==========================================================================
    // FSM Transition T7: Reset Offset when OFFLINE (old method fallback)
    // ==========================================================================

    /**
     * TC7: Reset offset when consumer is OFFLINE
     * Transition: T7 (catches CONSUMER_NOT_ONLINE, falls back to old method)
     */
    @Test
    public void testT7_ResetOffsetWhenOffline_FallsBackToOldMethod() throws Exception {
        // Arrange - First call throws CONSUMER_NOT_ONLINE, fallback to old method
        MQClientException notOnlineException = new MQClientException(
                ResponseCode.CONSUMER_NOT_ONLINE, "Consumer not online");
        when(mqAdminExt.resetOffsetByTimestamp(anyString(), anyString(), anyLong(), anyBoolean()))
                .thenThrow(notOnlineException);

        // Old method returns success
        List<RollbackStats> rollbackStatsList = Lists.newArrayList(new RollbackStats());
        when(mqAdminExt.resetOffsetByTimestampOld(anyString(), anyString(), anyLong(), anyBoolean()))
                .thenReturn(rollbackStatsList);

        ResetOffsetRequest request = createResetOffsetRequest("offline-group");

        // Act
        Map<String, ConsumerGroupRollBackStat> result = consumerService.resetOffset(request);

        // Assert - T7: Old method should be used as fallback
        assertNotNull("T7: Result should not be null", result);
        assertTrue("T7: Offline reset via old method should succeed",
                result.get("offline-group").isStatus());

        // Verify fallback to old method
        verify(mqAdminExt).resetOffsetByTimestampOld(anyString(), anyString(), anyLong(), anyBoolean());
    }

    // ==========================================================================
    // FSM Full Lifecycle Test: T1 → T2 → T3
    // ==========================================================================

    /**
     * TC8: Complete lifecycle test
     * Transitions: T1 → T2 → T3 (Create → Update → Delete)
     */
    @Test
    public void testT8_FullLifecycle_CreateUpdateDelete() throws Exception {
        // Arrange
        String groupName = "lifecycle-test-group";
        doNothing().when(mqAdminExt).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        doNothing().when(mqAdminExt).deleteSubscriptionGroup(anyString(), anyString(), anyBoolean());
        doNothing().when(mqAdminExt).deleteTopicInBroker(any(), anyString());
        doNothing().when(mqAdminExt).deleteTopicInNameServer(any(), anyString());

        // T1: Create
        ConsumerConfigInfo createConfig = createTestConsumerConfig(groupName);
        boolean createResult = consumerService.createAndUpdateSubscriptionGroupConfig(createConfig);
        assertTrue("T1: Create should succeed", createResult);

        // T2: Update
        createConfig.getSubscriptionGroupConfig().setRetryMaxTimes(30);
        boolean updateResult = consumerService.createAndUpdateSubscriptionGroupConfig(createConfig);
        assertTrue("T2: Update should succeed", updateResult);

        // T3: Delete
        DeleteSubGroupRequest deleteRequest = new DeleteSubGroupRequest();
        deleteRequest.setGroupName(groupName);
        deleteRequest.setBrokerNameList(Lists.newArrayList("broker-a"));
        boolean deleteResult = consumerService.deleteSubGroup(deleteRequest);
        assertTrue("T3: Delete should succeed", deleteResult);
    }

    // ==========================================================================
    // Helper Methods
    // ==========================================================================

    private ConsumerConfigInfo createTestConsumerConfig(String groupName) {
        ConsumerConfigInfo configInfo = new ConsumerConfigInfo();
        configInfo.setClusterNameList(Lists.newArrayList("DefaultCluster"));

        SubscriptionGroupConfig groupConfig = new SubscriptionGroupConfig();
        groupConfig.setGroupName(groupName);
        groupConfig.setRetryMaxTimes(16);
        configInfo.setSubscriptionGroupConfig(groupConfig);

        return configInfo;
    }

    private ResetOffsetRequest createResetOffsetRequest(String groupName) {
        ResetOffsetRequest request = new ResetOffsetRequest();
        request.setTopic("test-topic");
        request.setConsumerGroupList(Lists.newArrayList(groupName));
        request.setResetTime(System.currentTimeMillis() - 3600000); // 1 hour ago
        request.setForce(false);
        return request;
    }

    private ClusterInfo createClusterInfo() {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("DefaultCluster", new HashSet<>(Arrays.asList("broker-a")));
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("broker-a");
        HashMap<Long, String> brokerNameTable = new HashMap<>();
        brokerNameTable.put(0L, "localhost:10911");
        brokerData.setBrokerAddrs(brokerNameTable);
        brokerAddrTable.put("broker-a", brokerData);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        clusterInfo.setClusterAddrTable(clusterAddrTable);
        return clusterInfo;
    }
}
