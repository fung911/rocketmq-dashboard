/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.dashboard.model.request.TopicConfigInfo;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.apache.rocketmq.common.TopicAttributes.TOPIC_MESSAGE_TYPE_ATTRIBUTE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Partition Testing for TopicServiceImpl.createOrUpdate(TopicConfigInfo)
 *
 * Feature Under Test: createOrUpdate(TopicConfigInfo req)
 *
 * Key behavior in implementation: 1) Copies request -> TopicConfig via BeanUtils. 2) If
 * req.messageType is blank, defaults to TopicMessageType.NORMAL. 3) Resolves target brokers from
 * (clusterNameList, brokerNameList). 4) For each brokerName, calls
 * mqAdminExt.createAndUpdateTopicConfig(brokerAddr, topicConfig). 5) Wraps/propagates exceptions as
 * RuntimeException.
 *
 * Partitioning dimensions (Equivalence Partitioning + Boundary Value Analysis):
 *
 * D1 MessageType: - M1: blank / null / whitespace -> should default to NORMAL - M2: specified
 * (e.g., "FIFO") -> should be used as-is
 *
 * D2 Target broker selection: - B1: clusterNameList only (valid cluster -> brokers) - B2:
 * brokerNameList only (valid broker names) - B3: both clusterNameList + brokerNameList (union) -
 * B4: neither provided (empty targets) -> no create/update calls
 *
 * D3 Invalid environment / mapping: - E1: unknown cluster name ->
 * clusterAddrTable.get(cluster)=null -> RuntimeException - E2: broker name not in brokerAddrTable
 * -> NPE -> RuntimeException
 */
public class TopicCreateOrUpdatePartitionTest {

  @InjectMocks
  private TopicServiceImpl topicService;

  @Mock
  private MQAdminExt mqAdminExt;

  @Mock
  private ClusterInfo clusterInfo;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  /**
   * Test Case 1 (M1 + B1): Blank messageType + clusterNameList only Expect: messageType defaults to
   * NORMAL; createAndUpdateTopicConfig called for each broker in the cluster.
   */
  @Test
  public void testCreateOrUpdate_BlankMessageType_ClusterTargets_DefaultsToNormal()
      throws Exception {
    // Arrange
    TopicConfigInfo req = new TopicConfigInfo();
    req.setTopicName("topic_test");
    req.setReadQueueNums(4);
    req.setWriteQueueNums(4);
    req.setPerm(6);
    req.setOrder(false);
    req.setMessageType("   "); // blank -> should default
    req.setClusterNameList(Collections.singletonList("cluster-a"));
    req.setBrokerNameList(null);

    Map<String, Set<String>> clusterAddrTable = new HashMap<>();
    clusterAddrTable.put("cluster-a", new HashSet<>(Arrays.asList("broker-1", "broker-2")));

    Map<String, BrokerData> brokerAddrTable = new HashMap<>();
    brokerAddrTable.put("broker-1", mockBrokerData("127.0.0.1:10911"));
    brokerAddrTable.put("broker-2", mockBrokerData("127.0.0.1:10912"));

    when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
    when(clusterInfo.getClusterAddrTable()).thenReturn(clusterAddrTable);
    when(clusterInfo.getBrokerAddrTable()).thenReturn(brokerAddrTable);

    // Capture TopicConfig passed into admin call
    ArgumentCaptor<TopicConfig> cfgCaptor = ArgumentCaptor.forClass(TopicConfig.class);

    // Act
    topicService.createOrUpdate(req);

    // Assert: called twice (two brokers)
    verify(mqAdminExt, times(2)).createAndUpdateTopicConfig(any(), cfgCaptor.capture());

    // Assert: message type attribute defaults to NORMAL
    // Key format in code: "+" + TOPIC_MESSAGE_TYPE_ATTRIBUTE.getName()
    TopicConfig captured = cfgCaptor.getAllValues().get(0);
    Assert.assertNotNull(captured.getAttributes());
    String key = "+" + TOPIC_MESSAGE_TYPE_ATTRIBUTE.getName();
    Assert.assertEquals("NORMAL", captured.getAttributes().get(key));
  }

  /**
   * Test Case 2 (M2 + B2): Specified messageType + brokerNameList only Expect: messageType
   * preserved; createAndUpdateTopicConfig called once per brokerNameList element.
   */
  @Test
  public void testCreateOrUpdate_SpecifiedMessageType_BrokerTargets_PreservesMessageType()
      throws Exception {
    // Arrange
    TopicConfigInfo req = new TopicConfigInfo();
    req.setTopicName("topic_test");
    req.setReadQueueNums(4);
    req.setWriteQueueNums(4);
    req.setPerm(6);
    req.setOrder(false);
    req.setMessageType("FIFO");
    req.setClusterNameList(null);
    req.setBrokerNameList(Arrays.asList("broker-1", "broker-2"));

    // cluster info still required by implementation
    when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
    when(clusterInfo.getClusterAddrTable()).thenReturn(Collections.emptyMap());

    Map<String, BrokerData> brokerAddrTable = new HashMap<>();
    brokerAddrTable.put("broker-1", mockBrokerData("127.0.0.1:10911"));
    brokerAddrTable.put("broker-2", mockBrokerData("127.0.0.1:10912"));
    when(clusterInfo.getBrokerAddrTable()).thenReturn(brokerAddrTable);

    ArgumentCaptor<TopicConfig> cfgCaptor = ArgumentCaptor.forClass(TopicConfig.class);

    // Act
    topicService.createOrUpdate(req);

    // Assert
    verify(mqAdminExt, times(2)).createAndUpdateTopicConfig(any(), cfgCaptor.capture());

    TopicConfig captured = cfgCaptor.getAllValues().get(0);
    String key = "+" + TOPIC_MESSAGE_TYPE_ATTRIBUTE.getName();
    Assert.assertEquals("FIFO", captured.getAttributes().get(key));
  }

  /**
   * Test Case 3 (B3): Both clusterNameList and brokerNameList provided Expect: union of brokers, no
   * duplicates.
   */
  @Test
  public void testCreateOrUpdate_ClusterAndBrokerTargets_UnionNoDuplicates() throws Exception {
    // Arrange
    TopicConfigInfo req = new TopicConfigInfo();
    req.setTopicName("topic_test");
    req.setReadQueueNums(4);
    req.setWriteQueueNums(4);
    req.setPerm(6);
    req.setOrder(false);
    req.setMessageType(null); // defaults
    req.setClusterNameList(Collections.singletonList("cluster-a"));
    req.setBrokerNameList(Arrays.asList("broker-2", "broker-3")); // broker-2 overlaps

    Map<String, Set<String>> clusterAddrTable = new HashMap<>();
    clusterAddrTable.put("cluster-a", new HashSet<>(Arrays.asList("broker-1", "broker-2")));

    Map<String, BrokerData> brokerAddrTable = new HashMap<>();
    brokerAddrTable.put("broker-1", mockBrokerData("127.0.0.1:10911"));
    brokerAddrTable.put("broker-2", mockBrokerData("127.0.0.1:10912"));
    brokerAddrTable.put("broker-3", mockBrokerData("127.0.0.1:10913"));

    when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
    when(clusterInfo.getClusterAddrTable()).thenReturn(clusterAddrTable);
    when(clusterInfo.getBrokerAddrTable()).thenReturn(brokerAddrTable);

    // Act
    topicService.createOrUpdate(req);

    // Assert: broker-1, broker-2, broker-3 => 3 calls
    verify(mqAdminExt, times(3)).createAndUpdateTopicConfig(any(), any(TopicConfig.class));
  }

  /**
   * Test Case 4 (B4): Neither clusterNameList nor brokerNameList provided Expect: no
   * createAndUpdateTopicConfig calls (no targets).
   */
  @Test
  public void testCreateOrUpdate_NoTargets_NoAdminCalls() throws Exception {
    // Arrange
    TopicConfigInfo req = new TopicConfigInfo();
    req.setTopicName("topic_test");
    req.setReadQueueNums(4);
    req.setWriteQueueNums(4);
    req.setPerm(6);
    req.setOrder(false);
    req.setMessageType(null);
    req.setClusterNameList(Collections.emptyList());
    req.setBrokerNameList(Collections.emptyList());

    when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
    when(clusterInfo.getClusterAddrTable()).thenReturn(Collections.emptyMap());
    when(clusterInfo.getBrokerAddrTable()).thenReturn(Collections.emptyMap());

    // Act
    topicService.createOrUpdate(req);

    // Assert
    verify(mqAdminExt, never()).createAndUpdateTopicConfig(any(), any(TopicConfig.class));
  }

  /**
   * Test Case 5 (E1): Unknown cluster name -> clusterAddrTable.get(cluster) returns null ->
   * addAll(null) -> RuntimeException Expect: RuntimeException.
   */
  @Test
  public void testCreateOrUpdate_UnknownClusterName_ThrowsRuntimeException() throws Exception {
    // Arrange
    TopicConfigInfo req = new TopicConfigInfo();
    req.setTopicName("topic_test");
    req.setReadQueueNums(4);
    req.setWriteQueueNums(4);
    req.setPerm(6);
    req.setOrder(false);
    req.setMessageType(null);
    req.setClusterNameList(Collections.singletonList("unknown-cluster"));
    req.setBrokerNameList(null);

    Map<String, Set<String>> clusterAddrTable = new HashMap<>();
    // intentionally DO NOT put "unknown-cluster"

    when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
    when(clusterInfo.getClusterAddrTable()).thenReturn(clusterAddrTable);
    when(clusterInfo.getBrokerAddrTable()).thenReturn(Collections.emptyMap());

    // Act + Assert
    try {
      topicService.createOrUpdate(req);
      Assert.fail("Expected RuntimeException for unknown clusterName");
    } catch (RuntimeException ex) {
      Assert.assertNotNull(ex);
    }
  }

  /**
   * Test Case 6 (E2): Broker name exists in targets but missing in brokerAddrTable -> NPE when
   * selecting broker addr. Expect: RuntimeException.
   */
  @Test
  public void testCreateOrUpdate_BrokerMissingInBrokerAddrTable_ThrowsRuntimeException()
      throws Exception {
    // Arrange
    TopicConfigInfo req = new TopicConfigInfo();
    req.setTopicName("topic_test");
    req.setReadQueueNums(4);
    req.setWriteQueueNums(4);
    req.setPerm(6);
    req.setOrder(false);
    req.setMessageType(null);
    req.setClusterNameList(null);
    req.setBrokerNameList(Collections.singletonList("broker-missing"));

    when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
    when(clusterInfo.getClusterAddrTable()).thenReturn(Collections.emptyMap());
    when(clusterInfo.getBrokerAddrTable()).thenReturn(Collections.emptyMap()); // missing
                                                                               // broker-missing

    // Act + Assert
    try {
      topicService.createOrUpdate(req);
      Assert.fail("Expected RuntimeException for broker missing in brokerAddrTable");
    } catch (RuntimeException ex) {
      Assert.assertNotNull(ex);
    }
  }

  private BrokerData mockBrokerData(String selectedAddr) {
    BrokerData brokerData = mock(BrokerData.class);
    when(brokerData.selectBrokerAddr()).thenReturn(selectedAddr);
    return brokerData;
  }
}
