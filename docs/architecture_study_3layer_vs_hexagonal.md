# 학습 정리: 3-Layer vs Hexagonal — 우리 프로젝트 회고

> 작성일: 2026-04-16
> 목적: Phase 5 "경량 헥사고날" 결정에 대한 회고 + 3-layer로 전환하는 판단 근거를 학습 자료로 정리.

---

## 0. 한 줄 결론

> **현재 우리 프로젝트(1인, ~5,800 LOC, Riot API 데이터 파이프라인)에는 "3-layer + 외부 API 어댑터화"가 정답이다. "경량 헥사고날"이라는 이름은 본질을 흐릴 뿐.**

---

## 1. 우리가 처음에 헥사고날을 선택한 이유 (회고)

### 표면적 이유 (ADR에 적힌 것)
- Riot API 호출부의 테스트 용이성
- 외부 라이브러리 교체 가능성
- 도메인 로직과 인프라 분리

### 진짜 이유 (대화에서 드러난 것)
> "처음에 도메인을 어떻게 짜야할지 몰라서 비즈니스 로직부터 짰다"

**이건 헥사고날을 선택할 이유가 아니다.** 오히려 그 반대.
- 도메인이 불명확할수록 → 추상화(Port/Adapter)는 **추측 기반의 잘못된 경계**를 굳혀버림
- 도메인이 명확해진 다음에 → 필요한 곳만 포트화하는 게 정석

**교훈**: 아키텍처는 "코드를 어떻게 쓸지 모를 때" 도입하는 게 아니라, "도메인이 명확해진 후 그 경계를 보호하기 위해" 도입한다.

---

## 2. 헥사고날의 진짜 가치 (언제 의미 있나?)

### 헥사고날이 빛나는 조건
1. **도메인이 복잡하고 안정적** — 규칙이 자주 안 바뀌고, 비즈니스 로직이 두꺼움
2. **외부 의존성이 자주 교체됨** — DB를 PG↔Mongo로 바꾸거나, 결제 PG를 갈아끼우거나
3. **팀 규모가 크다** — 도메인팀 ↔ 인프라팀이 따로 일함
4. **테스트가 중요한 도메인** — 금융, 의료 등 비즈니스 규칙 테스트가 핵심

### 우리 프로젝트는?
| 조건 | 우리 상황 | 매칭 |
|---|---|---|
| 도메인 복잡도 | 낮음 (Riot API 데이터 ETL) | ❌ |
| 외부 교체 가능성 | DB 안 바꿈, Riot API는 유일한 출처 | ❌ |
| 팀 규모 | 1인 | ❌ |
| 테스트 핵심성 | 통합 테스트가 중요 (단위 테스트 < 통합) | ❌ |

→ **4/4 미스매치**. 헥사고날의 효용을 누릴 수 없는 환경에서 비용만 지불 중.

---

## 3. 우리 "경량 헥사고날"의 모순 (왜 애매한가?)

### ADR vs 실제 코드의 충돌
- **ADR 선언**: "Riot API 호출부만 포트화. JPA·DB 레이어는 그대로 유지"
- **실제 코드**: `ingest` 모듈에 DB 레이어 포트가 7개 존재 (`MatchPersistencePort`, `LeagueEntryPersistencePort` 등)

### 헥사고날 형식만 흉내낸 흔적
- `domain/` 폴더가 대부분의 모듈에서 **거의 비어있음**
- Service가 Port를 부르지만, Port 뒤에는 단순 JPA Repository wrapping만 있음
  → Port 1개당 Adapter 1개만 존재 = **추상화의 가치 0**
- "헥사고날"이라 부르지만 실질은 "Repository에 인터페이스 한 겹 더 씌운 3-layer"

### 핵심 안티패턴
> **"Port는 있는데 Domain이 없다"** = 헥사고날의 형(形)만 있고 실(實)이 없음.

---

## 4. 도메인 모델 분리는 왜 하는가? (헥사고날과 별개의 가치)

### Anemic vs Rich Domain Model (Martin Fowler)

**Anemic (빈혈)**: 데이터만 있는 객체 + Service에 모든 로직
```java
class Order { Long id; BigDecimal total; /* getter/setter만 */ }
class OrderService { void cancel(Order o) { o.setStatus(CANCELED); ... } }
```

**Rich (충만)**: 객체가 자기 책임을 안다
```java
class Order {
    void cancel() {
        requireStatus(PENDING);  // 불변 조건
        this.status = CANCELED;
        this.canceledAt = now();
    }
}
```

### 왜 Rich가 좋은가?
- **불변 조건(invariant)이 한 곳에 모임** — Service 여러 군데에 흩어진 if문 사라짐
- **상태 전이 규칙이 객체 안에 캡슐화** — 잘못된 상태 변경이 컴파일/런타임에 차단됨
- **읽을 때 의도가 명확** — `order.cancel()` vs `service.changeOrderStatus(order, CANCELED)`

### 우리 프로젝트의 좋은 예: `MatchQueue`
이미 Rich Domain Model이다:
```java
public static MatchQueue newReady(...) { ... }   // 정적 팩토리
public void markRunning() { requireStatus(READY); ... }  // 상태 전이
public void markDone()    { requireStatus(RUNNING); ... }
private void requireStatus(QueueStatus expected) {
    if (this.status != expected) throw new IllegalStateTransitionException(...);
}
```
→ **별도 도메인 폴더로 옮길 필요 없다.** JPA 엔티티가 이미 Rich Domain Model 역할을 충분히 하고 있음.

### 핵심 인사이트
> **"도메인 모델 분리"와 "헥사고날 아키텍처"는 별개다.**
> Rich Domain Model은 3-layer에서도, 헥사고날에서도, 어디서든 가능.
> JPA Entity ≠ Anemic Model. JPA Entity에 비즈니스 메서드를 넣으면 Rich Domain Model이 됨.

---

## 5. 3-Layer 아키텍처 정의

### 3개의 층
```
┌─────────────────────────────────────┐
│  Presentation Layer                 │  HTTP/Scheduler/CLI 진입점
│  Controller, Runner, Scheduler       │
└──────────────┬──────────────────────┘
               │ 호출 (위→아래만)
┌──────────────▼──────────────────────┐
│  Application Layer                  │  유스케이스 = 비즈니스 흐름
│  Service / UseCase                   │
└──────────────┬──────────────────────┘
               │ 호출 (위→아래만)
┌──────────────▼──────────────────────┐
│  Infrastructure Layer               │  외부 세계 (DB / API / 파일)
│  Repository, External API Client     │
└─────────────────────────────────────┘
```

### 단 하나의 규칙
**의존성은 위에서 아래로만**. 역방향 금지, 건너뛰기 지양.

### 자주 헷갈리는 것들

| 질문 | 답 |
|---|---|
| `domain/` 폴더는 4번째 레이어인가? | **아니다.** 모든 층이 공유하는 "공유 커널(Shared Kernel)" |
| Application에 Port 인터페이스 두면 헥사고날인가? | **아니다.** 그냥 DIP(의존성 역전) 패턴. 3-layer에서도 흔함 |
| Controller가 Repository 직접 호출해도 되나? | **안 된다.** 비즈니스 로직이 Controller에 새어나감 |
| JPA Entity가 Domain Model이어도 되나? | **된다.** 비즈니스 메서드 넣으면 Rich Domain Model |

---

## 6. 우리 프로젝트의 정답: "3-Layer + 외부 API 어댑터화"

### 채택 이유
- ✅ 1인 프로젝트 — 추상화 비용 회수 불가
- ✅ 5,800 LOC 규모 — 폴더 구조가 단순할수록 유지보수 ↑
- ✅ DB 안 바꿈 — JPA Repository 그대로 사용
- ✅ Riot API는 외부 의존성 + 테스트 어려움 → **여기만 Port로 보호**
- ✅ JPA Entity가 이미 Rich Domain Model 역할 수행 중

### Port/Adapter 보존 대상 (단 2개)
1. `RiotApiPort` — 외부 HTTP 호출, 테스트 격리 필요
2. `ChampionMetaClient` — Data Dragon API, 외부 의존성

### 나머지는 모두 제거
- DB Persistence Port 7개 → 삭제, JPA Repository 직접 사용
- 빈 `domain/` 폴더들 → `common/domain/`으로 통합

---

## 7. 의사결정 체크리스트 (다음번 아키텍처 선택 시)

새 모듈/기능을 만들 때 자문할 질문:

```
□ 이 기능의 도메인이 이미 명확한가?
   YES → 명확한 경계대로 분리
   NO  → 일단 단순하게 구현 후 리팩토링

□ 외부 의존성을 정말 교체할 가능성이 있는가?
   YES → Port/Adapter 도입
   NO  → 직접 구현체 사용

□ 1인이 1년 안에 다시 봐도 이해되는가?
   YES → 추상화 OK
   NO  → 추상화 제거

□ "이 패턴을 왜 썼지?"라는 질문에 1문장으로 답할 수 있는가?
   YES → 채택
   NO  → 패턴 빼고 직접 코드로
```

---

## 8. 면접/포트폴리오용 한 줄 정리

> **"초기에 도메인이 불확실해 헥사고날을 선택했지만, 1인 프로젝트 규모와 실제 도메인의 단순성을 재평가한 결과, 3-layer + 외부 API 어댑터화로 전환했습니다. 추상화는 필요한 곳(Riot API)에만 적용하고, JPA Entity가 Rich Domain Model 역할을 하도록 설계했습니다."**

핵심 키워드:
- 의사결정 회고 (ADR을 다시 검토할 줄 안다)
- YAGNI 적용 (불필요한 추상화 제거)
- Rich Domain Model 이해
- 의존성 역전(DIP) ≠ 헥사고날 구분

---

## 9. 참고 개념

| 개념 | 한 줄 설명 |
|---|---|
| **3-Layer** | Presentation → Application → Infrastructure 단방향 의존 |
| **Hexagonal** | 도메인을 중심에 두고 Port/Adapter로 외부 격리 |
| **Clean Architecture** | 헥사고날 + 의존성 규칙 강화 (4개 동심원) |
| **DDD (전술)** | Aggregate, Entity, Value Object, Domain Event |
| **DIP (Dependency Inversion)** | 구체 → 추상에 의존. Port/Adapter의 핵심 원리 |
| **Rich Domain Model** | 데이터 + 행위를 함께 갖는 객체 |
| **Anemic Model** | 데이터만 있고 로직은 Service에 있는 객체 (안티패턴) |
| **Shared Kernel** | 여러 모듈/레이어가 공유하는 핵심 타입 모음 |
| **YAGNI** | You Aren't Gonna Need It — 추측 기반 추상화 금지 |

---

## 10. 다음 단계

→ `docs/refactoring_to_3layer_plan.md` 참조 (구체적 구현 계획)
