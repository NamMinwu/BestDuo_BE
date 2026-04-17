---
title: 외부 관측 서비스 연동 운영 가이드
status: active
last_updated: 2026-04-17
---

# 외부 관측 서비스 연동 운영 가이드

[operations_readiness_plan.md](./operations_readiness_plan.md) Phase 2 / PR-7 의 운영 작업을 다룬다. 코드 변경 없이 외부 서비스를 연결하여 **메트릭 시각화 / 에러 추적 / 알림 / 외부 헬스체크**를 가동한다.

대상 서비스:

| 용도 | 서비스 | 비용 |
|---|---|---|
| 메트릭 원격 저장 + 대시보드 | Grafana Cloud (Free) | 10K series, 14일 보존 |
| 에러 추적 | Sentry (Developer 플랜) | 5K events/month |
| 알림 채널 | Discord Webhook | 무료 |
| 외부 헬스체크 | UptimeRobot (Free) | 5분 간격, 50개 모니터 |

코드 측 준비 상태 (PR-5 / PR-6 머지 완료 가정):
- `/actuator/prometheus` 노출 (Spring Boot Actuator + Micrometer Prometheus)
- 구조화 JSON 로그 (`logback-spring.xml`, `prod` 프로파일에서 `LogstashEncoder`)
- 커스텀 메트릭: `pipeline.stage.completed`, `pipeline.match_queue.size`, `riot.api.request`
- Sentry SDK 설치 (`SENTRY_DSN` 비어 있으면 자동 no-op)

---

## 1. Sentry (PR-6 후속 운영 작업)

### 1.1 프로젝트 생성

1. https://sentry.io/signup/ 가입 → 무료 Developer 플랜 선택.
2. **Create Project** → Platform: **Java / Spring Boot**.
3. 프로젝트명 예시: `bestduo-be-prod`.
4. 생성 후 표시되는 **DSN** 값을 복사 (`https://<key>@oXXXX.ingest.sentry.io/YYYY` 형태).

### 1.2 Railway 환경변수 등록

Railway Dashboard → Service → **Variables**에서 추가:

| Env Var | 값 | 비고 |
|---|---|---|
| `SENTRY_DSN` | (복사한 DSN) | 비어 있으면 SDK가 no-op |
| `SENTRY_ENVIRONMENT` | `prod` | 로컬은 미설정 → `local` 기본값 |
| `SENTRY_RELEASE` | `0.0.1-SNAPSHOT` | 배포마다 변경. 가능하면 git SHA 자동 주입 |
| `SENTRY_TRACES_SAMPLE_RATE` | `0.05` | 트레이싱 샘플링 비율 (5%) |

저장 후 자동 재배포 → `/actuator/health` 확인.

### 1.3 검증

1. Railway 콘솔 → Logs에서 `Sentry` 초기화 로그 확인 (`Initializing SDK ... environment=prod`).
2. 의도적 에러 발생 (예: 임시로 `/admin/queue/error-test` 같은 트리거를 만들거나, 운영 중 자연 발생을 기다림).
3. Sentry 프로젝트 → Issues에서 새 이슈 표시 확인.

### 1.4 알림 통합

Sentry → **Settings → Integrations → Discord** → Connect Workspace → 알림을 받을 채널 선택.
또는 Sentry **Alerts → Notifications** 에서 Discord webhook 직접 등록 (다음 §3 참고).

기본 룰 권장:
- **이슈 발생 빈도 ≥ 10 events/min** → 즉시 Discord
- **새로운 이슈 등장** → 즉시 Discord
- **Regression (해결된 이슈 재발)** → 즉시 Discord

### 1.5 노이즈 관리

`application.yml`에 이미 등록:

```yaml
sentry:
  ignored-exceptions-for-type:
    - com.bestduo_BE.common.infra.riot.exception.RiotRateLimitedException
```

운영하며 노이즈 예외가 추가되면 같은 리스트에 패키지 경로로 추가한다 (예: 일시적 외부 5xx 래퍼 등).

---

## 2. Grafana Cloud (Prometheus remote_write + 대시보드)

### 2.1 계정 / 스택 생성

1. https://grafana.com/auth/sign-up/create-user → 무료 가입.
2. 자동으로 stack 1개 생성됨 (`<account>.grafana.net`).
3. **Connections → Add new connection → Hosted Prometheus metrics → Prometheus**.
4. **Forward metrics from a Prometheus server**가 아닌 **By an agent / scrape 외부에서 push**가 필요 없으므로 → **Prometheus remote_write 엔드포인트** 정보를 저장한다:
   - URL: `https://prometheus-prod-XX-prod-eu-west-X.grafana.net/api/prom/push`
   - Username: 숫자 ID
   - Password: API Token (Write 권한)

### 2.2 수집 방식 선택

Spring Boot 앱은 `/actuator/prometheus`를 **노출**(pull)할 뿐 push 하지 않는다. Grafana Cloud로 데이터를 보내는 방법은 두 가지:

**(A) Grafana Agent (또는 Alloy) — 권장**

별도 Railway 서비스로 Grafana Agent 컨테이너를 1개 띄워서 내부 네트워크로 백엔드를 scrape → Grafana Cloud로 remote_write.

장점: Spring Boot 앱은 변경 없음. Agent의 scrape interval만 운영.
단점: Railway에 컨테이너 1개 추가 (Hobby plan 한 서비스 슬롯 소비).

설정 예 (`agent-config.yaml`):

```yaml
metrics:
  global:
    scrape_interval: 30s
  configs:
    - name: bestduo
      remote_write:
        - url: https://prometheus-prod-XX-prod-eu-west-X.grafana.net/api/prom/push
          basic_auth:
            username: ${GRAFANA_CLOUD_PROM_USER}
            password: ${GRAFANA_CLOUD_PROM_TOKEN}
      scrape_configs:
        - job_name: bestduo-backend
          metrics_path: /actuator/prometheus
          static_configs:
            - targets: ['bestduo-be.railway.internal:8080']
```

Railway에 Agent 서비스 추가 → 위 yaml을 마운트 → 환경변수 주입.

**(B) Grafana Cloud Synthetic / Prometheus 외부 scrape**

Grafana Cloud가 인터넷 노출된 `/actuator/prometheus`를 직접 scrape. 이 경우 엔드포인트가 공개되므로 **Basic Auth 또는 IP allowlist**가 필요 → Spring Security 설정 변경이 코드 PR로 필요. 지금 단계에서는 (A)를 채택.

### 2.3 데이터 도착 확인

Grafana Cloud → **Explore → Prometheus data source** → `up{job="bestduo-backend"}` 쿼리 → `1` 반환 확인.

샘플 쿼리:
- `pipeline_stage_completed_total` — Stage별 누적 처리량
- `pipeline_match_queue_size` — 큐 길이
- `riot_api_request_seconds_count` — Riot API 호출 수

(Micrometer는 `.` 을 `_` 로 변환하여 Prometheus 노출한다.)

### 2.4 대시보드 패널 (1개 대시보드 / 4개 row)

**Row 1 — 파이프라인 처리량**

| 패널 | 쿼리 | 시각화 |
|---|---|---|
| Stage별 success rate (5분) | `sum by (stage) (rate(pipeline_stage_completed_total{outcome="success"}[5m]))` | Time series |
| Stage별 error rate (5분) | `sum by (stage) (rate(pipeline_stage_completed_total{outcome="error"}[5m]))` | Time series |
| 에러율 % | `sum by (stage) (rate(pipeline_stage_completed_total{outcome="error"}[5m])) / sum by (stage) (rate(pipeline_stage_completed_total[5m]))` | Stat (red>5%) |

**Row 2 — 큐 / Riot API**

| 패널 | 쿼리 | 시각화 |
|---|---|---|
| Match queue size | `pipeline_match_queue_size` | Time series |
| Riot API 응답시간 p95 | `histogram_quantile(0.95, sum by (le, endpoint) (rate(riot_api_request_seconds_bucket[5m])))` | Time series |
| Riot API 실패율 | `sum by (endpoint) (rate(riot_api_request_seconds_count{outcome="error"}[5m])) / sum by (endpoint) (rate(riot_api_request_seconds_count[5m]))` | Stat |

**Row 3 — JVM / HTTP**

| 패널 | 쿼리 | 시각화 |
|---|---|---|
| Heap 사용량 | `jvm_memory_used_bytes{area="heap"}` | Time series |
| GC pause | `rate(jvm_gc_pause_seconds_sum[5m])` | Time series |
| HTTP 요청 RPS | `sum by (uri) (rate(http_server_requests_seconds_count[5m]))` | Time series |
| HTTP 5xx | `sum by (uri) (rate(http_server_requests_seconds_count{status=~"5.."}[5m]))` | Stat (red>0) |

**Row 4 — 시스템**

| 패널 | 쿼리 | 시각화 |
|---|---|---|
| Process CPU | `process_cpu_usage` | Gauge |
| File descriptor | `process_files_open_files` | Time series |
| DB 커넥션 active | `hikaricp_connections_active` | Time series |

대시보드는 **Grafana → Dashboards → New → Import**로 위 패널을 직접 구성하거나, 추후 JSON으로 export하여 `docs/grafana-dashboard.json`에 커밋한다 (재현성).

### 2.5 알림 룰 (Grafana Alerting)

| 룰 | 조건 | 채널 | 심각도 |
|---|---|---|---|
| Pipeline error rate high | `error_rate > 0.2` 5분 지속 | Discord | warning |
| Match queue 폭증 | `pipeline_match_queue_size > 5000` 10분 지속 | Discord | warning |
| Riot API 실패율 | `riot 5xx rate > 0.1` 5분 지속 | Discord | warning |
| Heap 위험 | `heap used / max > 0.9` 5분 지속 | Discord | critical |
| Service down | `up{job="bestduo-backend"} == 0` 1분 지속 | Discord | critical |

Grafana → **Alerting → Contact points → New** → Discord webhook 등록 (다음 §3에서 만든 webhook URL 입력).

---

## 3. Discord Webhook

### 3.1 채널 / 웹훅 생성

1. Discord 서버에서 알림 전용 채널 생성 (예: `#bestduo-alerts`).
2. 채널 톱니바퀴 → **Integrations → Webhooks → New Webhook**.
3. 이름/아이콘 설정 후 **Copy Webhook URL**.

### 3.2 사용처

| 사용처 | 등록 위치 |
|---|---|
| Grafana Alerting | Contact points → Discord → URL 붙여넣기 |
| Sentry Alerts | Settings → Integrations → Discord (또는 Alert Rule → Webhook URL 직접 입력) |
| UptimeRobot 알림 | My Settings → Alert Contacts → Add Webhook |

### 3.3 보안

- Webhook URL은 사실상 **비밀**(누구나 채널에 글을 쓸 수 있음). git에 커밋 금지. Railway / Grafana / Sentry / UptimeRobot 각 서비스의 secret 저장소에만 저장.
- 노출 의심 시 Discord 채널에서 Webhook 삭제 후 재발급.

### 3.4 알림 형식

기본 페이로드(`content`/`embeds`)는 각 발신 서비스가 자동 구성한다. Grafana는 alert rule에서 **message template**으로 한국어/요약 형식을 커스터마이즈할 수 있다.

권장 템플릿(Grafana):

```
[{{ .Status | toUpper }}] {{ .CommonLabels.alertname }}
- 환경: {{ .CommonLabels.env }}
- 시작: {{ .StartsAt.Format "2006-01-02 15:04:05" }}
- 값: {{ range .Alerts }}{{ .ValueString }}{{ end }}
- 대시보드: {{ .CommonAnnotations.dashboardURL }}
```

---

## 4. UptimeRobot (외부 헬스체크)

### 4.1 모니터 등록

1. https://uptimerobot.com/signUp → 무료 가입.
2. **Add New Monitor** → Type: **HTTP(s)**.
3. URL: `https://<railway-public-domain>/actuator/health`
4. Monitoring Interval: **5분** (free 플랜 최소).
5. Monitor Timeout: **30초**.
6. Alert Contacts: 다음 §4.2.

### 4.2 알림 채널 연결

**My Settings → Alert Contacts → Add Alert Contact**:

| Type | 값 |
|---|---|
| Webhook | Discord webhook URL (§3에서 생성한 것) + POST + Content-Type `application/json` |
| (선택) Email | 운영자 메일 (백업용) |

Discord webhook은 UptimeRobot 기본 페이로드와 직접 호환되지 않으므로 **Webhook URL 끝에 `/slack` 추가** 가 가장 간편하다 (Discord가 Slack-호환 페이로드를 받아준다).

예: `https://discord.com/api/webhooks/XXX/YYY/slack`

### 4.3 검증

- 등록 직후 모니터 status가 5분 내 **Up** 으로 표시되는지 확인.
- 임시로 Railway 서비스를 stop → 5~10분 내 Discord 알림 도착 확인 → 다시 start.

### 4.4 한계

- Free 플랜은 **5분 간격**이 최소 → 최대 ~10분의 감지 지연을 수용한다.
- `/actuator/health`는 `show-details: never`로 단순 UP/DOWN 만 반환하므로 컴포넌트별 상세 진단은 Grafana로 본다.

---

## 5. 환경변수 / Secret 관리 정리

운영자가 보유해야 할 secret 일람. 모두 **Railway env var** 또는 외부 SaaS 콘솔에 저장하며 git에는 절대 커밋하지 않는다.

| Secret | 저장 위치 | 갱신 주기 | 비고 |
|---|---|---|---|
| `EXTERNAL_RIOT_API_KEY` | Railway | 월 1회 | Riot Developer Portal에서 발급 |
| `ADMIN_API_KEY` | Railway | 분기 1회 | 32자 이상 무작위 |
| `SPRING_DATASOURCE_PASSWORD` | Railway (DB plugin이 주입) | DB 회전 시 | Railway가 자동 관리 |
| `SENTRY_DSN` | Railway | 프로젝트 재생성 시 | Sentry → Project Settings → Client Keys |
| `SENTRY_RELEASE` | Railway (배포 스크립트) | 배포마다 | git SHA 권장 |
| `SENTRY_TRACES_SAMPLE_RATE` | Railway | 비용 조정 시 | 기본 0.05 |
| `GRAFANA_CLOUD_PROM_USER` | Grafana Agent 서비스 env | 토큰 회전 시 | 숫자 ID |
| `GRAFANA_CLOUD_PROM_TOKEN` | Grafana Agent 서비스 env | 분기 1회 | Write-only 권한 토큰 |
| Discord Webhook URL | Grafana / Sentry / UptimeRobot | 노출 의심 시 | Discord 채널 → Webhook 재발급 |
| UptimeRobot API Key | UptimeRobot 콘솔 | 필요 시 | 모니터 자동화 시에만 |

### 5.1 회전 절차 (예: Riot API Key)

1. Riot Developer Portal에서 신규 키 발급.
2. Railway env `EXTERNAL_RIOT_API_KEY` 갱신 → 자동 재배포.
3. `/actuator/health` 정상 확인 + Grafana에서 `riot_api_request_seconds_count` 증가 확인.
4. 구 키는 발급처에서 폐기.

### 5.2 노출 사고 대응

1. 즉시 해당 secret을 발급처에서 폐기/재발급.
2. Railway env에 신규 값 주입 → 재배포.
3. git history에 누출되었다면 BFG/`git filter-repo`로 제거 + force-push 결정 (별도 검토 필요).
4. `docs/operations_tuning_log.md` 또는 별도 incident 로그에 사건 기록.

---

## 6. 완료 기준 (Phase 2 — PR-7)

다음을 모두 충족하면 Phase 2 종료:

- [ ] Sentry: prod 환경에서 의도적 에러 발생 → Sentry Issue 자동 생성 → Discord 알림 도착.
- [ ] Grafana Cloud: 대시보드 1개에서 Stage별 처리량 / 큐 길이 / Riot API 실패율 / JVM heap / HTTP 5xx 실시간 확인 가능.
- [ ] Grafana Alerting: 위 5개 룰 활성화 + Discord contact point 동작 확인.
- [ ] UptimeRobot: `/actuator/health` 5분 간격 모니터 Up 상태 + 강제 다운 시 5~10분 내 Discord 알림 수신.
- [ ] 본 문서의 모든 secret이 Railway 또는 각 SaaS 콘솔에 저장되어 있고, 저장소에는 어떤 형태로도 커밋되지 않음.

---

## 7. 참고

- [operations_readiness_plan.md](./operations_readiness_plan.md) — 전체 로드맵 (Phase 1~3)
- [operations_env_config.md](./operations_env_config.md) — 환경변수 운영 전략
- [decisions/observability_tradeoffs.md](./decisions/observability_tradeoffs.md) — 도구 선정 trade-off
- `src/main/resources/application.yml` — Sentry / Actuator 기본 설정
- `src/main/resources/application-prod.yml` — prod 오버라이드
- `src/main/resources/logback-spring.xml` — 구조화 로그 설정
