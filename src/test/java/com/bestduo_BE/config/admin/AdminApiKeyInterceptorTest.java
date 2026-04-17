package com.bestduo_BE.config.admin;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminApiKeyInterceptorTest {

  private static final String VALID_KEY = "secret-key-123";
  private static final String HEADER = "X-Admin-Key";

  private AdminApiKeyInterceptor interceptor;
  private HttpServletRequest request;
  private HttpServletResponse response;
  private MockHttpServletRequest mockRequest;
  private MockHttpServletResponse mockResponse;

  @BeforeEach
  void setUp() {
    AdminApiKeyProperties properties = new AdminApiKeyProperties();
    properties.setKey(VALID_KEY);
    interceptor = new AdminApiKeyInterceptor(properties);

    mockRequest = new MockHttpServletRequest();
    mockResponse = new MockHttpServletResponse();
    request = mockRequest;
    response = mockResponse;
  }

  @Nested
  @DisplayName("preHandle")
  class PreHandle {

    @Test
    @DisplayName("올바른 X-Admin-Key 헤더면 true를 반환하고 요청을 통과시킨다")
    void preHandle_validKey_returnsTrue() throws Exception {
      mockRequest.addHeader(HEADER, VALID_KEY);

      boolean result = interceptor.preHandle(request, response, new Object());

      assertThat(result).isTrue();
      assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
    }

    @Test
    @DisplayName("헤더가 아예 없으면 false를 반환하고 401을 응답한다")
    void preHandle_missingHeader_returnsFalseAndUnauthorized() throws Exception {
      boolean result = interceptor.preHandle(request, response, new Object());

      assertThat(result).isFalse();
      assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("헤더 값이 빈 문자열이면 false를 반환하고 401을 응답한다")
    void preHandle_emptyHeader_returnsFalseAndUnauthorized() throws Exception {
      mockRequest.addHeader(HEADER, "");

      boolean result = interceptor.preHandle(request, response, new Object());

      assertThat(result).isFalse();
      assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }

    @Test
    @DisplayName("헤더 값이 설정된 키와 다르면 false를 반환하고 401을 응답한다")
    void preHandle_mismatchedKey_returnsFalseAndUnauthorized() throws Exception {
      mockRequest.addHeader(HEADER, "wrong-key");

      boolean result = interceptor.preHandle(request, response, new Object());

      assertThat(result).isFalse();
      assertThat(mockResponse.getStatus()).isEqualTo(HttpServletResponse.SC_UNAUTHORIZED);
    }
  }
}
