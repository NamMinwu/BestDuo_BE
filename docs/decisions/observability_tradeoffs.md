---
title: 관측성 스택 선정 — Trade-off 분석
status: accepted
last_updated: 2026-04-17
related:
  - ../operations_readiness_plan.md
  - ../operations_env_config.md
---

# 관측성 스택 선정 — Trade-off 분석

[운영 전환 준비 계획 §3 (Phase 2)](../operations_readiness_plan.md#3-phase-2--관측성--알림) 에서 도입할 관측성 스택을 선정한 근거를 기록한다.

## 요약 (결정)

| 영역 | 선정 | 비고 |
|---|---|---|
| 에러 추적 | **Sentry** (Developer free) | `sentry-spring-boot-starter-jakarta` |
| 메트릭 원격 저장 | **Grafana Cloud Prometheus `remote_write`** (Free) | 이미 `micrometer-registry-prometheus` 사용 중 |
| 알림 채널 | Discord Webhook | Grafana Alert → Webhook |
| 외부 헬스체크 | UptimeRobot | 5분 간격 `/actuator/health` |

관측성의 세 축(**로그 / 메트릭 / 에러**)을 한 벤더로 묶지 않고 **특화 도구를 병행**한다.

---

## 1. 배경 및 제약

| 항목 | 현재 상태 |
|---|---|
| 인프라 | Railway Hobby plan (단일 인스턴스, $5 크레딧/월) |
| 런타임 | Spring Boot 4 / Java 21 |
| 이미 연결된 의존성 | `spring-boot-starter-actuator`, `micrometer-registry-prometheus` |
| 네트워킹 | [Phase 1](../operations_readiness_plan.md#2-phase-1--보안--스키마-안전장치-최우선)에서 Public 노출 축소 예정 |
| 프론트엔드 | Next.js / Vercel (별도) |
| 예산 | 월 $10 이하 유지 |

**해결해야 할 핵심 문제**
- 장애를 **알아차리고(detection)** — 알림
- 원인을 **추적할(diagnosis)** 수 있어야 — 에러 스택 / 메트릭 / 로그
- APM(분산 트레이싱, DB 쿼리 프로파일)까지는 현 단계 불필요 (YAGNI)

---

## 2. 평가 기준

1. **Spring Boot / Micrometer 통합 품질** — 공식 starter, 자동계측 범위
2. **무료 tier의 실질 용량** — 현재 트래픽에서 overshoot 없이 수용 가능한가
3. **Railway 네트워킹 적합성** — 인바운드 노출 없이 동작(push 기반) 가능한가
4. **운영 부담** — self-host 시 "모니터링 인프라가 먼저 죽는" meta-monitoring 문제
5. **벤더 lock-in과 exit 비용** — 표준 프로토콜 / 데이터 이전 가능성

---

## 3. 에러 추적 — Sentry

### 3-1. 후보 비교

| 옵션 | Pros | Cons | 결론 |
|---|---|---|---|
| **Sentry (Developer free)** | 공식 Spring Boot starter, 5K events/월 + 30일 보관, issue grouping·release tracking·source map 1급, 프론트(Next.js)와 **동일 프로젝트로 trace 연결** | 초과 시 Team $26/mo, 메트릭/대시보드 약함 | ✅ 채택 |
| Datadog APM | APM + 로그 + 메트릭 한 벤더 | 호스트당 $31/mo, trace/log volume 기반으로 **비용 예측 불가**. APM은 현 단계 overkill | ❌ |
| New Relic | 무료 100GB/월로 관대 | 100GB 초과 시 GB당 $0.30, 에러 전용 UX(issue grouping, release health) 정제도가 Sentry보다 약함 | ❌ |
| Rollbar | Free 5K occurrences/월 | Spring 통합 레시피·커뮤니티 자료 부족, 프론트-백 통합 이슈 뷰 약함 | ❌ |
| Bugsnag | Free 7.5K events | Spring Boot starter가 커뮤니티판 → 버전 호환성 리스크 | ❌ |
| GlitchTip (Sentry API 호환 OSS, self-host) | 라이선스 비용 0 | Railway Hobby에 컨테이너 추가 → 메모리 압박, **meta-monitoring 공백** | ❌ |
| 로그만 (Loki/ELK + grep) | 추가 비용 0 | fingerprinting·dedup·release tracking·알림 라우팅을 **직접 구현**해야 함 (YAGNI 위반) | ❌ |

### 3-2. 결정 근거

- **통합 비용이 가장 낮음**: `application.yml`에 DSN 한 줄 + starter 의존성으로 `@ExceptionHandler` 누락 예외, ERROR 레벨 로그, HTTP breadcrumb, release 정보가 자동 캡처됨.
- **무료 tier가 실제 사용량을 상회함**: 배치 파이프라인 1시간 간격, 스테이지 실패율 1% 가정 시 월 수십~수백 이벤트 — 5K 한도에 여유가 큼.
- **Exit 경로 존재**: Sentry 중단이 필요하면 GlitchTip(Sentry API 호환)으로 **SDK 교체 없이 DSN만 변경**해 self-host 가능. 에러 이벤트는 장기 보존 가치가 낮아 이전 비용이 작음.
- **프론트·백 단일 프로젝트 trace** 연결이 가능하여 End-to-end 원인 추적에 유리.

### 3-3. 수용한 한계

- 5K events/월 초과 시 다음 플랜 $26 — 빈번히 넘으면 재검토 필요.
- Sentry 자체 장애 시 에러 가시성이 단절되므로, **알림 경로는 Grafana Alert → Discord와 이중화**하여 단일 장애점을 피한다.

---

## 4. 메트릭 원격 저장 — Grafana Cloud Prometheus `remote_write`

### 4-1. Pull vs Push — Railway 환경에서의 결정적 차이

| 방식 | Railway Hobby에서의 현실 |
|---|---|
| Pull (Prometheus scrape) | 외부 수집기가 `/actuator/prometheus`에 접근해야 함. Private Networking은 동일 프로젝트 내부만 가능 → 외부 scrape는 **Public Networking을 열어야 함**. [Phase 1의 Actuator 축소 방침](../operations_readiness_plan.md#2-phase-1--보안--스키마-안전장치-최우선)과 충돌 |
| **Push (`remote_write`)** | 앱이 아웃바운드로 지표를 밀어냄. 인바운드 노출 0 — Phase 1 보안 방향과 일관 |

Push 방식이 **단순 선호가 아니라 보안 아키텍처 결정과 묶여 있다**는 점이 핵심.

### 4-2. 후보 비교

| 옵션 | Pros | Cons | 결론 |
|---|---|---|---|
| **Grafana Cloud Prometheus `remote_write`** | 이미 `micrometer-registry-prometheus` 사용 중 — 설정만 추가, 10K active series + 14일 보관 무료, `remote_write`는 **Prometheus 표준 프로토콜(Protobuf + Snappy/HTTP)** 로 lock-in 최소 | Free 사용자 3명 cap, 10K series 초과 시 $8/1K series | ✅ 채택 |
| Self-host Prometheus + Grafana (Railway 컨테이너) | 비용 0, 완전 제어 | 컨테이너 2개 + 디스크 → Hobby 리소스 초과, **meta-monitoring 공백**, 운영 대상을 늘리는 결정은 Phase 2 방향과 역행 | ❌ |
| Datadog | APM/로그/메트릭 통합 | 호스트당 $15/mo, custom metric 100개/host 초과 시 $0.05/metric — **예산 즉시 초과**, 보관 1.5일 | ❌ |
| New Relic | 무료 100GB/월 관대 | OTLP 경유가 주류 → `micrometer-registry-otlp` 추가 필요, Prometheus 커뮤니티 대시보드 템플릿 재사용 불가 | ❌ |
| InfluxDB Cloud | 시계열 전용 성능 우수 | Flux/InfluxQL 학습 필요, Prometheus 생태계 단절 | ❌ |
| AWS CloudWatch | AWS 인프라와 통합 | Railway 네트워크/IAM 불일치, metric당 과금이 Micrometer 다수 지표에 불리 | ❌ |
| Railway 내장 Observability | 추가 인프라 0 | CPU/메모리/네트워크만 제공 — **애플리케이션 커스텀 메트릭**(stage별 처리량, 큐 길이 등) 계측 불가 | ❌ |

### 4-3. 결정 근거

- **코드 변경 최소**: 이미 의존성이 깔려 있고 `remote_write` URL/credential만 설정하면 됨.
- **Free tier 여유**: Spring Boot 기본 JVM/HTTP 지표 약 300~500 series + 커스텀 메트릭 수십 개 ≪ 10K.
- **Lock-in이 기술적으로 낮음**: `remote_write`는 공개 스펙 → 이전 경로가 구체적임.
  - self-host Prometheus / Grafana 로 이전 → `remote_write` URL만 변경
  - VictoriaMetrics Cloud / Mimir / Thanos 로도 동일한 protocol로 이전 가능
  - 즉, **Grafana Cloud가 아니라 Prometheus 생태계에 락인**되는 구조. Prometheus 생태계는 사실상 산업 표준.
- **Push 방식이 Phase 1 보안 방향과 일관** (§4-1 참조).

### 4-4. 수용한 한계

- 10K active series 초과 시 과금 → **cardinality 설계 규율 필수**.
  - `user_id`, `request_id` 같은 고유값을 label로 사용 금지.
  - tag는 enum 성격(stage, region, outcome)으로 제한.
- Free tier 3 user 상한 — 팀 확장 시 Pro $8/user/mo 재검토.

---

## 5. 왜 단일 벤더로 묶지 않았는가

Datadog / New Relic 처럼 로그·메트릭·에러·APM을 하나로 묶는 선택지가 있음에도 특화 도구 병행을 택한 이유:

1. **관측성 세 축의 특성이 다름**
   - 로그: 고볼륨, 단기 보존
   - 메트릭: 저볼륨, 장기 보존
   - 에러/트레이스: 중간 볼륨, 구조화
   - 한 벤더가 세 영역 모두 잘하는 경우는 유료 구간에서만 성립. **무료 tier는 특화 벤더가 우위**.
2. **장애 반경이 분리됨**
   - Sentry 장애 → 에러 가시성 상실, 메트릭/알림 유지
   - Grafana 장애 → 메트릭 가시성 상실, 에러/Discord 알림 유지
   - 단일 벤더 통합은 **단일 장애점(SPOF)** 이 됨.
3. **현재 규모에선 통합(correlation) 이득 < 비용 절감**
   - 규모가 커져 단일 이슈에서 "메트릭 이상 → 관련 로그 → 관련 트레이스"를 즉시 연결해야 하는 단계가 오면 Datadog/NR로 재검토.

---

## 6. 결과 (Consequences)

### Positive
- 월 비용 0 유지.
- 기존 의존성(Micrometer + Prometheus)에 얹는 형태로 코드 변경 최소.
- Prometheus 표준 덕분에 **벤더 이전 경로가 명시적**.
- Phase 1의 "Admin/Actuator 공격면 축소"와 일관된 push 기반 아키텍처.

### Negative
- 벤더 2곳(Sentry + Grafana Cloud) 계정/시크릿 관리 부담.
- Free tier 상한(5K events, 10K series, 3 user) 각각 모니터링 필요.
- Cardinality 설계 규율이 개발자 책임으로 남음.

### Neutral
- 알림 경로는 Sentry → Discord, Grafana Alert → Discord **이중화**하여 단일 장애점 회피.

---

## 7. 재검토 트리거

| 트리거 | 재검토 후보 |
|---|---|
| 월 error > 50K, Sentry Team plan 유료 구간 고착 | Sentry Team 유지 또는 GlitchTip self-host |
| active series > 10K, custom metric cardinality 증가 | VictoriaMetrics Cloud 또는 self-host |
| 팀원 > 3명 또는 보안 감사 요구 | Grafana Cloud Pro 또는 자체 stack |
| 멀티 인스턴스 전환 | pull 방식 + 서비스 디스커버리 재검토 |
| APM(분산 트레이싱, DB 쿼리 프로파일) 요구 | Datadog APM / Elastic APM |
| 규제(PII, 국내 저장) 요구 | self-host 강제 |

---

## 8. 참고

- [운영 전환 준비 계획 §3](../operations_readiness_plan.md#3-phase-2--관측성--알림)
- [운영 환경변수 설정 전략](../operations_env_config.md)
- Sentry Spring Boot: <https://docs.sentry.io/platforms/java/guides/spring-boot/>
- Grafana Cloud `remote_write`: <https://grafana.com/docs/grafana-cloud/monitor-infrastructure/integrations/integration-reference/integration-prometheus/>
- Prometheus `remote_write` spec: <https://prometheus.io/docs/concepts/remote_write_spec/>
