# match_queue 제거 → inline 파이프라인 구현 계획

> 작성일: 2026-06-05
> 상태: 계획(Planned) — 착수 대기
> 근거 결정: [ADR-008](./adr_remove_match_queue_inline_pipeline.md) / 요구사항: [pipeline_requirements.md](./pipeline_requirements.md)
> 범위: **C-now (inline 순차)**. 병렬 executor·롤백 flag·poison 테이블 **없음**(확정).

---

## 0. 스코프 & 확정 사항

- **대상**: Stage 2(Collect) → Stage 3(Ingest) 경계만. `match_queue` 제거 + inline 융합.
- **안 건드림**: Aggregate cron, Archive, serving API(QR 표시는 별도 트랙).
- **확정**: 실패 처리 = ADR-008 §6.5 옵션 A(1회 시도 + metric + skip). 롤백 flag 없음. 병렬 fan-out은 prod 키 때(ADR-008 §8).

## 1. 전체 성공 기준 (verify 대상)

제거 완료 후 다음이 **모두** 성립:
1. 재시작/재배포 후 데이터 손실 0, 자동 재개 (NFR-5)
2. dedup 유지 — 같은 매치 1회만 ingest (FR-4)
3. patch 순도 유지 — cross-patch 오염 0 (FR-1)
4. 관측 공백 0 — 큐 제거 전 emit 메트릭이 큐 지표와 parity (조건③)
5. 빌드/테스트 green, `match_queue` 참조 0

---

## 2. Phase A — 관측 메트릭 emit (큐 살아있는 채로) — ✅ 코드 구현 완료

> 조건③: 큐를 떼기 전에 새 관측 표면을 먼저 깔고 parity 검증.

**설계 판단**: `PipelineMetrics`는 `pipeline` 컨텍스트이고 `pipeline → ingest`(`RegionalPipelineRunner`→`MatchIngestRunner`) 의존이 이미 있어, `ingest`에서 PipelineMetrics를 쓰면 **순환 의존**이 된다. → Phase A 메트릭은 **전부 pipeline 컨텍스트에서만** 배선(ingest 무수정).

**변경 (구현됨)**
- `PipelineMetrics`: `pipeline.ingest.success`/`pipeline.ingest.failure`(Counter), `pipeline.collect.pending_summoners`/`pipeline.heartbeat`(Gauge) 추가.
- `RegionalPipelineRunner`: `loop`에서 `recordHeartbeat()`, Stage3 성공 시 `recordIngestOutcome(result.done(), result.error())` — 큐가 받던 done/error 카운트를 그대로 emit(parity).
- `SummonerJpaRepository.countMatchIdsPendingSummoners()` + `PendingSummonersGaugeRegistrar`(gauge 등록).
- 테스트: `PipelineMetricsTest`에 ingest outcome / heartbeat / pending gauge 검증 추가. (build + test green)

**Phase B로 연기**: `ingest_failure{reason}` 분류(auth/5xx/timeout)·`ingest_latency`(Timer)·auth-halt → inline 에러 처리(옵션 A)와 함께. Phase A는 *parity 검증*이 목표라 success/failure 카운트면 충분(failures도 운영상 드묾).

**verify (배포 후 — 미수행)**
- Grafana에서 `pipeline_ingest_success` rate ≈ 기존 `IngestQueueStats.doneLast10m`, `pipeline_ingest_failure` ≈ 큐 ERROR 증가분, `pipeline_heartbeat`/`pipeline_collect_pending_summoners` 정상 노출.

---

## 3. Phase B — inline 경로 + 조건 ①② (핵심 변경)

**변경 (surgical)**
- `CollectMatchIdsRunner`:
  - 의존성 교체: `MatchQueueEnqueuer` 제거 → `MatchJpaRepository`(existsById) + `IngestMatchDetail` 주입.
  - `collectForSummoner`: `enqueueAllIdempotent(matchIds,…)` → matchIds 루프:
    ```
    for (id : matchIds)
      if (matchRepo.existsById(id)) continue;        // dedup (ingest 앞 → API 콜 절약)
      try   ingestMatchDetail.execute(id, tier, ctx.patch());  // 1 API 콜 + save
      catch (RiotRateLimitedException e) throw e;      // 429 → 상위 backoff
      catch (Exception e) metrics.recordIngestFailure(reason(e));  // 옵션 A: skip
    ```
  - **조건①**: `runBatch` 의 `ctx == null`(patch 미등록)이면 수집 skip — `findRecentMatchIds` 비결정 fallback(`CollectMatchIdsRunner:108`) 차단.
  - **조건②**: `markMatchIdsCollected` 는 현 위치(`:86`, summoner 처리 후) 유지 — inline에선 ingest까지 끝난 뒤 표시되므로 자동 충족.
- `RegionalPipelineRunner`:
  - `executeTick` 의 Stage3 블록 + `runStage3WithTierRoundRobin` + `buildTierOrder` 제거.
  - → `if (collectMatchIdsRunner.hasPending()) runBatch(); else sleep(pollingInterval);` 단일 단계.
- `findMatchIdsPendingSummoners`: **tier 순 정렬** 추가(tier 우선순위 = summoner 선택 순서로 이전).

**테스트** (한글 `@DisplayName`)
- `CollectMatchIdsRunnerTest` / `RegionalPipelineRunnerTest` inline로 재작성.
- 신규: **재시작 시나리오** — summoner의 매치 일부만 ingest 후 크래시 → 재수집 시 dedup으로 중복 0, 미수집분 ingest, 데이터 손실 0.
- 신규: dedup(이미 match 테이블에 있는 id는 API 콜 안 함), 조건①(ctx==null이면 수집 skip).

**verify**
- 테스트 green.
- 스테이징/프로드(큐와 병행): inline 경로로 수집→저장→집계 정상, Phase A 메트릭이 정상 진행 표시.
- 재시작 mid-run → 손실/중복 0 확인.

---

## 4. Phase C — 큐 코드 제거 + 스키마 drop

> Phase B가 프로드에서 안정 확인된 *후*.

**제거 (main)**
- `MatchQueue`, `QueueStatus`, `MatchQueueJpaRepository`, `MatchQueueDispatcher`, `MatchQueueEnqueuer`
- `MatchIngestRunner`(큐 기반), `IngestQueueStats`, `MatchQueueGaugeRegistrar`, `AdminQueueController`(`/admin/queue/*`)
- `IngestQueueStatsJpaRepository` 등 큐 전용 repo

**제거 (test) — 9개**
- `MatchQueueDispatcherTest`, `MatchQueueDispatcherPhase5Test`, `MatchIngestRunnerTest`, `MatchQueueEnqueuerTest`, `MatchQueueTest`, `MatchQueueJpaRepositoryTransactionalTest`
- `CollectMatchIdsRunnerTest`, `RegionalPipelineRunnerTest`, `PipelineMetricsTest` — 큐 참조분 정리(Phase B에서 inline로 재작성된 것 확정)

**마이그레이션**
- `V7__drop_match_queue.sql` — `DROP TABLE match_queue;` (DONE 무한증가 문제도 동반 해소 — NFR-6)

**verify**
- `./gradlew build` green, `grep -r MatchQueue src/main` = 0.
- Grafana: 큐 게이지(`MatchQueueGaugeRegistrar`) 제거 → Phase A emit 메트릭으로 대체 확인.
- 프로드 디스크 추이(`bestduo_db_size_bytes`)에서 match_queue 증가분 소멸 확인.

---

## 5. 진행 순서 / 게이트

```
Phase A (메트릭) ──parity 검증(수일)──▶ Phase B (inline, 큐 병행) ──프로드 안정──▶ Phase C (제거 + V7 drop)
```
각 게이트 통과 전 다음 단계 착수 금지. 특히 **A→B 순서가 관측 공백 방지의 핵심**(조건③).

## 6. 스코프 밖 (참고)

- 병렬 executor fan-out → prod 키 시(ADR-008 §8)
- QR 표시 라벨/임계 → serving 트랙(requirements §8)
- poison skip-set → `ingest_failure_total` 가 필요를 보일 때만(reversible, ADR-008 §6.5)
