package org.apache.rocketmq.dashboard.service.impl;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.rocketmq.dashboard.config.RMQConfigure;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.verify;

public class OpsServiceImplMockTest {

    @InjectMocks
    private OpsServiceImpl opsService;

    @Mock
    private RMQConfigure configure;

    @Mock
    private GenericObjectPool<MQAdminExt> mqAdminExtPool;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void testUpdateNameSvrAddrList_ClearsPool() {
        // Arrange
        String newNameSrvList = "10.0.0.1:9876;10.0.0.2:9876";

        // Act
        opsService.updateNameSvrAddrList(newNameSrvList);

        // Assert: Verify the behavioral side effects
        verify(configure).setNamesrvAddr(newNameSrvList); // state change on config
        verify(mqAdminExtPool).clear(); // critical behavior: pool must be cleared when NameSrv changes
    }
}
