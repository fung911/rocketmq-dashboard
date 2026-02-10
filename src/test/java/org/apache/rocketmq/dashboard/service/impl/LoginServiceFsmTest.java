package org.apache.rocketmq.dashboard.service.impl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.rocketmq.dashboard.service.strategy.UserContext;
import org.apache.rocketmq.dashboard.util.UserInfoContext;
import org.apache.rocketmq.dashboard.util.WebUtil;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

public class LoginServiceFsmTest {

  private LoginServiceImpl loginService;

  @Mock
  private UserContext userContext;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private HttpSession session;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    loginService = new LoginServiceImpl();

    // Inject mock userContext into LoginServiceImpl (field injection)
    Field f = LoginServiceImpl.class.getDeclaredField("userContext");
    f.setAccessible(true);
    f.set(loginService, userContext);
  }

  @After
  public void tearDown() {
    UserInfoContext.clear();
  }

  /**
   * Transition: Start -> Authenticated Guard: session has username AND user exists
   */
  @Test
  public void testLogin_SessionHasUser_UserExists_ReturnsTrue() throws Exception {
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute(WebUtil.USER_NAME)).thenReturn("mars");
    UserInfo userInfo = new UserInfo();
    when(userContext.queryByUsername("mars")).thenReturn(userInfo);

    boolean allowed = loginService.login(request, response);

    assertTrue(allowed);
    assertSame(userInfo, UserInfoContext.get(WebUtil.USER_NAME));
    verify(response, never()).sendRedirect(anyString());
  }

  /**
   * Transition: Start -> Redirected Guard: session has username BUT user missing => redirect to
   * login
   */
  @Test
  public void testLogin_UserMissing_RedirectsAndReturnsFalse() throws Exception {
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute(WebUtil.USER_NAME)).thenReturn("mars");
    when(userContext.queryByUsername("mars")).thenReturn(null);

    when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/some-page"));
    when(request.getQueryString()).thenReturn(null);
    when(request.getContextPath()).thenReturn("");

    boolean allowed = loginService.login(request, response);

    assertFalse(allowed);
    verify(response, times(1)).sendRedirect(contains("/#/login?redirect="));
  }

  /**
   * Transition: Start -> Redirected Guard: no session (or no username) => redirect to login
   */
  @Test
  public void testLogin_NoSessionOrNoUsername_RedirectsAndReturnsFalse() throws Exception {
    when(request.getSession(false)).thenReturn(null);

    when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/anything"));
    when(request.getQueryString()).thenReturn("a=1");
    when(request.getContextPath()).thenReturn("");

    boolean allowed = loginService.login(request, response);

    assertFalse(allowed);
    verify(response, times(1)).sendRedirect(contains("/#/login?redirect="));
  }
}
