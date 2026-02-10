package org.apache.rocketmq.dashboard.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.rocketmq.dashboard.service.LoginService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class AuthInterceptorFsmTest {

  @InjectMocks
  private AuthInterceptor interceptor;

  @Mock
  private LoginService loginService;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  /**
   * Transition: Start -> Bypassed Guard: method == OPTIONS
   */
  @Test
  public void testPreHandle_OPTIONS_Bypass() throws Exception {
    when(request.getMethod()).thenReturn("OPTIONS");

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertTrue(allowed);
    verify(loginService, never()).login(any(), any());
  }

  /**
   * Transition: Start -> Bypassed Guard: URL contains "/rocketmq-dashboard/csrf-token"
   */
  @Test
  public void testPreHandle_CsrfToken_Bypass() throws Exception {
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURL())
        .thenReturn(new StringBuffer("http://localhost/rocketmq-dashboard/csrf-token"));

    boolean allowed = interceptor.preHandle(request, response, new Object());

    assertTrue(allowed);
    verify(loginService, never()).login(any(), any());
  }

  /**
   * Transition: Start -> Authenticated OR Redirected Delegation: depends on loginService.login(...)
   */
  @Test
  public void testPreHandle_NormalRequest_DelegatesToLoginService() throws Exception {
    when(request.getMethod()).thenReturn("GET");
    when(request.getRequestURL()).thenReturn(new StringBuffer("http://localhost/some-api"));

    when(loginService.login(request, response)).thenReturn(true);
    assertTrue(interceptor.preHandle(request, response, new Object()));

    when(loginService.login(request, response)).thenReturn(false);
    assertFalse(interceptor.preHandle(request, response, new Object()));

    verify(loginService, times(2)).login(request, response);
  }
}
