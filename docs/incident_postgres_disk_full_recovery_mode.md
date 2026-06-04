# 인시던트 포스트모템 — PostgreSQL "recovery mode" 크래시 루프 (디스크 풀)

- **발생일**: 2026-06-04
- **환경**: Railway (Hobby plan), PostgreSQL 16, Spring Boot 3 (Flyway + Hibernate)
- **영향**: 애플리케이션 부팅 실패 (전면 다운). 데이터 유실 0.
- **심각도**: SEV-1 (서비스 기동 불가)
- **근본 원인**: DB 데이터 볼륨 100% 포화 → Postgres가 복구 직후 체크포인트를 디스크에 쓰지 못해 `PANIC` → 무한 크래시 루프
- **결과**: 데이터 보존한 채 복구 완료, `match` 테이블 3.86GB → 2.3GB로 정리

---

## 1. 증상 (Symptom)

Spring Boot 기동 시 다음 예외로 컨텍스트 초기화 실패:

```
BeanCreationException: Error creating bean 'entityManagerFactory'
  → Failed to initialize dependency 'flywayInitializer'
  → Unable to obtain connection from database:
      FATAL: the database system is in recovery mode
SQL State : 57P03
```

처음엔 `entityManagerFactory` / `flywayInitializer` 빈 생성 실패처럼 보였지만, **스택트레이스 가장 안쪽(`Caused by`)** 이 진짜 원인이었다:

```
Caused by: org.postgresql.util.PSQLException: FATAL: the database system is in recovery mode
  SQL State : 57P03   (cannot_connect_now)
  at ...HikariPool.checkFailFast
```

> **교훈 ①** — 빈 생성 실패 스택은 "따라 죽은" 결과일 뿐이다. **맨 안쪽 `Caused by`** 와 **SQL State(`57P03`)** 가 진짜 신호. `57P03 = cannot_connect_now` 은 *애플리케이션 버그가 아니라* Postgres 서버가 "지금은 연결 못 받음"이라고 거절하는 상태다.

## 2. 트리거 (Trigger)

운영자가 Railway에서 불필요한 서비스(redis, 보조 AI 서비스)를 삭제 → **환경 redeploy** 발생 → 그 과정에서 **Postgres 컨테이너가 재시작**.

서비스 삭제 자체는 원인이 아니다. 재시작이 **이미 가득 찬 디스크** 위에서 일어나면서 잠재된 문제를 표면화시킨 **방아쇠**였을 뿐.

## 3. 진단 과정 (Investigation)

### 3.1 스택이 아니라 Postgres 로그를 본다

앱 로그가 아니라 **DB 서비스 로그**를 확인하자 결정적 단서가 나왔다:

```
LOG:   database system was not properly shut down; automatic recovery in progress
LOG:   redo starts at A/4A1790E0
LOG:   invalid record length at A/4A18FBA8: expected at least 24, got 0
LOG:   redo done at A/4A18FB70  ... elapsed: 0.00 s        ← WAL 복구는 매번 성공
LOG:   checkpoint starting: end-of-recovery immediate wait
PANIC: could not write to file
       "pg_logical/replorigin_checkpoint.tmp": No space left on device   ← 진범
LOG:   checkpointer process was terminated by signal 6: Aborted
LOG:   terminating any other active server processes        ← 전체 재시작 → 루프
```

여기서 두 가지를 읽어냈다:

- `invalid record length ... got 0` 은 **손상이 아니라** WAL의 끝(마지막 레코드)에 도달했다는 정상 표시. 바로 다음 줄 `redo done`이 0.00초에 떴다 → **WAL 복구는 매 사이클 성공하고 데이터는 온전하다.**
- 문제는 복구 **직후**다. Postgres는 복구가 끝나면 **end-of-recovery 체크포인트**를 반드시 디스크에 써야 하는데, 디스크가 0바이트라 그 작은 파일(`replorigin_checkpoint.tmp`, 수 KB)조차 못 써서 `PANIC` → 프로세스 강제 종료 → postmaster가 클러스터 전체 재시작 → 다시 복구 → 또 PANIC … **무한 크래시 루프**.

### 3.2 디스크가 진짜 꽉 찼는지 확인

크래시 루프 중인 컨테이너에는 셸을 붙일 수 없어서, **Custom Start Command를 `sleep infinity`로 바꿔** Postgres를 안 띄운 채 컨테이너만 살린 뒤 진입:

```bash
df -h /var/lib/postgresql/data
# /dev/zd14816  4.4G  4.4G  0  100%   ← 포화 확정

du -sh pgdata/*
# 4.3G  base      ← 실제 데이터가 거의 전부
# 81M   pg_wal
```

`log/`(서버 로그)나 큰 `pgsql_tmp/`(임시파일) 같은 **안전하게 지울 쓰레기가 전혀 없었다.** 4.3G가 통째로 `base/`(실데이터).

### 3.3 무엇이 디스크를 채웠나

`match` 테이블(LoL 매치 원본 JSON payload)이 주범:

```sql
SELECT pg_size_pretty(pg_total_relation_size('match'));   -- 3863 MB
-- 본체(heap) 141MB + 인덱스 45MB + TOAST(압축 JSON) ~3.6GB
```

`archive.r2`(R2 아카이브)와 `aggregate.scheduler`/`retention`(오래된 패치 자동 정리)이 **둘 다 비활성**이라 매치 데이터가 패치마다 무한 누적된 것이 근본 원인.

## 4. 첫 번째 시도와 실패 — `DELETE`의 함정

디스크에 몇 MB의 여유만 만들면 체크포인트가 성공해 기동된다는 점을 이용해, **`pg_wal`의 미래 예약 세그먼트**(현재 REDO 파일보다 번호가 큰 것)를 안전하게 삭제해 임시로 Postgres를 띄웠다. (REDO 파일은 절대 손대지 않음 — 복구에 필요.)

띄운 뒤, 기존에 만들어 둔 `cleanupArchived`(R2 아카이브 완료된 패치를 `DELETE`) 엔드포인트로 16.9를 지우려 했다 → **즉시 다시 크래시.**

이유:

1. 한 트랜잭션에서 대량 `DELETE FROM match WHERE patch=16.9` → **WAL 폭증** → 확보했던 여유를 순식간에 초과 → 또 `PANIC`.
2. 설령 성공해도 **`DELETE`는 OS에 디스크 공간을 반환하지 않는다.** 행을 "죽은(dead)" 것으로 표시만 할 뿐, 파일 크기는 그대로. 공간 회수는 `VACUUM FULL`(테이블 재작성)이 필요한데, 그건 **여유 공간이 있어야** 돈다.

> **교훈 ②** — 거의 가득 찬 디스크에서:
> - `DELETE` = 독 (WAL 폭증 + 공간 회수 0)
> - `VACUUM FULL` = 불가 (재작성용 여유 공간이 없음 → catch-22)
> - **즉시 공간을 반환하는 건 `TRUNCATE` / `DROP TABLE` / `DROP INDEX` 뿐** (파일을 unlink)

## 5. 해결 (Resolution)

볼륨 증설은 불가능했다 (Hobby plan 상한 5GB에 이미 도달). 따라서 **16.9만 제거하면서 공간을 회수**하는 유일하게 안전한 길을 택했다 — *keep-set을 서버 밖으로 빼두고 통째로 TRUNCATE 후 복원*.

```sql
-- 1. 남길 데이터(최신 2개 패치)를 로컬로 추출 (서버 디스크 불사용, psql \copy)
\copy (SELECT * FROM match
       WHERE split_part(game_version,'.',1)||'.'||split_part(game_version,'.',2)
             IN ('16.10','16.11')) TO 'match_keep.tsv'
-- → COPY 559060   (453,578 + 105,482)

-- 2. 안전핀: 추출 완료를 행 수로 검증 (이 파일이 유일한 백업)
--    wc -l match_keep.tsv  → 559060 확인 전엔 절대 TRUNCATE 금지

-- 3. 통째로 비우기 → 26MB의 여유로도 3.86GB 즉시 회수
TRUNCATE TABLE match;
-- df: 100% → 13% (564M used)

-- 4. 남긴 데이터 복원 (Postgres가 재압축 → 서버엔 다시 ~2.3GB)
\copy match FROM 'match_keep.tsv'
-- → COPY 559060

-- 5. 검증
SELECT count(*) FROM match;                 -- 559060
-- match_data 2287 MB, db_total 2777 MB
VACUUM ANALYZE match;
```

**사전 안전 점검** (코드에서 확인):
- `match` PK = `match_id` (문자열) → 시퀀스 없음, COPY 후 재설정 불필요
- `match`를 참조하는 외래키 없음 → `TRUNCATE` CASCADE 문제 없음

추가로, 대량 작업이 WAL을 다시 폭증시키지 않도록:

```sql
ALTER SYSTEM SET max_wal_size = '48MB';
ALTER SYSTEM SET min_wal_size = '32MB';
SELECT pg_reload_conf();
```

### 결과
- 크래시 루프 종료, **데이터 유실 0** (16.10·16.11 559,060행 보존, 16.9 제거)
- `match` 3.86GB → 2.3GB, DB 2.78GB
- TSV는 압축이 풀린 평문이라 3.8GB였지만(디스크의 압축 jsonb보다 큼), 재적재 시 재압축되어 서버 크기는 부풀지 않음

> **부수 메모 (WAL idle 미회수)** — `max_wal_size`를 낮춰도 idle 상태에선 `pg_wal`의 잉여 "미래 예약 세그먼트"가 즉시 줄지 않는다. WAL 정리는 *현재 지점보다 뒤로 밀린* 세그먼트를 대상으로 하므로, **쓰기 활동이 있어야**(앱 재가동) 정상 운영 중 자동으로 `max_wal_size` 수준까지 트림된다.

## 6. 타임라인 (요약)

| 단계 | 행동 | 결과 |
|---|---|---|
| 1 | 앱 스택 대신 **DB 로그** 확인 | `PANIC: No space left` → 디스크 풀 + 크래시 루프 확정 |
| 2 | Start Command=`sleep infinity`로 셸 확보, `df`/`du` | 4.3G가 전부 실데이터, 지울 쓰레기 없음 |
| 3 | 안전한 미래 WAL 세그먼트 삭제 → 임시 기동 | Postgres 일시 가동 |
| 4 | `cleanupArchived`(DELETE) 시도 | WAL 폭증 → 재크래시 (DELETE의 함정 학습) |
| 5 | keep-set `\copy` → `TRUNCATE` → `\copy` 복원 | 16.9 제거 + 공간 회수, 데이터 보존 |
| 6 | `max_wal_size` 축소 | WAL 폭증 재발 방지 |

## 7. 재발 방지 (Action Items)

- [ ] **`match` 패치별 파티셔닝** — 오래된 패치 정리를 `DROP PARTITION`으로 (즉시·무WAL·무VACUUM). 제약된 디스크에서 retention의 정답. `DELETE` 기반 정리의 catch-22를 원천 차단.
- [ ] **자동 정리 활성화** — `archive.r2.enabled=true` + `aggregate.scheduler.enabled=true` + `retention.patches`를 2~3개로. 패치가 쌓이기 전에 R2로 아카이브 후 정리.
- [ ] **디스크 사용률 알림** — Railway Metrics 80% 임계 알림. "기동 불가"가 되기 전에 경고받기.
- [ ] **`cleanupArchived` 개선** — 대량 단일 `DELETE` 금지. 작은 배치 + 빈번한 커밋, 또는 본 인시던트의 `COPY`+`TRUNCATE` 패턴으로 대체.
- [ ] **용량 모니터링 지표** — `pg_database_size`, 패치별 행 수/바이트를 주기 수집.

## 8. 핵심 교훈 (Lessons Learned)

1. **에러는 가장 안쪽부터 읽는다.** 빈 생성 실패 → Flyway → Hikari는 전부 증상이고, 진범은 맨 안쪽 `FATAL ... 57P03`. SQL State 하나가 "앱 버그 아님 / DB 서버 상태"를 단번에 가른다.
2. **`recovery mode` 무한 루프 ≠ 데이터 손상.** WAL 복구는 성공하는데 *복구 후 체크포인트*가 디스크 풀로 실패하는 패턴. 데이터는 멀쩡하다 — 침착하게 공간만 확보하면 된다.
3. **제약된 디스크에서 공간 회수는 도구 선택이 전부다.** `DELETE`(독) / `VACUUM FULL`(공간 필요) / `TRUNCATE`·`DROP`(즉시 회수). 상황에 맞는 무기를 골라야 한다.
4. **파괴적 작업엔 안전핀을 건다.** `TRUNCATE` 전 추출 파일을 **행 수로 검증**(559060)하고, PK/외래키를 **코드로 사전 확인**했기에 데이터 유실 0으로 끝낼 수 있었다.
5. **용량은 운영 지표다.** retention/아카이브/알림이 꺼져 있던 게 근본 원인. 기능보다 "꽉 차기 전에 비우는 자동화"가 먼저였어야 했다.

## 9. 참고 — 관련 PostgreSQL 개념

- **WAL (Write-Ahead Log)**: 데이터 파일 변경 전에 변경 내역을 먼저 기록하는 내구성 장치. 크래시 시 이걸 재생(redo)해 복구. `pg_wal/`의 REDO 파일은 복구 필수 → 삭제 금지, 미래 예약 세그먼트는 안전.
- **TOAST**: 큰 컬럼(여기선 jsonb payload)을 압축해 별도 저장. `match`의 덩치 대부분이 TOAST였고, `pg_relation_size`(141MB)와 `pg_total_relation_size`(3.86GB)의 차이가 그 증거.
- **end-of-recovery checkpoint**: 크래시 복구 직후 강제 체크포인트. 이걸 디스크에 못 쓰면 기동 자체가 실패 → 본 인시던트의 직접 사인.
- **`57P03 (cannot_connect_now)`**: 서버가 시작/복구/종료 중이라 연결을 일시 거절하는 표준 SQL State.
