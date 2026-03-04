package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.model.User;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Manual stubbing example: We subclass UserServiceImpl and override queryByName(...) so that
 * queryByUsernameAndPassword(...) can be tested without touching UserContext / auth storage.
 */
public class UserServiceImplStubTest {

  /**
   * A manual stub via subclass override. We can control what queryByName returns.
   */
  private static class StubUserService extends UserServiceImpl {
    private User userToReturn;

    public void setUserToReturn(User user) {
      this.userToReturn = user;
    }

    @Override
    public User queryByName(String name) {
      return userToReturn;
    }
  }

  @Test
  public void testQueryByUsernameAndPassword_PasswordMismatch_ReturnsNull() {
    StubUserService service = new StubUserService();
    service.setUserToReturn(new User("mars", "correct", 0));

    User result = service.queryByUsernameAndPassword("mars", "wrong");

    assertNull(result);
  }

  @Test
  public void testQueryByUsernameAndPassword_PasswordMatch_ReturnsUser() {
    StubUserService service = new StubUserService();
    User expected = new User("mars", "pw", 0);
    service.setUserToReturn(expected);

    User result = service.queryByUsernameAndPassword("mars", "pw");

    assertEquals(expected.getName(), result.getName());
    assertEquals(expected.getPassword(), result.getPassword());
  }

  @Test
  public void testQueryByUsernameAndPassword_UserNotFound_ReturnsNull() {
    StubUserService service = new StubUserService();
    service.setUserToReturn(null);

    User result = service.queryByUsernameAndPassword("mars", "pw");

    assertNull(result);
  }
}
