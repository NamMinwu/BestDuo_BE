# ADR-001: Riot API 경계 포트화 — 경량 헥사고날 채택

> 작성일: 2026-04-15
> **상태: Superseded by [ADR-002 — 3-Layer 전환](./adr_3layer_transition.md) (2026-04-16)**
> 대상 Phase: Phase 5 (Riot API Port 경량 추출)

> ⚠️ **이 결정은 ADR-002에 의해 대체되었습니다.**
> Phase 5에서 채택한 "경량 헥사고날"은 이후 Port 인터페이스가 14개로 점진 확장되며
> DB-layer까지 이중 추상화하는 부작용을 낳았습니다. 2026-04-16 재평가에서 3-Layer 구조로
> 단순화하는 [ADR-002](./adr_3layer_transition.md)를 채택했고, 본 ADR은 역사적 기록으로 유지합니다.
> Port 보존 결정은 `RiotApiPort` 1개 → 2개 (`+ChampionMetaClient`)로 확장되었습니다.

---

## 1. 결정 요약

Riot API 호출 경계에 대해 **경량 헥사고날(Lightweight Hexagonal)** 방식을 채택한다.

- Riot API 호출부만 `RiotApiPort` 인터페이스로 포트화한다.
- JPA 엔티티와 DB 레이어는 헥사고날 경계를 두지 않는다 (이중화 회피).
- 전면(Full) 헥사고날 아키텍처는 현재 규모에서 오버엔지니어링으로 판단해 채택하지 않는다.

---

## 2. 배경 및 문제 인식

### 2-1. 현재 구조의 문제

현재 Riot API 호출은 여러 애플리케이션 서비스에서 직접 이루어진다.

```
CollectMatchIdsRunner      → matchIdsFinder (실제 HTTP 구현에 직접 의존)
DailyLeagueEntriesRunner  → leagueEntryFetcher (실제 HTTP 구현에 직접 의존)
IngestMatchDetail          → 직접 HTTP 클라이언트 사용
```

이 구조에서 발생하는 문제:

1. **단위 테스트 불가**: 실제 Riot API 를 사용하는 통합 테스트만 가능하거나, 테스트마다 Mockito stub을 길게 작성해야 한다.
2. **관심사 분리 없음**: 비즈니스 로직(언제, 어떤 데이터를 가져올지)과 전송 로직(HTTP 헤더, 재시도, rate limit)이 혼재한다.
3. **교체 비용**: Riot API 클라이언트 라이브러리나 HTTP 구현을 바꾸면 서비스 레이어까지 수정해야 한다.

---

## 3. 검토한 아키텍처 옵션

### Option A — 현행 유지 (변경 없음)

**개요**: 현재 구조 그대로 유지.

**장점**
- 즉각적인 작업 불필요
- 코드 변경 리스크 없음

**단점**
- 위 2-1의 문제가 그대로 남음
- 테스트 속도와 격리성 개선 불가
- 기능 추가 시 같은 패턴의 결함이 반복됨

**결론**: 기술 부채를 방치하는 선택. 중장기적으로 유지보수 비용 증가.

---

### Option B — 전면 헥사고날 (Full Hexagonal Architecture)

**개요**: DDD+헥사고날 아키텍처를 전면 적용. 도메인 레이어, 애플리케이션 레이어, 포트/어댑터를 엄격히 분리. JPA 엔티티도 별도의 도메인 모델로 이중화.

```
domain/
  model/                # 순수 도메인 객체 (JPA 무관)
  port/in/              # UseCase 인터페이스
  port/out/             # Repository, Riot API 포트

application/
  service/              # UseCase 구현

adapter/
  in/web/               # REST 컨트롤러
  out/persistence/      # JPA 어댑터 (도메인 → 엔티티 매핑)
  out/riot/             # RiotApiHttpAdapter
```

**장점**
- 레이어 간 의존성 완벽 제어
- 도메인 모델이 프레임워크에 완전히 독립
- 교과서적 아키텍처 — 대규모 팀·복잡 도메인에 적합

**단점**
- **현재 규모(119 파일 / 5,767 LOC)에서 오버엔지니어링**: 추가 파일 수십 개, 매핑 코드 수백 줄 발생
- JPA 엔티티 ↔ 도메인 모델 이중화: 매핑 계층 도입 → 실질적 도메인 복잡도 변화 없이 코드량만 증가
- Repository 포트화: 테스트는 Testcontainers로 충분히 격리 가능 → 추가 레이어 가치 낮음
- 도입 비용 대비 이점이 현재 문제를 해결하는 데 과도함

**결론**: 팀이 여럿이고 도메인이 복잡하거나 다중 스토리지를 사용해야 할 때 적합. 현재 단계에서는 채택하지 않음.

---

### Option C — 경량 헥사고날 (Lightweight Hexagonal) ← **채택**

**개요**: "외부 I/O 경계만 포트화"하는 절충안. Riot API 호출부만 인터페이스로 추상화하고, JPA·DB 레이어는 그대로 유지한다.

```
application/port/
  MatchIdsFinder        # (기존) — 이미 인터페이스로 분리됨
  RiotApiPort           # (신규) — Riot API 통합 포트

adapter/out/riot/
  RiotApiHttpAdapter    # 실제 HTTP 구현체

test/
  FakeRiotApiAdapter    # 테스트용 인메모리 구현체
```

**장점**
- 가장 고통이 큰 경계(외부 HTTP API)만 추상화 → 90%의 테스트 편의성 이득
- JPA 이중화 없음 → 기존 코드 구조 유지, 불필요한 매핑 코드 없음
- 파일 증가 최소 (2~3개)
- `FakeRiotApiAdapter`로 테스트 시 실제 HTTP 없이 비즈니스 로직만 검증 가능
- 전면 헥사고날 대비 도입 비용이 1/5 이하

**단점**
- DB 레이어는 여전히 JPA 직접 의존 → Repository 교체 유연성 없음
- 완전한 포트-어댑터 패턴은 아님

**결론**: 현재 프로젝트의 규모와 문제에 가장 적합한 균형점. 채택.

---

## 4. 결정 이유 (Why Phase 5 = Option C)

### 4-1. 현재 가장 큰 고통은 "외부 API 테스트 격리"

프로젝트에서 실제로 겪는 문제는 Riot API 의존성이다:
- Riot API가 없으면 테스트를 실행할 수 없다 (또는 Mockito stub이 매우 길어진다)
- Rate limit·429·네트워크 오류를 테스트에서 재현하기 어렵다

Option C는 이 문제를 `FakeRiotApiAdapter` 하나로 해결한다.

### 4-2. JPA 포트화의 ROI가 낮다

PostgreSQL은 바뀔 가능성이 낮고, Testcontainers로 실제 DB를 사용한 통합 테스트가 이미 가능하다. Repository 포트화는 추가 매핑 코드를 요구하지만 그에 비례하는 이득이 없다.

### 4-3. 프로젝트 규모가 전면 헥사고날을 정당화하지 않는다

| 지표 | bestduo_BE_dev | 전면 헥사고날 권장 기준 |
|------|---------------|------------------------|
| 파일 수 | ~119 | 300+ |
| LOC | ~5,767 | 20,000+ |
| 팀 규모 | 1인 | 3인 이상 |
| 도메인 복잡도 | 낮음 (파이프라인 + 집계) | 높음 (복잡한 비즈니스 룰) |

### 4-4. 점진적 확장이 가능하다

경량 헥사고날은 전면 헥사고날로 나아가는 디딤돌이다. Phase 5에서 `RiotApiPort`를 도입하면, 이후 DB 레이어 포트화가 필요해질 때 같은 패턴을 그대로 적용할 수 있다. 지금 전부 할 이유가 없다.

---

## 5. Phase 5 구현 스코프

이 결정에 따라 Phase 5에서 다음을 구현한다.

```
[신규]
src/main/java/com/bestduo_BE/
  common/application/port/RiotApiPort.java        # 포트 인터페이스
  common/infra/riot/RiotApiHttpAdapter.java       # HTTP 구현체 (기존 코드 이동)

src/test/java/com/bestduo_BE/
  common/infra/riot/FakeRiotApiAdapter.java       # 테스트용 Fake 구현체
```

`RiotApiPort` 인터페이스:
```java
public interface RiotApiPort {
    List<String> fetchMatchIdsSince(String puuid, long startEpoch, int count);
    List<String> fetchMatchIdsBetween(String puuid, long startEpoch, long endEpoch, int count);
    List<String> fetchRecentMatchIds(String puuid, int count);
    Match fetchMatchDetail(String matchId);
    List<LeagueEntry> fetchLeagueEntries(Tier tier, Division div, int page);
}
```

**변경되지 않는 것**:
- JPA 엔티티 구조
- Repository 레이어
- DB 스키마

---

## 6. 이후 방향

Phase 5 이후, 팀·규모·요구사항이 바뀌면 아래를 검토할 수 있다:

| 상황 | 검토 항목 |
|------|-----------|
| DB 교체 필요성 대두 | Repository 포트화 (Option B 부분 적용) |
| 도메인 복잡도 급증 | 도메인 모델 분리 (DDD Aggregate 도입) |
| 팀 3인 이상으로 확장 | 전면 헥사고날 전환 재검토 |

---

## 7. 참고

- `docs/refactoring_architecture_plan.md` — 종합 리팩토링 계획 (Section 0, G, Phase 5)
- 관련 PR: Phase 5 구현 PR (작성 예정)
