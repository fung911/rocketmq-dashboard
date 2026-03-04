package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.admin.UserMQAdminPoolManager;
import org.apache.rocketmq.dashboard.model.User;
import org.apache.rocketmq.dashboard.service.strategy.UserContext;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertSame;
import static org.mockito.Mockito.*;

/**
 * Mockito mocking example: Verifies that UserServiceImpl interacts with UserMQAdminPoolManager
 * correctly.
 */
public class UserServiceImplMockTest {

  @InjectMocks
  private UserServiceImpl userService;

  @Mock
  private UserMQAdminPoolManager userMQAdminPoolManager;

  // Not used in the tests below, but required because UserServiceImpl has @Autowired fields.
  @Mock
  private UserContext userContext;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testGetMQAdminExtForUser_BorrowsFromPool() throws Exception {
    User user = new User("mars", "pw", 0);
    MQAdminExt fakeAdmin = mock(MQAdminExt.class);

    when(userMQAdminPoolManager.borrowMQAdminExt("mars", "pw")).thenReturn(fakeAdmin);

    MQAdminExt result = userService.getMQAdminExtForUser(user);

    verify(userMQAdminPoolManager).borrowMQAdminExt("mars", "pw");
    assertSame(fakeAdmin, result);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testGetMQAdminExtForUser_NullUser_Throws() throws Exception {
    userService.getMQAdminExtForUser(null);
  }

  @Test
  public void testReturnMQAdminExtForUser_ReturnsToPool() {
    User user = new User("mars", "pw", 0);
    MQAdminExt fakeAdmin = mock(MQAdminExt.class);

    userService.returnMQAdminExtForUser(user, fakeAdmin);

    verify(userMQAdminPoolManager).returnMQAdminExt("mars", fakeAdmin);
  }

  @Test
  public void testReturnMQAdminExtForUser_NullArgs_NoPoolInteraction() {
    MQAdminExt fakeAdmin = mock(MQAdminExt.class);

    userService.returnMQAdminExtForUser(null, fakeAdmin);
    userService.returnMQAdminExtForUser(new User("mars", "pw", 0), null);

    verifyNoInteractions(userMQAdminPoolManager);
  }

  @Test
  public void testOnUserLogout_ShutsDownUserPool() {
    User user = new User("mars", "pw", 0);

    userService.onUserLogout(user);

    verify(userMQAdminPoolManager).shutdownUserPool("mars");
  }
}
