# ADR-008: Stage 2→3 `match_queue` 영속 큐 제거 — inline 파이프라인 채택, 병렬 fan-out은 prod 키 수령 시 활성화

> 작성일: 2026-06-05
> 상태: 채택(Accepted) — 구현은 단계적(phased), 현재 미적용
> 대상 모듈: `com.bestduo_BE.pipeline`, `com.bestduo_BE.ingest`, `com.bestduo_BE.common`
> 관련 ADR: [ADR-006](./adr_aggregate_from_match_payload.md)(payload 기반 재집계), [daily_pipeline_redesign_plan §0](./daily_pipeline_redesign_plan.md)(WorkItem 큐 거부)

---

## 1. 결정 요약

Stage 2(Collect) → Stage 3(Ingest) 사이의 DB 영속 큐 `match_queue` 를 제거하고, **summoner 단위 inline collect→ingest** 구조로 전환한다.

- **선택안: Option C (inline + executor fan-out)** 를 목표 아키텍처로 채택.
- **현재 범위(dev 키)**: 병렬 처리는 도입하지 않는다. inline **순차** 처리로 충분하다(단일 스레드가 dev 예산 천장에 이미 도달).
- **prod 키 수령 시**: ingest를 `executor + 공유 rate limiter` 로 fan-out 하는 활성화 계획을 §8에 따로 기록한다. 지금 구현하지 않는다(YAGNI).
- dedup·재시작 영속은 이미 존재하는 `match` 테이블 + `summoner.match_ids_collected_at` 로 흡수한다.
- 제거 전 **3가지 보장 조건**(§6)을 반드시 충족하고 단계적으로(§7) 진행한다.

---

## 2. 배경 — `match_queue` 의 현재 역할

`match_queue`(스키마: `V2__drop_and_recreate_summoner_and_match_queue.sql:35-47`)는 다음을 담당한다:

| 역할 | 구현 |
|---|---|
| Dedup | `match_id` PK + enqueue 시 `existsById`(`MatchQueueEnqueuer:19`) |
| Fan-out 버퍼 | collect 1콜 → matchId 10~30개를 적재, ingest가 1개씩 소비 |
| 재시작 영속 | status/locked_at 영속, 재시작 시 백로그 보존 |
| Tier 우선순위 | `collection_tier` 필터 + round-robin(`RegionalPipelineRunner:135`) |
| 재시도/poison | `retry_count`/`last_error`/cooldown + ERROR 종결 |
| 관측 | `IngestQueueStats`(status/error/throughput) → `MatchQueueGaugeRegistrar` Micrometer 게이지 |

즉 `match_queue` 는 **작업 분배(coordination)** 와 **관측(observability)** 의 이중 역할을 한다.

---

## 3. `match_queue` 가 없어도 되는 이유

### 3.1 5가지 역할 중 4개는 기존 테이블이 공짜로 흡수

| 큐가 하던 일 | 큐 없이 대체 |
|---|---|
| Dedup | `match` 테이블 `existsById` (대상만 큐→match) |
| 재시작 영속 | `summoner.match_ids_collected_at` + `match` 테이블 |
| Fan-out 버퍼 | inline 루프 (단일 스레드라 rate 디커플링 불필요) |
| 예산 배분(collect↔ingest) | summoner당 1:N 구조적 고정 |
| **Poison 컷오프** | ⚠️ **이것만** 명시적 대체 필요 (미니 실패 테이블/카운터) |

→ 큐의 *고유* 잔여 가치는 **poison 컷오프 + 동적 매치-우선순위 + 큐-깊이 관측** 셋뿐이며, 앞 둘은 런타임에 거의 쓰이지 않고 관측은 emit 메트릭으로 이전 가능(§3.4).

### 3.2 큐가 *유일하게* 정당한 자리(멀티 프로세스)가 사라짐

DB 영속 큐가 꼭 필요한 단 하나의 시나리오 = **프로세스를 넘는 작업 분배**. prod 키 수치가 이를 무력화한다:

- `500 req/10s` = 50/s, `30,000 req/10min` = 50/s → **지속 천장 50 req/s**
- 한 프로세스가 **동시 10~25 스레드**(Little's Law: 50 × 0.2~0.5s)로 천장을 포화
- rate limit은 **키당 공유** → 프로세스를 늘려도 throughput 불변, 분산 rate limiting + 공유 큐 복잡도만 추가 → **순손해**

→ 멀티 프로세스가 후보에서 빠지므로 DB 큐의 유일한 명분도 소멸. (멀티 머신이 의미 있으려면 *키를 추가로 받아 예산이 실제 N배가 될 때* — 별개 문제, §9.)

### 3.3 현재 큐는 멀티워커 대비도 반쪽

`pickReadyWithPriorityAndLock` 의 `candidates` CTE에 **`FOR UPDATE SKIP LOCKED` 가 없다**(`MatchQueueJpaRepository:118-145`). 정확성은 `AND status='READY'` 재검사로 보장되나, N워커가 같은 우선순위 행을 동시에 노려 lock 경합(thundering herd)으로 확장되지 않는다. → 멀티워커로 가는 순간 이 SQL을 다시 써야 하므로, 지금 큐 유지가 미래 작업을 적립해주지 않는다.

### 3.4 보장해야 할 2가지가 큐 없이 충족됨

| 보장 | 큐 없이 | 전제 조건 |
|---|---|---|
| 재시작 안전 | 손실 = in-flight summoner 1명 re-collect(1콜), 데이터 손실 0, 총 부하와 무관 | ①②(§6) |
| 모니터링 | event-emitted Micrometer 메트릭(이미 스택 보유) | ③(§6) |

메트릭 매핑: `ingest_success_total` / `ingest_failure_total{reason}` / `ingest_latency`(p99=느린 호출) / `ingest_inflight` / `collect_pending_summoners` / heartbeat. → 큐 스캔보다 표준적이고, "메트릭을 위해 DONE 행 보존"이라는 디스크 비용도 사라진다.

> 참고: 실제 겪은 디스크풀 인시던트([incident_postgres_disk_full_recovery_mode.md](./incident_postgres_disk_full_recovery_mode.md))는 Postgres가 죽은 DB-레벨 장애라, 같은 DB의 `match_queue` 도 보호를 주지 못했다.

### 3.5 유지 시의 실제 비용 (불필요 + 비용 누적)

- **DONE 행 무한 증가**: `markDone` 만 있고 `match_queue` 삭제 경로가 없음. 5GB 디스크 제약 + 디스크풀 이력 box에서 단조 증가.
- **dead `priority` 컬럼**: `V2:38` `NOT NULL` 이나 `MatchQueue.newReady:58-71` 가 set하지 않아 항상 0, 어떤 쿼리도 읽지 않음.

→ "불필요하지만 무해"가 아니라 **"불필요하고 비용을 누적 중"**. 단, 제거는 §6 조건을 구현하며 §7 단계로만.

---

## 4. 검토한 대안 — 파이프라인 구조 trade-off

비교 축: prod 예산 포화 / 재시작 / 모니터링 / 디스크·운영비 / 복잡도 / 멀티머신.

| | A. DB 영속 큐(현재) | B. Inline 순차 | C. Inline + executor fan-out ★ | D. In-memory 큐 + 워커풀 | E. 멀티프로세스 + 브로커 |
|---|---|---|---|---|---|
| prod 50req/s 포화 | △ (멀티워커+SKIP LOCKED 필요) | ❌ (1스레드, 예산 놂) | ✅ (동시 10~25) | ✅ | ✅ (키당 공유라 무의미) |
| 동시성 메커니즘 | DB claim | 없음 | executor+limiter | BlockingQueue+풀 | 브로커+분산limiter |
| 재시작 안전 | ✅ 백로그 박제 | ✅ summoner 단위(1콜 손실) | ✅ 동일 | ⚠️ in-memory 증발 | ✅ 브로커 영속 |
| dedup | 큐 PK | match 테이블 | match 테이블 | match 테이블 | 브로커/큐 |
| poison | retry_count/ERROR | 미니가드 | 미니가드 | 미니가드 | 브로커 DLQ |
| 모니터링 | 큐 스캔(현재 wired) | emit 메트릭 | emit 메트릭 | emit 메트릭 | 브로커+emit |
| 디스크/운영비 | ❌ DONE 무한증가 | ✅ 최소 | ✅ 최소 | ✅ 최소 | ❌ 브로커 운영 |
| 복잡도 | 중 | **최저** | 낮음 | 중 | **최고** |
| 멀티머신 확장 | △ | ❌ | ❌ | ❌ | ✅ |

- **A (현재)**: 단일 키·단일 스레드에선 이점 0 + DONE 무한증가 + SKIP LOCKED 미비. 진짜 멀티프로세스 분배 시에만 적합 — 이 시스템엔 해당 없음.
- **B (Inline 순차)**: 가장 단순. dev 키엔 충분하나 prod 예산을 못 채움. → **현재(dev) 단계의 구현 형태.**
- **C (Inline + executor fan-out)** ★: collector가 matchId 생산 → `Semaphore + virtual-thread executor` 가 ingest 병렬, 각 호출이 기존 `DualWindowRateLimiter.acquire()` 통과. prod 예산 포화 + 큐 없이 dedup/재시작/관측. → **채택안. C = B(now) + executor 활성화(prod 키 시).**
- **D (In-memory 큐)**: 재시작 시 백로그 증발이라 C 대비 이점 거의 없음. DB 큐의 메모리판이라 어중간.
- **E (멀티프로세스 + 브로커)**: 키당 예산 공유라 throughput 불변 + 분산 조율/운영비 + 최고 복잡도. 기획의 단일 스레드 전제와 충돌. 키 추가로 예산이 실제 N배 될 때만(§9).
- **참고 F. Spring Batch / WorkItem 큐**: [daily_pipeline_redesign_plan §0](./daily_pipeline_redesign_plan.md)에서 이미 거부(끝없는 Stage3와 철학 불일치, 단일 워커 이중 큐). 재론 없음.

---

## 5. 채택 — 왜 C인가, 그리고 단계 분리

**C를 목표로 하되, 구현을 두 단계로 분리한다:**

- **C-now (현재, dev 키)** = Option B 형태. inline **순차** collect→ingest. `match_queue` 제거의 본체. 병렬 처리는 **도입하지 않는다**(단일 스레드가 dev 예산을 이미 채움 — 병렬화는 효과 0 + speculative).
- **C-later (prod 키 수령 시)** = executor fan-out 활성화(§8). 지금은 설계만 기록.

이렇게 분리하는 이유: 큐 제거의 가치(단순화·디스크·dead code 청소)는 *지금* 실현되고, 동시성은 *실제로 필요해지는 시점(prod 키)*에 최소 변경으로 켜는 것이 "pressure가 real해질 때 refactor" 원칙에 맞다.

---

## 6. 제거 전 반드시 충족할 보장 조건

| # | 조건 | 이유 |
|---|---|---|
| ① | **patch context 항상 보장** (`ctx==null` fallback 차단) | 재시작 후 re-collect가 같은 매치 집합을 줘야 함. `findMatchIdsSince/Between(startTime)`(시간 경계)은 결정적이나 `findRecentMatchIds(count)`(`CollectMatchIdsRunner:108-116`)는 윈도우가 밀려 매치 누락 가능 |
| ② | **`markMatchIdsCollected` 를 ingest 완료 *후*로 이동** | 현재는 enqueue 직후(`CollectMatchIdsRunner:86`). inline에선 summoner의 모든 매치 ingest 후 표시해야 중간 크래시 시 통째 재처리됨 |
| ③ | **관측 메트릭을 먼저 깔고 큐와 병행 검증 후 제거** | 큐 관측 표면을 잃고 눈을 가린 채 인프라를 뽑는 것 방지 |

> ①②를 지키면 재시작 손실은 in-flight summoner 1명당 1 collect 콜로 bounded(데이터 손실 0). ③을 지키면 관측 공백 없음.

---

## 6.5 실패/재시도 처리 — 옵션 A (전용 재시도 머신 없음)

큐의 `retry_count`(max 2)/`cooldown`(10분)/terminal-ERROR 를 **재현하지 않는다.** 운영 실측상 재시도가 거의 발생하지 않았고, inline에는 두 가지 자연 redundancy가 있기 때문:

1. **재수집 = 자연 재시도**: 같은 patch 내 summoner 재시드 시 실패 matchId가 다시 떠올라 `existsById` dedup 하에 재시도됨.
2. **매치당 ~10 수집 경로**: 참가자 10명 전원이 시드 대상 → 한 경로 일시 실패는 다른 참가자 경로로 덮임(dedup 1회 저장).

**정책:**
- ingest **1회 시도** → non-429 예외면 `ingest_failure_total{reason}` 기록 + log + **skip**. retry_count/markError/poison 테이블 없음.
- **429** → 기존 `RegionalPipelineRunner` backoff 유지(무변경).
- **크래시** → 조건②(재수집)가 stale 복구 대체.
- **⚠️ 시스템 실패(auth/대량 5xx)는 조용히 흡수 금지** → 실패율 급증 알람 + auth halt. (큐도 못 하던 부분 — 오히려 개선.)
- **reversible**: poison이 실제 예산을 갉으면 `ingest_failure_total` 가 즉시 드러냄 → 그때 작은 skip-set 추가(YAGNI). 지금은 미반영.

**롤백 flag 없음**: Phase A 메트릭 병행검증으로 안전성을 확보하므로 inline↔큐 토글 flag는 두지 않는다(단순성).

---

## 7. 단계적 마이그레이션 (4단계)

1. **관측 이전**: `ingest_*`/`collect_pending_summoners`/heartbeat 메트릭을 emit 방식으로 추가. (큐와 **병행** 운영, 지표 parity 검증)
2. **inline 경로 도입 + ①② 가드**: `collectForSummoner` 가 enqueue 대신 `match.existsById` 체크 후 inline ingest. 실패 처리 = §6.5 옵션 A(1회 시도 + metric + skip, flag 없음). (큐 경로와 병행 비교)
3. **큐 경로 차단**: enqueue/dequeue 호출 제거, `RegionalPipelineRunner` 의 Stage2/Stage3 분기를 단일 단계로 통합.
4. **스키마 drop**: `match_queue` 테이블 + `MatchQueue`/`QueueStatus`/`MatchQueueJpaRepository`/`MatchQueueDispatcher`/`MatchQueueEnqueuer`/`IngestQueueStats`/`MatchQueueGaugeRegistrar`/관련 admin 제거.

---

## 8. prod 키 수령 시 활성화 계획 (C-later, 지금 구현 안 함)

prod 키(`500/10s`, `30,000/10min` = **50 req/s 지속**)를 받으면 단일 스레드로는 천장을 못 채우므로 ingest를 fan-out 한다.

### 설계
```
단일 프로세스
  ├─ collector (단일 스레드): pending summoner(tier 순) → matchIds fetch → bounded 채널 push
  └─ ingest pool: Semaphore(maxInFlight) + virtual-thread executor
                  → 각 task가 기존 DualWindowRateLimiter.acquire() 통과 → fetch + save
```

- **limiter 무변경**: `DualWindowRateLimiter.acquire()` 는 이미 thread-safe(`:33` synchronized 검사 + `:52` lock 밖 sleep). 숫자만 prod 값(short 500/10s, long 30000/10min)으로.
- **maxInFlight ≈ 50 req/s × latency** (Little's Law) → 시작 ~16~32, p99 보며 튜닝. Semaphore로 in-flight를 묶어 스레드 폭발 방지.
- **collector는 단일 스레드 유지**: collect와 ingest가 **같은 regional 예산을 공유**하므로 collector 병렬화는 ingest 예산을 뺏는 제로섬 + 백로그 재생성. collect는 ingest의 ~1/N(1~4.5 req/s) 볼륨이라 한 스레드로 충분. bounded 채널 + 공유 limiter가 collect↔ingest를 자동 균형.
- **collector 동시성은 튜너블(기본 1)**: `ingest_inflight`/채널-empty 가 ingest starvation을 *측정으로* 보일 때만 1→2로 상향. 병렬 collector 풀을 미리 짓지 않는다(YAGNI).

### 이때도 큐는 불필요
희소자원 코디네이터는 limiter(전역 예산), 병렬성은 executor, dedup은 match 테이블 — 전부 프로세스 내 완결. DB 큐가 끼어들 자리 없음.

---

## 9. 언제 큐/멀티프로세스를 재도입하나

다음 *측정된* 조건이 실제로 발생할 때만 재검토(그 전엔 speculative):

- **API 키를 추가로 받아 예산이 실제 N배가 됨** → 키별 워커를 여러 프로세스/머신에 분산할 실익 발생 → 공유 큐/브로커(Redis/SQS) + 분산 rate limiting + `FOR UPDATE SKIP LOCKED` 기반 claim 설계.
- **로컬 연산이 병목** (단일 인스턴스가 50 matches/s 처리/저장을 못 따라감) → 현재는 I/O 대기가 지배라 해당 없음.

---

## 10. 후속 영향

- **디스크**: `match_queue`(특히 DONE 무한행) 제거로 단조 증가 한 축 제거. [bestduo_db_size_bytes](./portfolio_slides.md) 추이에 반영.
- **관측 성숙도**: 상태-테이블 스캔 → event-emitted 메트릭으로 전환(표준 패턴).
- **포기하는 것(명시)**: 동적 매치-단위 우선순위, per-item 큐-깊이/RUNNING-stuck 추적(→ latency p99 + 타임아웃 + heartbeat로 대체).
- **코드**: pipeline/ingest 경계 단순화(Stage2/3 단일 단계화), dead `priority` 컬럼 동반 소멸.

## 11. 참고

- [ADR-006](./adr_aggregate_from_match_payload.md) — payload 기반 재집계(큐 없이 재처리 가능 전제와 정합)
- [daily_pipeline_redesign_plan.md](./daily_pipeline_redesign_plan.md) §0 — WorkItem/Spring Batch 거부 근거
- [incident_postgres_disk_full_recovery_mode.md](./incident_postgres_disk_full_recovery_mode.md) — 디스크 제약이 1급 운영 지표인 이유
- [pipeline_implementation_map.md](./pipeline_implementation_map.md) — 현재 구현 맵(큐 동작 file:line)
