# ADR-007: Archive endpoint OOM 해결 — Hibernate L1 캐시 우회 projection + temp file 스트리밍

> 작성일: 2026-05-17
> 상태: 채택(Accepted)
> 관련 PR: [#82](https://github.com/NamMinwu/BestDuo_BE/pull/82) (cleanup endpoint), [#83](https://github.com/NamMinwu/BestDuo_BE/pull/83) (heap dump 인프라, 분석 후 제거), [#84](https://github.com/NamMinwu/BestDuo_BE/pull/84) (본 ADR 대상 fix)
> 대상 모듈: `com.bestduo_BE.archive`, `com.bestduo_BE.common.infra.persistence`
> 영향 파일:
> - 신규: `MatchPayloadProjection`, `MatchJpaRepository#findPayloadPageByTierAndPatch`
> - 수정: `MatchArchiver` (entity fetch → projection, `ByteArrayOutputStream` → temp file)
> - 제거: `DebugAdminController`, `HeapDumpUploader` (분석 도구, 일회성)

---

## 1. 결정 요약

| 항목 | 변경 전 | 변경 후 |
|---|---|---|
| match 데이터 fetch | `Match` 엔티티 (`findPageByTierAndPatch`) | `MatchPayloadProjection` interface projection (`findPayloadPageByTierAndPatch`) |
| Hibernate L1 캐시 등록 | O — 페이지마다 누적 | X — projection 은 영속성 관리 대상 아님 |
| gzip 출력 버퍼 | `ByteArrayOutputStream` (메모리) | temp 파일 (`Files.createTempFile`) |
| S3 업로드 방식 | `RequestBody.fromBytes(byte[])` | `RequestBody.fromFile(Path)` (chunked) |
| 5-tier 순회 결과 (4GB 서버) | OOM (502) | 성공 (338,968 row / 490 MB upload) |

핵심 시그니처:

```java
public interface MatchPayloadProjection {
  String getMatchId();
  String getPayloadJson();
}

List<MatchPayloadProjection> findPayloadPageByTierAndPatch(
    String tier, String patch, String afterId, int pageSize);
```

---

## 2. 문제 정의

### 2-1. 증상

`POST /admin/archive/match-payload?patches=16.8&tiers=CHALLENGER,GRANDMASTER,MASTER,DIAMOND,EMERALD` 호출 시 Railway prod (4GB memory) 에서 502 응답.

서버 로그:
```
java.lang.OutOfMemoryError: Java heap space
```

다만 다음 조건에서는 OOM 발생하지 않음:
- **단일 tier** (예: `tiers=EMERALD`) — patch 16.8 / EMERALD 단독은 정상 종료
- **로컬 dev 환경** — 1MB synthetic seed 데이터로는 재현 안 됨

→ 5-tier 순회를 한 HTTP 요청 안에서 돌렸을 때만 터지는, 누적성 문제로 추정.

### 2-2. 초기 가설과 함정

heap dump 분석 전 가장 의심받은 코드:

```java
ByteArrayOutputStream buf = new ByteArrayOutputStream();
try (GZIPOutputStream gzip = new GZIPOutputStream(buf)) {
  streamPayloadsInto(patch, tier, gzip);  // gzip 압축본이 buf 에 누적
}
byte[] body = buf.toByteArray();          // 한 번 더 복사
s3Client.putObject(..., RequestBody.fromBytes(body));
```

가설: gzip 압축본이 메모리에 통째로 누적 + `toByteArray()` 가 한 번 더 복사 → peak 메모리 2배.

이 가설만 보고 fix 했다면 **잘못된 곳을 고쳤을 것**. 실제 원인은 다른 곳에 있었다.

---

## 3. 진단 — heap dump 분석 절차

가설 검증을 위해 prod 의 실제 heap 상태를 캡처하는 인프라를 임시로 만들었다.

### 3-1. heap dump 자동 캡처 + 회수 경로

PR [#83](https://github.com/NamMinwu/BestDuo_BE/pull/83) 에서 구축:

1. **JVM 옵션** (Railway env 변수):
   ```
   JAVA_TOOL_OPTIONS=-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps -XX:+ExitOnOutOfMemoryError
   ```
2. **Railway Persistent Volume** `/dumps` 마운트 — OOM 시 컨테이너 재시작에도 hprof 보존
3. **`POST /admin/debug/upload-heap-dump`** — `/dumps/*.hprof` 파일을 R2 로 PUT 해서 로컬에서 다운로드 가능

### 3-2. 첫 시도의 막힘

prod 에서 OOM 알람은 떴지만 `/dumps` 에 hprof 가 안 생겼다. 로그는:
```
Picked up JAVA_TOOL_OPTIONS: -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/dumps ...
```
JVM 이 옵션을 picked up 한 것은 확인됨. 그런데:
- "Dumping heap to ..." 로그 없음
- "Terminating due to ..." 로그 없음
- 앱은 죽지 않고 살아있음

> **추정 원인**: 컨테이너의 cgroup memory limit 에 먼저 도달해 Linux OOM-killer (SIGKILL) 가 JVM 의 OOM throw 보다 먼저 발생. JVM 은 자기 자신이 OOM 을 던질 기회를 못 얻음. 또는 부분 OOM 상태에서 JVM 이 그래도 살아남아 hprof 작성을 시작하지 못함.

### 3-3. 우회 — 로컬 앱 + prod DB

heap dump 가 prod 에서 안 잡히는 함정을 우회하기 위해, **로컬 IntelliJ** 에서 앱을 띄우고 **DB 만 prod 를 가리키게** 변경해서 같은 archive 호출을 재현.

안전 장치:
- 로컬에서는 archive endpoint 만 호출, cleanup 절대 호출 금지 (read-only)
- 배경 스케줄러 모두 비활성화: `AGGREGATE_SCHEDULER_ENABLED=false`, `PIPELINE_RUNNER_ENABLED=false`, `PATCH_SYNC_ENABLED=false`

로컬은 IntelliJ Profiler 가 직접 hprof 를 잡을 수 있어 prod 의 cgroup 문제를 피한다.

### 3-4. hprof 분석 결과

| 객체 | Retained Size | Note |
|---|---:|---|
| `byte[]` (전체) | 222.81 MB | top consumer |
| `String` (전체) | 213.89 MB | payload_json 문자열 |
| **`StatefulPersistenceContext`** | **212.80 MB** | **단일 인스턴스, GC Root: Java Frame** |
| `ByteArrayOutputStream` (archive 용) | 534 KB | 후보 가설이었으나 무관 수준 |

`StatefulPersistenceContext` 는 Hibernate 의 **L1 캐시** (first-level cache) 구현체. 단일 인스턴스가 212 MB 를 retain 하고 있다는 것은, 한 EntityManager 안에 엄청난 수의 엔티티가 영속 상태로 매달려 있다는 뜻.

또한 GC Root 가 **Java Frame** (현재 스택 프레임) 이므로, 이 인스턴스는 **현재 처리 중인 HTTP 요청 스레드에 묶여 있고** GC 가 손댈 수 없다.

### 3-5. 메커니즘 — 왜 L1 이 212 MB 가 됐나

```java
private int streamPayloadsInto(String patch, Tier tier, GZIPOutputStream gzip) {
  String cursor = "";
  while (true) {
    List<Match> page = matchRepository.findPageByTierAndPatch(...);
    // ↑ JPA 가 ResultSet → Match 엔티티 변환하면서 L1 캐시에 등록
    if (page.isEmpty()) break;
    for (Match m : page) {
      gzip.write(m.getPayloadJson().getBytes(...));
      cursor = m.getMatchId();
    }
  }
}
```

- 페이지마다 `Match` 엔티티 500개가 L1 캐시에 등록됨.
- Spring Boot 의 OSIV (Open Session In View) 기본값 `true` 가 HTTP 요청 끝까지 `EntityManager` 를 열어둠.
- 한 요청 안에서 5 tier 순회하면 L1 캐시가 비워지지 않고 계속 누적.

수치 추정 (검증 후 실측치):
- patch 16.8 MASTER 99,192 row × 평균 payload 1.4 KB ≈ 138 MB
- 5 tier 합산 ≈ **490 MB raw** (gzip 압축 후 기준)
- gzip 압축 전 원본 string + Match 엔티티 오버헤드까지 합치면 L1 이 1 GB+ 까지 누적 → 4 GB 컨테이너에서 cgroup limit 강타

### 3-6. 단일 tier 가 통과한 이유

- patch 16.8 EMERALD 단독 = 141,539 row → L1 이 ~200 MB 정도 누적, 4 GB heap 안에 여유 있음
- 단일 HTTP 요청 끝나면 EntityManager 닫히고 L1 통째로 GC 됨
- → "5-tier 순회 시에만 OOM" 의 정확한 원인 일치

---

## 4. 검토한 대안

### 옵션 A — `EntityManager.clear()` 명시 호출

각 페이지 처리 후 또는 각 tier 처리 후 명시적으로 L1 비우기.

| 장점 | 단점 |
|---|---|
| 최소 코드 변경 | Match 엔티티 fetch 자체 비용은 그대로 — 18+ 컬럼 read |
| | dirty checking 부담 그대로 (read-only 인데도 변경 추적함) |
| | `clear()` 호출 누락 시 다시 OOM — 방어선이 약함 |

→ 기각. 원인을 우회하지 근본 해결이 아님.

### 옵션 B — OSIV 끄기 (`spring.jpa.open-in-view=false`)

전역으로 OSIV 비활성화.

| 장점 | 단점 |
|---|---|
| 트랜잭션이 메서드 단위로 끝나면서 L1 도 같이 정리됨 | **다른 endpoint** 가 controller 에서 lazy loading 하고 있으면 `LazyInitializationException` 줄줄이 발생 |
| | archive 외 기능 회귀 위험이 큼 — blast radius 너무 넓음 |

→ 기각. archive 만의 문제에 전역 설정 변경은 비대칭.

### 옵션 C — `@Transactional(REQUIRES_NEW)` 로 tier 마다 새 트랜잭션

archive 의 tier 루프를 별도 메서드로 빼고 `REQUIRES_NEW` 로 매 tier 마다 새 트랜잭션 시작.

| 장점 | 단점 |
|---|---|
| tier 사이에 L1 리셋 | OSIV 가 바깥에서 또 세션을 잡고 있어서 효과 제한적 |
| | tier 안의 누적은 그대로 (한 tier 만으로도 이론적으로 OOM 가능) |

→ 기각. 부분적 효과 + 복잡도 증가.

### 옵션 D — interface projection 으로 L1 우회 (채택)

`Match` 엔티티 대신 `MatchPayloadProjection` interface projection 으로 fetch. Spring Data JPA 가 `Proxy.newProxyInstance` 로 만든 read-only proxy 객체는 영속성 컨텍스트에 등록되지 않으므로 페이지가 GC 대상이 됨.

| 장점 | 단점 |
|---|---|
| L1 캐시에 아예 등록 안 됨 — 근본 해결 | aggregate 경로 (`AggregateBottomDuoFromMatch`) 는 같은 메서드 쓰는데, 그쪽도 같이 바꿔야 하나? 라는 의문 |
| SELECT 컬럼 2개 (`match_id`, `payload_json`) 로 축소 — 네트워크 트래픽 감소 | |
| dirty checking 부담 없음 | |
| archive 만 새 메서드 추가, 기존 메서드는 aggregate 가 그대로 사용 — 영향 범위 명확 | |

→ **채택**.

aggregate 도 함께 고치지 않은 이유는 [§7](#7-aggregate-경로를-함께-수정하지-않은-이유) 참조.

### 보조 — temp 파일 스트리밍

L1 캐시가 풀리면 `ByteArrayOutputStream` 의 압축본 누적은 절대값이 작아 보이지만 (534 KB), 다음 두 경우에 다시 OOM 의 후보가 됨:

- Riot patch 16.x 에서 payload 크기 증가 추세
- 한 tier 의 row 수가 더 늘어났을 때

`Files.createTempFile` → `GZIPOutputStream(FileOutputStream)` → `RequestBody.fromFile` 로 변경하면 gzip 압축본이 디스크로 흘러가고, AWS SDK 가 8 MB chunk 로 읽어 업로드. 메모리에 한 번에 올리지 않음. `finally` 에서 `Files.deleteIfExists` 로 정리.

L1 우회만으로도 즉시 OOM 은 해결되지만 같은 PR 에 묶어 future-proof.

---

## 5. 채택 근거 — 왜 옵션 D 인가

### 5-1. 근본 원인 제거

L1 캐시 누적이 진짜 원인이라는 게 hprof 의 retained size 와 GC Root 경로로 증명됨. projection 은 L1 에 등록 자체를 안 하므로 누적 자체를 막는다. "비우기" (옵션 A) 가 아니라 "처음부터 안 담기" 로 가는 것.

### 5-2. 영향 범위 최소

- 기존 `findPageByTierAndPatch` 는 그대로 두고 `findPayloadPageByTierAndPatch` 새로 추가
- `MatchArchiver` 만 새 메서드를 가리키도록 변경
- aggregate 경로 (`AggregateBottomDuoFromMatch`) 영향 없음 — cron 에서 tier 별로 분리 실행되어 OOM 위험 낮음

OSIV 끄기 같은 전역 변경 대비 blast radius 가 압도적으로 작다.

### 5-3. 부수 효과로 SELECT 비용도 감소

`Match` 엔티티 fetch 시 SELECT 컬럼이 18+ 개 (`match_id`, `payload_json`, `game_version`, `collection_tier`, `region`, `created_at`, ... ). projection 은 정확히 필요한 2 컬럼만 (`match_id`, `payload_json`).

network roundtrip 당 페이로드 감소 + Hibernate 의 엔티티 변환 비용 감소.

### 5-4. over-engineering 금지 원칙

- 비동기 큐 (SQS/Redis): 인프라 추가, 단순 archive 작업에 비대칭
- Spring Batch chunked job: 청크/Step/Job 추상화 부담
- streaming JDBC ResultSet (HOLD_CURSOR): JPA 추상화 깨야 함

이번 fix 는 **JPA 의 기능 (projection)** 안에서 끝난다. 새 의존성 없음.

---

## 6. 검증 결과

### 6-1. archive 처리 결과 (OOM 해결 검증)

PR #84 머지 후 prod 에서 실측:

```json
POST /admin/archive/match-payload?patches=16.8&tiers=CHALLENGER,GRANDMASTER,MASTER,DIAMOND,EMERALD

{
  "totalArchived": 338968,
  "totalBytes": 490548084,
  "results": [
    { "patch": "16.8", "tier": "CHALLENGER",  "archivedCount":    213, "bytes":    278872 },
    { "patch": "16.8", "tier": "GRANDMASTER", "archivedCount":   1256, "bytes":   1681235 },
    { "patch": "16.8", "tier": "MASTER",      "archivedCount":  99192, "bytes": 143335304 },
    { "patch": "16.8", "tier": "DIAMOND",     "archivedCount":  96768, "bytes": 140232136 },
    { "patch": "16.8", "tier": "EMERALD",     "archivedCount": 141539, "bytes": 205020537 }
  ]
}
```

수치 의미:
- **338,968 row** 를 **단일 HTTP 요청** 안에서 처리 — OOM 발생 전 동일 호출과 같은 부하
- 압축 후 R2 업로드 **490 MB**
- 4 GB Railway 컨테이너에서 OOM 없이 완료

### 6-2. 무결성 검증

| 검증 | 방법 | 결과 |
|---|---|---|
| R2 객체 크기 | `HeadObject` 의 `ContentLength` vs response `bytes` | 5 객체 모두 일치 |
| row 수 누락 | `count(*)` group by tier (patch=16.8) vs `archivedCount` | 모든 tier 일치 |

두 검증 다 통과 → 페이지네이션 누락 없음 + 네트워크 중간 끊김 없음.

### 6-3. cleanup 후 디스크 회수 측정

archive 직후 `/admin/archive/cleanup-archived` 로 16.8 의 match 행 삭제. autovacuum 1 사이클 통과 후 `pgstattuple` 로 실측한 회수 공간:

| 영역 | total | reusable free | free % |
|---|---:|---:|---:|
| 메인 heap (`match`) | 141 MB | **102 MB** | 72.7% |
| TOAST (`pg_toast.pg_toast_*`) | ~2,980 MB | **1,751 MB** | ~58.8% |
| **합계** | **3,121 MB** | **1,853 MB** | **59.4%** |

> TOAST 가 본체인 이유: `match.payload_json` (jsonb) 은 ~2 KB 임계치 넘으면 PostgreSQL 이 자동으로 별도 TOAST 테이블로 분리해 PGLZ 압축 저장한다. 그래서 메인 heap (141 MB) 은 메타데이터만, 실제 페이로드 무게는 TOAST 에 있다. cleanup 의 진짜 효과도 TOAST 의 free_space 에 나타남.

상태 전이:

| 시점 | n_live_tup | n_dead_tup | total disk |
|---|---:|---:|---:|
| cleanup 전 | ~703,720 | 0 | 3,121 MB |
| cleanup 직후 | 364,752 | ~338,968 | 3,121 MB (변화 없음 — DELETE 는 파일 안 줄임) |
| autovacuum 후 | 364,752 | **0** | 3,121 MB + 내부 free 1,853 MB |

→ autovacuum 이 dead tuple 을 모두 정리해 1,853 MB 를 **재사용 가능 공간** 으로 전환. OS 디스크 사용량은 동일하지만, 후속 patch (16.11, ...) ingest 가 추가 디스크 할당 없이 이 공간을 재사용한다.

### 6-4. Net storage 효과

| 관점 | 변화 |
|---|---:|
| DB hot storage 회수 (재사용 가능) | **+1,853 MB** |
| R2 cold storage 추가 | **−490 MB** |
| **Net storage 절감** | **≈ 1,363 MB (~1.36 GB)** |
| DB 디스크 free 비율 | **59.4%** |

raw 데이터는 손실 없이 R2 에 영구 보관, hot DB 부담만 분리.

### 6-5. 안전장치 동작 검증

PR #82 의 `protected_latest=2` 룰이 의도대로 동작:

| patch | rows (cleanup 후) | 처리 |
|---|---:|---|
| 16.10 | 33,244 | protected (최신 1위) — cleanup 호출 시 거부 대상 |
| 16.9 | 331,508 | protected (최신 2위) — cleanup 호출 시 거부 대상 |
| 16.8 | 0 | archive + delete 성공 |

진행 중 patch (16.9, 16.10) 가 정확히 보호되어 운영자 실수로 hot 데이터가 사라질 가능성 차단.

---

## 7. aggregate 경로를 함께 수정하지 않은 이유

`AggregateBottomDuoFromMatch` 도 `findPageByTierAndPatch` 를 쓰고 같은 L1 캐시 잠재 문제가 있다. 그러나 이번 PR 에 묶지 않았다.

| 이유 | 설명 |
|---|---|
| OOM 미발생 | cron 으로 tier 별로 분리 실행됨 — 한 HTTP 요청에 5 tier 가 모이지 않음 |
| 호출 빈도 | 일 1회 cron, archive 와 트리거 패턴 다름 |
| YAGNI | 실측 문제 없는 곳을 함께 고치면 회귀 위험 + PR 범위 비대 |

> 만약 aggregate cron 이 한 사이클에 여러 tier 를 같은 EntityManager 로 처리하는 구조로 바뀌면 그 시점에 같은 projection 패턴을 옮기면 됨. 현재 구조에서는 위험 없음.

---

## 8. 검증 인프라를 일회성으로 만든 이유

PR #83 의 heap dump uploader (`/admin/debug/upload-heap-dump`, `HeapDumpUploader`) 는 진단 끝나자마자 본 PR 에서 제거했다.

| 유지 시 비용 | 제거 시 손실 |
|---|---|
| 운영 endpoint 표면 증가 (admin API key 만 통과하면 호출 가능) | 다음 OOM 분석 시 다시 만들어야 함 (코드 ~120 줄) |
| `archive.r2.enabled=true` 조건부지만 항상 빈 응답이라도 노출 | |

trade-off 가 명백해 제거 쪽. 단, JVM 옵션 (`HeapDumpOnOutOfMemoryError`) 과 `*.hprof` `.gitignore` 는 유지 — JVM 자체가 dump 를 만드는 인프라는 비용이 0 이고 미래 OOM 시 즉시 활용 가능.

---

## 9. 후속 영향

### 9-1. 가능해진 것

- **5-tier 일괄 archive** — 운영자가 한 patch 전체를 한 번에 archive 가능
- **추가 tier 확장** — IRON/BRONZE/SILVER 까지 동일 endpoint 로 처리해도 메모리 부담 안정적
- **추가 patch archive 예측 가능** — 16.8 실측 (row 당 평균 약 5.5 KB DB 점유, 압축 후 1.45 KB) 기반으로, 향후 16.11 출시 시 16.9 archive 호출하면 약 1.7 GB DB 회수 + 480 MB R2 추가 예상

### 9-2. 운영 변경점

- archive endpoint 의 peak memory 가 row 수와 무관해짐 — 페이지 단위 (500 row × payload_json 크기) 가 상한
- 디스크 사용량 ephemeral 증가 — temp 파일 (압축 후 ~200 MB) — `finally` 에서 즉시 정리됨

### 9-3. 잔존 리스크

- **temp 파일 디렉토리 용량** — Railway ephemeral disk 가 50 GB 라 ~200 MB 는 무관, 그러나 동시 호출 시 누적 가능. 현재는 admin endpoint 라 동시성 낮음.
- **aggregate 의 동일 패턴 잠재** — §7 참조. 현재 위험 없으나 모니터링 필요.
- **page size 500 의 적절성** — 페이로드가 더 커지면 페이지 단위 메모리도 비례 증가. 현재는 충분 여유.

---

## 10. 교훈

> **"의심스러운 코드"와 "진짜 원인"은 보통 다르다. 가설로 fix 하지 말고 dump 로 확인 후 fix 한다.**

이번 케이스의 의심받은 코드는 `ByteArrayOutputStream` 누적이었고 (실제로 메모리 누수처럼 보이는 패턴), 그것만 고쳤다면 OOM 은 그대로였을 것이다. 진짜 원인은 코드 한 줄 (`List<Match> page = matchRepository.find...`) 에 있었고, 그것이 Hibernate L1 + OSIV 와 만나 누적되었다.

**heap dump 의 retained size + GC Root 경로** 가 가설 대신 사실을 주는 도구였다.

부차 교훈:
- Spring Boot OSIV 기본값(`true`) 은 컨트롤러에서 lazy loading 을 편하게 해주지만, **bulk read 경로에서는 함정**. 단일 요청 처리 시간이 길면 L1 이 그 시간 동안 비워지지 않는다.
- `*Repository` 의 fetch 메서드를 추가할 때, 그 결과를 **entity 로 받을지 projection 으로 받을지** 는 단순한 스타일 선택이 아니라 메모리 특성을 결정하는 의사결정이다. read-only + 대량 처리 경로면 projection 이 기본.

---

## 11. 참고

- `src/main/java/com/bestduo_BE/common/infra/persistence/projection/MatchPayloadProjection.java`
- `src/main/java/com/bestduo_BE/common/infra/persistence/repository/MatchJpaRepository.java#findPayloadPageByTierAndPatch`
- `src/main/java/com/bestduo_BE/archive/application/MatchArchiver.java`
- [PR #84](https://github.com/NamMinwu/BestDuo_BE/pull/84) — 본 ADR 대상 fix
- [PR #83](https://github.com/NamMinwu/BestDuo_BE/pull/83) — 진단용 heap dump 인프라 (분석 후 제거)
- [PR #82](https://github.com/NamMinwu/BestDuo_BE/pull/82) — cleanup endpoint (archive 의 짝)
- Spring Data JPA docs — [Interface-based Projections](https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html#projections.interfaces)
- Hibernate User Guide — `StatefulPersistenceContext`, first-level cache
