# CoverageBucket 수집 속도 최적화 플랜

**대상:** targetMatchCount=20,000 / tier=EMERALD / patch=최신

---

## 1. 현황 분석

### 시스템 구조

```
CoverageScheduler (10초마다)
  └─ CoverageSchedulingService.schedule()
       └─ determineNextWorkItem(bucket)
            ├─ verifiedPool < threshold → SEED_SUMMONERS
            ├─ readyMatchQueue < threshold → REFRESH_SUMMONERS
            └─ queuedMatchIds > 0 or recentIngested < threshold → INGEST_MATCH_DETAIL

WorkItemPoller (500ms마다)
  └─ WorkItem 1개 pick & execute
       └─ IngestMatchDetailWorker
            └─ MatchIngestWorker.execute(batchLimit, tier, patch)
                 └─ IngestMatchDetail.execute(matchId, ...)
                      └─ riotMatchLoader.loadMatch(matchId)  ← API call 1회/match
```

### Riot API Rate Limit

| 제한 | 값 |
|------|-----|
| 단기 | 20 req / 1초 |
| 장기 | 100 req / 2분 |

- 장기 제한 기준 처리량 상한: **50 matches/min = 3,000 matches/hour**
- 20,000 matches 최소 소요 시간: **약 6.7시간** (API 제한 천장)

### 병목 진단 과정

**Step 1. WorkItem 처리 시간 측정 (DB 쿼리)**

```sql
SELECT id, status, locked_at, updated_at,
       EXTRACT(EPOCH FROM (updated_at - locked_at)) AS duration_sec,
       retry_count, last_error
FROM work_item
WHERE type = 'INGEST_MATCH_DETAIL'
  AND patch = ?
  AND tier = 'EMERALD'
ORDER BY created_at DESC
LIMIT 20;
```

결과: INGEST WorkItem 완료 간격 **~2분**, 처리량 ~30 matches/WorkItem

**Step 2. match_queue 현황 확인**

```sql
SELECT status, count(*)
FROM match_queue
WHERE collection_tier = 'EMERALD'
GROUP BY status;
```

결과: `READY=40, RUNNING=30, DONE=420`
→ READY가 batch.ingest(50)보다 적어 매번 미달 처리 중

**Step 3. work_item 타입별 현황 확인**

```sql
SELECT type, status, count(*)
FROM work_item
WHERE tier = 'EMERALD' AND patch = ?
GROUP BY type, status;
```

결과: `REFRESH RUNNING=1, INGEST idle`

**핵심 원인 발견:**
`ready-match-queue threshold=50`으로 설정 → READY(40) < 50 이면 INGEST를 완전 차단하고 REFRESH만 실행. READY가 40개나 있어도 INGEST가 idle 상태.

---

## 2. 병렬화 가능 여부 검토

| 항목 | 내용 |
|------|------|
| rate limit 장기 | 100 req / 2분 |
| 병렬 3개 시 API 호출 | 50 × 3 = 150 calls → 2분 한도 즉시 초과 |
| 결론 | **병렬화 무의미, 오히려 429 증가** |

→ 병렬화 대신 **INGEST idle time 제거**에 집중

---

## 3. 최적화 내용

### Phase 1. application.yml 튜닝

| 설정 | 변경 전 | 변경 후 | 이유 |
|------|---------|---------|------|
| `batch.ingest` | 30 | 50 | WorkItem당 처리량 증가 |
| `batch.refresh` | 20 | 50 | REFRESH 1회에 더 많은 summoner 처리 |
| `threshold.verified-pool` | 20 | 100 | summoner pool을 두껍게 유지, SEED 재진입 빈도 감소 |
| `threshold.ready-match-queue` | 20 → 50 → **20** | **20** | 50으로 올렸다가 INGEST idle 발생 확인 후 20으로 조정 |
| `threshold.recent-ingest` | 5 | 20 | INGEST 트리거 민감도 향상 |
| `threshold.ingest-window-minutes` | 30 | 10 | 창을 좁혀 반응성 향상 |
| `duplicate-pending-limit` | 1 | 1 유지 | rate limit 때문에 병렬화 불필요 |

### Phase 2. determineNextWorkItem 로깅 추가

각 스케줄 사이클마다 결정 근거를 로그로 기록:

```
[Scheduling] bucketId=1 patch=15.8 tier=EMERALD deficit=19500
             verifiedPool=45 queuedMatchIds=28 recentIngested=2
             → INGEST_MATCH_DETAIL
```

확인 가능한 패턴:
- SEED/REFRESH가 지나치게 자주 끼어드는지
- `null` (idle) 사이클이 얼마나 되는지
- INGEST가 연속으로 돌고 있는지

---

## 4. 검증 쿼리

```sql
-- match_queue 상태 (READY가 threshold 근처에서 유지되는지)
SELECT status, count(*)
FROM match_queue
WHERE collection_tier = 'EMERALD'
GROUP BY status;

-- work_item 상태 (INGEST가 지속적으로 RUNNING인지)
SELECT type, status, count(*)
FROM work_item
WHERE tier = 'EMERALD' AND patch = ?
GROUP BY type, status;

-- INGEST WorkItem 처리 간격
SELECT id, status, locked_at, updated_at,
       EXTRACT(EPOCH FROM (updated_at - locked_at)) AS duration_sec,
       last_error
FROM work_item
WHERE type = 'INGEST_MATCH_DETAIL'
  AND patch = ? AND tier = 'EMERALD'
ORDER BY created_at DESC
LIMIT 20;
```

---

## 5. 이론 처리량 한계

| 항목 | 값 |
|------|-----|
| API rate limit 천장 | 50 matches/min |
| 20,000 matches 최소 시간 | ~6.7시간 |
| Phase 1 적용 후 기대 효과 | INGEST idle time 제거 → 천장에 근접 |

> **Note:** 처리 속도를 더 높이려면 Riot API Production key (rate limit 상향) 발급이 필요.
> Development key (100 req/2min) 기준으로는 6.7시간이 물리적 하한선.
