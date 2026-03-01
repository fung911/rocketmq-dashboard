package org.apache.rocketmq.dashboard.controller;

import org.apache.rocketmq.dashboard.service.impl.OpsServiceImpl;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;

/**
 * Demonstrates the use of a manual Stub (via a subclass override)
 * instead of a Mockito mock to test NamesvrController.
 */
public class NamesvrControllerStubTest {

    private NamesvrController namesvrController;
    private StubOpsService stubOpsService;

    // A manual stub created via subclassing
    private static class StubOpsService extends OpsServiceImpl {
        private String listToReturn;

        public void setStubBehavior(String returnInfo) {
            this.listToReturn = returnInfo;
        }

        @Override
        public String getNameSvrList() {
            return listToReturn;
        }
    }

    @Before
    public void setUp() throws Exception {
        namesvrController = new NamesvrController();
        stubOpsService = new StubOpsService();

        // Inject the manual stub into NamesvrController
        Field f = NamesvrController.class.getDeclaredField("opsService");
        f.setAccessible(true);
        f.set(namesvrController, stubOpsService);
    }

    @Test
    public void testGetNameSvrList_ReturnsStubbedData() {
        // Configure our manual stub to return info
        String fakeNameSrvs = "192.168.1.1:9876;192.168.1.2:9876";
        stubOpsService.setStubBehavior(fakeNameSrvs);

        Object result = namesvrController.nsaddr();

        assertEquals(fakeNameSrvs, result);
    }
}
