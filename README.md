# BestDuo_BE

## Overview (English)
BestDuo_BE is the backend that powers **bestduo**, a League of Legends analytics tool that focuses on bottom-lane duo synergy. The service collects Emerald+ solo-queue summoners, keeps their match history up to date, and aggregates win rate, pick rate, ranking, and counter data for every ADC/support pairing per patch. All data is sourced directly from Riot's league-v4 and match-v5 APIs and is used strictly for educational statistics—there are no automation hooks or gameplay advantages.

### Key Capabilities
- **Seed → Refresh → Consume pipeline** guarded by Riot request budgets to continuously bring fresh solo-queue data.
- **Match payload storage** so the raw Riot match-v5 JSON is saved before domain-specific parsing.
- **Bottom duo extraction & aggregation** to compute synergy scores, tier-based rankings, and matchup counters from historical matches.
- **Public REST APIs** such as `/bottom-duo/stats`, `/bottom-duo/detail`, and `/bottom-duo/counters` that the frontend consumes.
- **Admin tooling** (`/admin/session`, `/collect`, etc.) to manually trigger sessions, enqueue matches, or inspect the collection queue.

### High-Level Flow
1. `SeedBootstrapRun` pulls league entries by tier/division and registers new summoner seeds.
2. `MatchIdsFinder` fetches recent match IDs which are pushed into the `match_queue`.
3. `CollectMatchDetailAndSaveRaw` loads match-v5 payloads, stores them, extracts bottom duos, and schedules new participants for expansion.
4. Aggregators compute tier-scoped stats and write them into materialized tables that the presentation layer reads.
5. `ViewBottomDuo*` use cases resolve patch versions, look up champion metadata, and expose the aggregated insights through controllers.

### API Surface (Selected)
- `GET /bottom-duo/stats`: global stats (win/pick/ban rate, ranking, tier delta) with filtering by tier, patch, or specific champions.
- `GET /bottom-duo/detail`: matchup breakdown for a specific duo versus opposing duos.
- `GET /bottom-duo/counters`: lowest-win-rate counters for a selected duo.
- `POST /collect/match/{matchId}`: manual ingestion of a Riot match with a chosen tier label.
- `POST /admin/session/run`: kick off a budgeted session that runs the seed, refresh, and consume phases sequentially.

### Tech & Monitoring
- Spring Boot + Gradle project structure with layered `application`, `domain`, `infra`, and `presentation` packages.
- Persistence via JPA repositories for matches, queues, and aggregated stats.
- Rate limiting handled by `DualWindowRateLimiter` + `RiotRateLimitInterceptor` to respect API quotas.
- Prometheus/Grafana dashboards (see `monitoring/`) to observe JVM metrics, SQL counts, and session timings.

---

## 시스템 개요 (Korean)
BestDuo_BE는 리그 오브 레전드 바텀 듀오 시너지를 분석하는 **bestduo** 서비스의 백엔드입니다. 에메랄드 이상의 솔로 랭크 플레이어를 지속적으로 수집하고, 최신 매치 기록을 동기화한 뒤 패치 단위로 ADC/서포터 조합의 승률·픽률·랭킹·카운터 데이터를 집계합니다. 모든 데이터는 Riot league-v4와 match-v5 API에서 직접 가져오며, 순수 통계 제공 목적만을 위해 사용됩니다.

### 주요 기능
- Riot 요청 예산을 지키는 **Seed → Refresh → Consume 파이프라인**으로 최신 솔로 랭크 데이터를 꾸준히 확보.
- **Match payload 저장**: match-v5 JSON을 가공 전에 먼저 저장해 재처리에 활용.
- **바텀 듀오 추출/집계**: 히스토리 데이터를 바탕으로 시너지 점수, 티어 기반 랭킹, 매치업 카운터를 계산.
- 프론트엔드에서 호출하는 **공개 REST API**(`/bottom-duo/stats`, `/bottom-duo/detail`, `/bottom-duo/counters` 등).
- 세션 실행, 매치 적재, 큐 상태 확인을 위한 **관리 도구**(`/admin/session`, `/collect` 등).

### 처리 흐름
1. `SeedBootstrapRun`이 티어/디비전별 리그 엔트리를 가져와 신규 시드를 등록합니다.
2. `MatchIdsFinder`가 최근 match ID를 조회해 `match_queue`에 적재합니다.
3. `CollectMatchDetailAndSaveRaw`가 match-v5 payload를 불러와 저장하고, 바텀 듀오를 추출하며 참가자를 확장 큐에 넣습니다.
4. 집계기에서 티어별 통계를 계산해 프레젠테이션 계층이 조회하는 테이블에 반영합니다.
5. `ViewBottomDuo*` 유스케이스가 패치 버전/챔피언 메타를 resolve하고, 컨트롤러를 통해 인사이트를 제공합니다.

### 제공 API 예시
- `GET /bottom-duo/stats`: 티어, 패치, 챔피언 필터에 따른 승률/픽률/랭킹 조회.
- `GET /bottom-duo/detail`: 특정 듀오 vs 상대 듀오 매치업 상세 데이터.
- `GET /bottom-duo/counters`: 선택 듀오 기준 최저 승률 카운터 목록.
- `POST /collect/match/{matchId}`: 지정 티어 라벨로 Riot 매치를 수동 적재.
- `POST /admin/session/run`: 예산을 나눠 시드/리프레시/소비 단계를 순차 실행.

### 기술 스택 및 모니터링
- Spring Boot + Gradle 기반 계층형 구조(`application`, `domain`, `infra`, `presentation`).
- 매치, 큐, 집계 테이블을 다루는 JPA 저장소.
- `DualWindowRateLimiter`와 `RiotRateLimitInterceptor`로 Riot API 레이트 리밋 준수.
- `monitoring/` 이하 Prometheus/Grafana 구성으로 JVM, SQL, 세션 타이밍을 관측.
