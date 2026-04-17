package com.bestduo_BE.config.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * /admin/** 경로에 대해 X-Admin-Key 헤더 검증. 실패 시 401을 반환하고 컨트롤러 진입을 막는다.
 */
@Component
public class AdminApiKeyInterceptor implements HandlerInterceptor {

  static final String HEADER_NAME = "X-Admin-Key";

  private final AdminApiKeyProperties properties;

  public AdminApiKeyInterceptor(AdminApiKeyProperties properties) {
    this.properties = properties;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
    String provided = request.getHeader(HEADER_NAME);
    if (provided == null || provided.isBlank() || !provided.equals(properties.getKey())) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      return false;
    }
    return true;
  }
}
