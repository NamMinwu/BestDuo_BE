package com.bestduo_BE.common.infra.riot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import com.bestduo_BE.common.infra.riot.exception.RiotApiException;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

/**
 * 회귀 테스트 — 에러 경로에서 X-Riot-Token API Key 리터럴이 로그에 유출되지 않는지 검증한다.
 *
 * <p>PR-4(로그 민감값 마스킹 점검)의 일환: RiotApiHttpAdapter는 현재 key를 참조하지 않지만,
 * 미래 회귀(메시지 포맷에 헤더 덤프를 추가하거나 wire-level DEBUG 로거를 켜는 등)를 막기 위해
 * 실제 에러 흐름을 돌려서 ROOT logger 캡처본에 key 리터럴이 들어가지 않음을 확인한다.
 */
class RiotApiSecretRedactionTest {

  private static final String SECRET_KEY = "RGAPI-redaction-test-000000000000";

  private Logger rootLogger;
  private ListAppender<ILoggingEvent> logAppender;
  private RiotRateLimitInterceptor interceptor;
  private RestTemplate restTemplate;
  private MockRestServiceServer mockServer;
  private RiotApiHttpAdapter adapter;

  @BeforeEach
  void setUp() {
    rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    logAppender = new ListAppender<>();
    logAppender.start();
    rootLogger.addAppender(logAppender);

    RiotApiMetrics metrics = new RiotApiMetrics(new SimpleMeterRegistry());
    interceptor = new RiotRateLimitInterceptor(
        SECRET_KEY,
        new DualWindowRateLimiter(10, Duration.ofSeconds(1), 60, Duration.ofMinutes(2)),
        Clock.systemUTC(),
        metrics
    );
    restTemplate = new RestTemplate();
    restTemplate.setInterceptors(List.of(interceptor));
    mockServer = MockRestServiceServer.bindTo(restTemplate).build();

    adapter = new RiotApiHttpAdapter(restTemplate, restTemplate, metrics);
  }

  @AfterEach
  void tearDown() {
    rootLogger.detachAppender(logAppender);
    logAppender.stop();
  }

  @Test
  @DisplayName("Riot API 에러 경로 — log 출력에 X-Riot-Token 값이 포함되지 않는다")
  void errorPath_doesNotLeakApiKey() {
    mockServer.expect(once(), requestTo(
            "/lol/match/v5/matches/by-puuid/some-puuid/ids?count=5"))
        .andRespond(withServerError());

    assertThatThrownBy(() -> adapter.findRecentMatchIds("some-puuid", 5))
        .isInstanceOf(RiotApiException.class);

    assertLogsDoNotContain(SECRET_KEY);
  }

  private void assertLogsDoNotContain(String secret) {
    assertThat(logAppender.list)
        .as("적어도 하나 이상의 로그가 캡처되어야 의미 있는 검증이다")
        .isNotEmpty();

    for (ILoggingEvent event : logAppender.list) {
      assertThat(event.getFormattedMessage())
          .as("formatted message at level %s", event.getLevel())
          .doesNotContain(secret);

      Object[] args = event.getArgumentArray();
      if (args != null) {
        assertThat(Arrays.toString(args))
            .as("log argument array at level %s", event.getLevel())
            .doesNotContain(secret);
      }

      IThrowableProxy throwable = event.getThrowableProxy();
      while (throwable != null) {
        assertThat(throwable.getMessage() == null ? "" : throwable.getMessage())
            .as("throwable message")
            .doesNotContain(secret);
        throwable = throwable.getCause();
      }
    }
  }

  @Test
  @DisplayName("캡처 appender 동작 확인 — SECRET_KEY를 직접 로깅하면 검증이 실패한다 (negative control)")
  void negativeControl_directSecretLogIsDetected() {
    rootLogger.setLevel(Level.INFO);
    rootLogger.info("leaking {} for control check", SECRET_KEY);

    assertThatThrownBy(() -> assertLogsDoNotContain(SECRET_KEY))
        .isInstanceOf(AssertionError.class);
  }
}
