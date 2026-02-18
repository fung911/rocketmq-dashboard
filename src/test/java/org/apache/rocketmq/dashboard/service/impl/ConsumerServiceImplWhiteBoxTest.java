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
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.dashboard.model.TopicConsumerInfo;
import org.apache.rocketmq.dashboard.model.request.ResetOffsetRequest;
import org.apache.rocketmq.dashboard.model.ConsumerGroupRollBackStat;
import org.apache.rocketmq.dashboard.service.ClusterInfoService;
import org.apache.rocketmq.dashboard.service.client.ProxyAdmin;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * White-box unit tests for ConsumerServiceImpl.
 *
 * Targets methods that had 0% coverage in the JaCoCo baseline report:
 * - queryConsumeStatsList (+ transitively: toTopicConsumerInfoList,
 * getClientConnection)
 * - queryConsumeStatsListByGroupName
 * - queryConsumeStatsListByTopicName
 * - getConsumerGroup (%SYS% prefix logic)
 *
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class ConsumerServiceImplWhiteBoxTest {

    @InjectMocks
    @Spy
    private ConsumerServiceImpl consumerService;

    @Mock
    private MQAdminExt mqAdminExt;
    @Mock
    private ClusterInfoService clusterInfoService;
    @Mock
    private RMQConfigure configure;
    @Mock
    private ProxyAdmin proxyAdmin;

    @Before
    public void setUp() throws Exception {
        ClusterInfo mockClusterInfo = MockObjectUtil.createClusterInfo();
        when(clusterInfoService.get()).thenReturn(mockClusterInfo);
        when(mqAdminExt.examineConsumeStats(anyString(), anyString()))
                .thenReturn(MockObjectUtil.createConsumeStats());
        when(mqAdminExt.examineConsumeStats(anyString()))
                .thenReturn(MockObjectUtil.createConsumeStats());
        when(mqAdminExt.examineConsumerConnectionInfo(anyString()))
                .thenReturn(MockObjectUtil.createConsumerConnection());
        when(mqAdminExt.getConsumerRunningInfo(anyString(), anyString(), anyBoolean()))
                .thenReturn(MockObjectUtil.createConsumerRunningInfo());
        when(configure.getNamesrvAddr()).thenReturn("localhost:9876");
    }

    // Covers: queryConsumeStatsList , toTopicConsumerInfoList
    // getClientConnection
    @Test
    public void testQueryConsumeStatsList_withTopic() throws Exception {
        List<TopicConsumerInfo> result = consumerService.queryConsumeStatsList("topic_test", "group_test");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        assertEquals("topic_test", result.get(0).getTopic());
        assertFalse(result.get(0).getQueueStatInfoList().isEmpty());
    }

    // Covers: blank-topic branch in toTopicConsumerInfoList filter predicate
    @Test
    public void testQueryConsumeStatsList_blankTopic() throws Exception {
        List<TopicConsumerInfo> result = consumerService.queryConsumeStatsList("", "group_test");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // Covers: queryConsumeStatsListByGroupName — address split + stats
    @Test
    public void testQueryConsumeStatsListByGroupName() throws Exception {
        when(mqAdminExt.examineConsumeStats(anyString(), anyString(), isNull(), eq(3000L)))
                .thenReturn(MockObjectUtil.createConsumeStats());
        List<TopicConsumerInfo> result = consumerService.queryConsumeStatsListByGroupName("group_test",
                "127.0.0.1:10911");
        assertNotNull(result);
        assertFalse(result.isEmpty());
    }

    // Covers: queryConsumeStatsListByTopicName  — group lookup + iteration
    @Test
    public void testQueryConsumeStatsListByTopicName() throws Exception {
        GroupList groupList = new GroupList();
        HashSet<String> groups = new HashSet<>();
        groups.add("group_test");
        groupList.setGroupList(groups);
        when(mqAdminExt.queryTopicConsumeByWho(anyString())).thenReturn(groupList);
        Map<String, TopicConsumerInfo> result = consumerService.queryConsumeStatsListByTopicName("topic_test");
        assertEquals(1, result.size());
        assertNotNull(result.get("group_test"));
    }

    // Covers: getConsumerGroup %SYS% prefix stripping + null safety
    @Test
    public void testGetConsumerGroup_sysPrefix() {
        assertEquals("MY_GROUP", consumerService.getConsumerGroup("%SYS%MY_GROUP"));
        assertEquals("normal", consumerService.getConsumerGroup("normal"));
        assertNull(consumerService.getConsumerGroup(null));
    }

    // Covers: resetOffset general exception catch branch
    @Test
    public void testResetOffset_exception() throws Exception {
        when(mqAdminExt.resetOffsetByTimestamp(anyString(), anyString(), anyLong(), anyBoolean()))
                .thenThrow(new RuntimeException("error"));
        ResetOffsetRequest req = new ResetOffsetRequest();
        req.setTopic("topic_test");
        req.setConsumerGroupList(Lists.newArrayList("group_test"));
        req.setResetTime(System.currentTimeMillis());
        req.setForce(false);
        Map<String, ConsumerGroupRollBackStat> result = consumerService.resetOffset(req);
        assertFalse(result.get("group_test").isStatus());
    }
}
