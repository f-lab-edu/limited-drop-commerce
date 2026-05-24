# Architecture — Limited Drop Commerce

> 이 문서는 `limited-drop-commerce` 백엔드의 아키텍처 기준 문서다.

구현 코드는 이 문서의 패키지 경계, 계층 책임, 의존성 방향, 트랜잭션 원칙을 따른다.
API 세부 계약은 `docs/api-contract.md`, 유스케이스 흐름은 `docs/use-cases.md`, 데이터 모델은 `docs/erd.md`에서 관리한다.

---

## 1. 목적

- 도메인 중심 패키지 구조를 정의한다.
- 계층별 책임과 의존성 방향을 명확히 한다.
- 트랜잭션, 외부 연동, 관측성에 대한 기본 구현 원칙을 정의한다.
- TDD 원칙을 따른다. 테스트 코드를 기반으로 구현을 진행한다.
- 테스트 코드 작성 -> 구현 -> 리팩토링의 사이클을 반복한다.

---

## 2. 패키지 구조

### 2.1 Domain Package

도메인 패키지는 비즈니스 기능 단위로 구성한다.

| 레이어 | 책임 |
|---|---|
| `controller` | HTTP 요청/응답 변환, 인증 주체 식별자 전달, 입력 검증 시작점 |
| `service` | 유스케이스 조합, 트랜잭션 경계 설정, 도메인 규칙 실행 |
| `repository` | 영속성 접근, JPA Repository 또는 커스텀 조회 구현 |
| `entity` | 도메인 상태, 상태 전이, 핵심 비즈니스 규칙 보유 |
| `dto` | API 요청/응답 모델 정의 |
| `exception` | 도메인 전용 비즈니스 예외 정의 |

### 2.2 Global Package

`global` 패키지는 특정 도메인에 속하지 않는 횡단 관심사를 담당한다.

| 패키지 | 책임 |
|---|---|
| `global.config` | Security, JPA Auditing, Web MVC 등 공통 설정 |
| `global.entity` | 공통 엔티티 기반 클래스 |
| `global.exception` | 전역 예외 처리, 공통 예외 기반 |
| `global.response` | 공통 API 응답 포맷 |
| `global.filter` | Servlet Filter 기반 요청 전처리 |
| `global.interceptor` | Spring MVC Interceptor 기반 요청 처리 |
| `global.util` | 범용 유틸리티 |

### 2.3 Infra Package

`infra` 패키지는 외부 시스템 연동 구현을 격리한다.

예시:

- PG 결제 승인/취소 API 클라이언트
- Kafka producer/consumer
- Redis client adapter
- 알림, 파일 스토리지 등 외부 의존성

도메인 서비스는 외부 시스템의 구체 구현에 직접 의존하지 않는다.
필요한 경우 애플리케이션 경계에 인터페이스를 두고 `infra`에서 구현한다.

---

## 3. 도메인 모델 원칙 (DDD)

도메인 모델은 단순 데이터 컨테이너가 아니라 비즈니스 규칙의 1차적 표현 위치다.
아래 원칙은 본 프로젝트의 도메인 패키지(`controller`/`service`/`entity`)와 함께 적용된다.

### 3.1 Aggregate Root와 경계

- 비즈니스 일관성을 동시에 유지해야 하는 엔티티 묶음을 하나의 Aggregate로 식별한다.
- 외부에서는 Aggregate Root를 통해서만 내부 엔티티에 접근한다. 내부 엔티티를 직접 노출하지 않는다.
- 상태 변경은 Aggregate Root의 도메인 메서드를 통해서만 수행하며, 내부 엔티티의 setter를 외부에서 호출하지 않는다.
- 하나의 트랜잭션에서는 하나의 Aggregate만 변경하는 것을 원칙으로 한다. 여러 Aggregate를 동시에 변경해야 하면 도메인 이벤트와 결과 일관성(Eventual Consistency)을 검토한다.
- Repository는 Aggregate Root 단위로만 정의한다. 내부 엔티티 전용 Repository를 두지 않는다.

### 3.2 Aggregate 간 ID 참조 원칙

- 다른 Aggregate를 참조할 때는 객체 참조(`@ManyToOne Brand brand`) 대신 ID 값(`Long brandId`)을 사용한다.
- 같은 Aggregate 내부 엔티티 사이에서는 객체 참조를 사용해도 된다.
- ID 참조는 Aggregate 경계를 강제하고, 의도치 않은 cascade와 N+1 문제를 사전에 차단한다.
- 다른 Aggregate의 데이터가 함께 필요한 조회는 별도 Repository 호출이나 조회 전용 DTO/Projection으로 처리한다 (5.3 참고).

### 3.3 Value Object

- 식별자 없이 속성으로만 동등성이 결정되는 개념(`Money`, `Address`, `Period`, `Email` 등)은 Value Object로 표현한다.
- JPA에서는 `@Embeddable`과 `@Embedded`로 표현하고 별도 테이블로 분리하지 않는다.
- Value Object는 **불변(immutable)** 으로 설계한다. 변경이 필요하면 새로운 인스턴스를 반환하는 메서드를 둔다.
- 검증 로직은 생성 시점(생성자 또는 정적 팩토리)에 두어, 잘못된 상태의 인스턴스가 만들어지지 않게 한다.

### 3.4 도메인 서비스와 Application Service

- 어느 한 Entity에 자연스럽게 속하지 않으면서 도메인 규칙에 해당하는 로직(예: 두 Aggregate 간의 정책 판단)은 **도메인 서비스**에 둔다. 도메인 서비스는 도메인 패키지 안에 둔다.
- `service` 패키지의 클래스(Application Service)는 트랜잭션 경계와 유스케이스 조합(입출력 변환, 권한 확인, 다른 서비스 호출 순서 결정)을 담당한다. 비즈니스 규칙 자체를 Application Service에서 직접 구현하지 않는다.
- 도메인 규칙이 controller나 Application Service로 새어 나가지 않는지 코드 리뷰에서 확인한다.

### 3.5 도메인 이벤트

- 상태 변경의 결과로 발생하는 부수 효과(외부 시스템 호출, 다른 Aggregate 갱신, 알림, Kafka 발행 등)는 **도메인 이벤트**로 표현한다.
- 도메인 이벤트는 Aggregate Root에서 등록하고, Application Service의 트랜잭션 커밋 시점에 `ApplicationEventPublisher`를 통해 발행한다.
- 이벤트 핸들러는 멱등하게 작성한다. 같은 이벤트가 재처리되어도 결과가 깨지지 않아야 한다 (7장 외부 시스템 연동 원칙과 일관됨).

---

## 4. 의존성 원칙

- `controller`는 `service`와 `dto`에 의존한다.
- `service`는 `repository`, `entity`, 외부 연동용 포트 또는 인터페이스에 의존한다.
- `entity`는 프레임워크 상세 구현이나 외부 시스템 클라이언트에 의존하지 않는다.
- 엔티티를 API 응답으로 직접 노출하지 않는다.
- 영속성 예외나 외부 시스템 예외를 그대로 상위 계층으로 전파하지 않는다.
- Spring Bean의 의존성 주입은 일관되게 생성자 주입으로 하며 이외에 주입 방식은 하지 않는다. 
- 직접 생성자를 작성하지 않고 Lombok `@RequiredArgsConstructor`를 사용한다.

---

## 5. 엔티티 및 영속성 원칙

### 5.1 공통 엔티티 규칙

- 모든 엔티티는 가능한 경우 `BaseTimeEntity`를 상속한다.
- 기본 생성자는 `@NoArgsConstructor(access = AccessLevel.PROTECTED)`를 사용한다.
- 엔티티 생성은 정적 팩토리 메서드 또는 의미 있는 생성 메서드를 우선한다.
- 상태 변경은 setter 대신 도메인 메서드로 표현한다.

예시:

```java
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Reservation extends BaseTimeEntity {

    public void confirm() {
        validateConfirmable();
        this.status = ReservationStatus.CONFIRMED;
    }
}
```

### 5.2 외래 키 제약 어노테이션 금지

- `@ManyToOne`, `@OneToOne`, `@OneToMany` 등 연관 관계를 선언할 때 `@JoinColumn`의 `foreignKey = @ForeignKey(name = "...")` 옵션을 사용하지 않는다.
- 운영 스키마는 별도 마이그레이션 도구(Flyway 등)로 관리하며, 외래 키 제약과 인덱스는 마이그레이션 SQL에서 명명한다. 어노테이션과 마이그레이션 양쪽에 스키마 정보를 두면 출처가 분산되어 일관성이 깨진다.
- JPA의 DDL 자동 생성은 로컬 개발/테스트 편의용이며, 운영 스키마의 권위(authoritative source)는 마이그레이션 SQL이다.

권장:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "company_id", nullable = false)
private Long companyId; // ID 참조(3.2)
```

지양:

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(
    name = "company_id",
    nullable = false,
    foreignKey = @ForeignKey(name = "fk_brand_company_id") // 사용하지 않는다
)
private Company company;
```

### 5.3 Repository 원칙과 N+1 방지

- 단순 CRUD는 Spring Data JPA Repository를 사용한다.
- 복잡한 조회는 Querydsl 또는 명시적 커스텀 repository 도입을 검토한다.
- 조회 최적화가 필요한 경우 반환 타입을 목적에 맞는 DTO 또는 projection으로 제한한다.

N+1 문제는 사후 튜닝이 아니라 작성 시점에 차단한다.

- **모든 `@ManyToOne`, `@OneToOne` 연관은 명시적으로 `fetch = FetchType.LAZY`를 지정한다.** JPA 기본값(`EAGER`)에 의존하지 않는다.
- 연관 엔티티가 함께 필요한 조회는 다음 중 한 가지로 **즉시 로딩을 명시**한다.
  - JPQL `join fetch`
  - `@EntityGraph(attributePaths = {...})`
- 목록 조회는 가급적 엔티티 대신 조회 전용 DTO/Projection을 반환한다. 화면에 필요한 필드만 select하여 연관 그래프 자체를 로딩하지 않게 한다.
- 1:N(컬렉션) 연관을 한 번의 fetch join으로 묶으면 페이징이 깨지고 cartesian product가 발생한다. 대신 다음을 사용한다.
  - 컬렉션 필드에 `@BatchSize(size = N)` 적용
  - 또는 전역 설정 `spring.jpa.properties.hibernate.default_batch_fetch_size`로 IN 쿼리 단위 배치 페치
- 새로 추가한 조회 경로는 테스트에서 실제 발생하는 SQL 개수를 확인한다(예: Hibernate Statistics, datasource-proxy 등). 통합 테스트에서 N+1을 회귀로 감지할 수 있도록 한다.

---

## 6. 트랜잭션 원칙

- DB 트랜잭션은 서비스 계층에서 시작한다.
- 조회 전용 유스케이스는 `@Transactional(readOnly = true)`를 사용한다.
- 상태 변경 유스케이스는 서비스 메서드 단위로 트랜잭션을 둔다.
- 외부 API 호출과 DB 트랜잭션을 불필요하게 길게 묶지 않는다.
- Redis, Kafka와 DB의 원자성이 필요한 경우 보상 처리 또는 Outbox 패턴을 검토한다.

---

## 7. 외부 시스템 연동 원칙

- 재시도 가능 여부를 외부 시스템별로 명시한다.
- 결제처럼 중복 실행 위험이 있는 연동은 멱등성 키를 고려한다.
- 타임아웃, 실패, 중복 처리 시나리오를 정상 흐름과 동일한 수준으로 설계한다.
- 비동기 이벤트 소비 로직은 중복 실행에도 결과가 깨지지 않도록 멱등하게 작성한다.

---

## 8. 운영 추적 원칙

운영 추적을 위해 다음 정보를 남긴다.

| 대상 | 내용 |
|---|---|
| Request log | HTTP method, path, status, latency, requestId |
| Business log | reservationId, orderId, paymentId, userId |
| Error log | exception type, error code, requestId |
| Metric | 예약 성공/실패 수, 재고 소진 수, 결제 성공/실패 수 |

`global.filter.MdcLoggingFilter`는 요청 단위 MDC 값을 설정하는 진입점으로 사용한다.

- 비밀번호, 카드번호, 토큰 원문 등 민감 정보는 로그에 남기지 않는다.
- 요청과 비즈니스 이벤트를 연결할 수 있는 식별자를 우선 기록한다.

---

## 9. 구현 체크리스트

- 새 패키지가 도메인 중심 구조를 따르는가?
- 도메인 규칙이 controller가 아니라 service 또는 entity에 위치하는가?
- Aggregate Root를 통해서만 내부 엔티티 상태가 변경되는가? Aggregate 간 참조가 ID로 표현되었는가?
- Value Object가 불변으로 설계되었는가?
- 부수 효과가 도메인 이벤트로 표현되고 트랜잭션 경계와 정렬되었는가?
- 엔티티 상태 변경이 메서드로 표현되는가?
- `@ManyToOne`/`@OneToOne` 연관에 `fetch = LAZY`가 명시되었는가?
- 연관 로딩이 fetch join, `@EntityGraph`, `@BatchSize` 중 적절한 수단으로 제어되는가? 신규 조회 경로의 SQL 개수를 테스트에서 확인했는가?
- `@JoinColumn`에 `foreignKey = @ForeignKey(...)`를 사용하지 않았는가? (외래 키 제약은 마이그레이션 SQL에서만 관리)
- 외부 시스템 연동 코드가 `infra`에 격리되어 있는가?
- API 응답이 `docs/api-contract.md` 규칙을 따르는가?
- 동시성, 재시도, 중복 실행 시나리오를 함께 고려했는가?
- 문서와 코드가 충돌하지 않는가?
