package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.model.Entry;
import org.apache.rocketmq.dashboard.model.Policy;
import org.apache.rocketmq.dashboard.model.PolicyRequest;
import org.apache.rocketmq.dashboard.service.ClusterInfoService;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * White-box tests for AclServiceImpl to increase meaningful line/branch coverage. Focus: -
 * getBrokerAddressList(): brokerName path + clusterName validation + missing cluster info path -
 * deleteAcl(): resource == null should pass "" into mqAdminExt - updateAcl(): null/empty request
 * should early-return - createAcl(): null request early-return, empty subject throws
 */
@RunWith(MockitoJUnitRunner.class)
public class AclServiceImplWhiteBoxTest {

  @Mock
  private ClusterInfoService clusterInfoService;

  @Mock
  private MQAdminExt mqAdminExt;

  @Mock
  private ClusterInfo clusterInfo;

  @Mock
  private BrokerData brokerData;

  @Spy
  @InjectMocks
  private AclServiceImpl aclService;

  @Test
  public void getBrokerAddressList_withBrokerName_returnsAllBrokerAddresses() {
    // Arrange
    when(clusterInfoService.get()).thenReturn(clusterInfo);

    // 1) clusterAddrTable: cluster -> set of brokerNames
    Map<String, Set<String>> clusterAddrTable = new HashMap<>();
    clusterAddrTable.put("anyCluster", new HashSet<>(Collections.singletonList("brokerA")));
    when(clusterInfo.getClusterAddrTable()).thenReturn(clusterAddrTable);

    // 2) brokerAddrTable: brokerName -> BrokerData (broker addresses)
    Map<String, BrokerData> brokerAddrTable = new HashMap<>();
    brokerAddrTable.put("brokerA", brokerData);
    when(clusterInfo.getBrokerAddrTable()).thenReturn(brokerAddrTable);

    // 3) brokerData.getBrokerAddrs() is HashMap<Long,String> in your dependency version
    HashMap<Long, String> brokerAddrs = new HashMap<>();
    brokerAddrs.put(0L, "127.0.0.1:10911");
    brokerAddrs.put(1L, "127.0.0.2:10911");
    when(brokerData.getBrokerAddrs()).thenReturn(brokerAddrs);

    // Act
    List<String> result = aclService.getBrokerAddressList("anyCluster", "brokerA");

    // Assert
    assertEquals(2, result.size());
    assertTrue(result.contains("127.0.0.1:10911"));
    assertTrue(result.contains("127.0.0.2:10911"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void getBrokerAddressList_withoutBrokerName_nullClusterName_throwsIllegalArgumentException() {
    // brokerName == null triggers clusterName validation
    when(clusterInfoService.get()).thenReturn(clusterInfo);
    aclService.getBrokerAddressList(null, null);
  }

  @Test(expected = RuntimeException.class)
  public void getBrokerAddressList_withoutBrokerName_clusterInfoMissing_throwsRuntimeException() {
    // brokerName == null, clusterName provided, but clusterInfoService.get() returns null
    when(clusterInfoService.get()).thenReturn(null);
    aclService.getBrokerAddressList("cluster1", null);
  }

  @Test
  public void deleteAcl_resourceNull_shouldPassEmptyStringToMqAdminExt() throws Exception {
    // Arrange: avoid depending on clusterInfo mapping; just stub address list
    doReturn(Arrays.asList("addr1:10911", "addr2:10911")).when(aclService)
        .getBrokerAddressList(eq("cluster1"), eq("brokerA"));

    // Act
    aclService.deleteAcl("cluster1", "brokerA", "User:alice", null);

    // Assert: resource null => res should be ""
    verify(mqAdminExt).deleteAcl("addr1:10911", "User:alice", "");
    verify(mqAdminExt).deleteAcl("addr2:10911", "User:alice", "");
    verifyNoMoreInteractions(mqAdminExt);
  }

  @Test
  public void updateAcl_nullRequest_shouldEarlyReturnAndDoNothing() throws Exception {
    aclService.updateAcl(null);
    // Should not call mqAdminExt at all
    verifyNoInteractions(mqAdminExt);
  }

  @Test
  public void createAcl_nullRequest_shouldReturnEmptyList() {
    Object result = aclService.createAcl(null);
    assertTrue(result instanceof List);
    assertTrue(((List<?>) result).isEmpty());
    verifyNoInteractions(mqAdminExt);
  }

  @Test(expected = IllegalArgumentException.class)
  public void createAcl_emptySubject_shouldThrowIllegalArgumentException() {
    PolicyRequest req = new PolicyRequest();
    req.setClusterName("cluster1");
    req.setBrokerName("brokerA");
    req.setSubject(""); // triggers exception

    Policy policy = new Policy();
    Entry entry = new Entry();
    entry.setResource(Collections.singletonList("Topic:Foo"));
    entry.setActions(Collections.singletonList("PUB"));
    entry.setDecision("allow");
    policy.setEntries(Collections.singletonList(entry));
    req.setPolicies(Collections.singletonList(policy));

    aclService.createAcl(req);
  }

  @Test
  public void getBrokerAddressList_withClusterName_only_shouldReturnBrokerAddresses() {
    when(clusterInfoService.get()).thenReturn(clusterInfo);

    Map<String, Set<String>> clusterAddrTable = new HashMap<>();
    clusterAddrTable.put("cluster1", new HashSet<>(Collections.singletonList("brokerA")));
    when(clusterInfo.getClusterAddrTable()).thenReturn(clusterAddrTable);

    Map<String, BrokerData> brokerAddrTable = new HashMap<>();
    brokerAddrTable.put("brokerA", brokerData);
    when(clusterInfo.getBrokerAddrTable()).thenReturn(brokerAddrTable);

    HashMap<Long, String> brokerAddrs = new HashMap<>();
    brokerAddrs.put(0L, "10.0.0.1:10911");
    when(brokerData.getBrokerAddrs()).thenReturn(brokerAddrs);

    List<String> result = aclService.getBrokerAddressList("cluster1", null);

    assertEquals(1, result.size());
    assertTrue(result.contains("10.0.0.1:10911"));
  }
}
