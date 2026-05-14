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

## 3. 의존성 원칙

- `controller`는 `service`와 `dto`에 의존한다.
- `service`는 `repository`, `entity`, 외부 연동용 포트 또는 인터페이스에 의존한다.
- `entity`는 프레임워크 상세 구현이나 외부 시스템 클라이언트에 의존하지 않는다.
- 엔티티를 API 응답으로 직접 노출하지 않는다.
- 영속성 예외나 외부 시스템 예외를 그대로 상위 계층으로 전파하지 않는다.
- Spring Bean의 의존성 주입은 일관되게 생성자 주입으로 하며 이외에 주입 방식은 하지 않는다. 
- 직접 생성자를 작성하지 않고 Lombok `@RequiredArgsConstructor`를 사용한다.

---

## 4. 엔티티 및 영속성 원칙

### 4.1 공통 엔티티 규칙

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

### 4.2 Repository 원칙

- 단순 CRUD는 Spring Data JPA Repository를 사용한다.
- 복잡한 조회는 Querydsl 또는 명시적 커스텀 repository 도입을 검토한다.
- 조회 최적화가 필요한 경우 반환 타입을 목적에 맞는 DTO 또는 projection으로 제한한다.

---

## 5. 트랜잭션 원칙

- DB 트랜잭션은 서비스 계층에서 시작한다.
- 조회 전용 유스케이스는 `@Transactional(readOnly = true)`를 사용한다.
- 상태 변경 유스케이스는 서비스 메서드 단위로 트랜잭션을 둔다.
- 외부 API 호출과 DB 트랜잭션을 불필요하게 길게 묶지 않는다.
- Redis, Kafka와 DB의 원자성이 필요한 경우 보상 처리 또는 Outbox 패턴을 검토한다.

---

## 6. 외부 시스템 연동 원칙

- 재시도 가능 여부를 외부 시스템별로 명시한다.
- 결제처럼 중복 실행 위험이 있는 연동은 멱등성 키를 고려한다.
- 타임아웃, 실패, 중복 처리 시나리오를 정상 흐름과 동일한 수준으로 설계한다.
- 비동기 이벤트 소비 로직은 중복 실행에도 결과가 깨지지 않도록 멱등하게 작성한다.

---

## 7. 운영 추적 원칙

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

## 8. 구현 체크리스트

- 새 패키지가 도메인 중심 구조를 따르는가?
- 도메인 규칙이 controller가 아니라 service 또는 entity에 위치하는가?
- 엔티티 상태 변경이 메서드로 표현되는가?
- 외부 시스템 연동 코드가 `infra`에 격리되어 있는가?
- API 응답이 `docs/api-contract.md` 규칙을 따르는가?
- 동시성, 재시도, 중복 실행 시나리오를 함께 고려했는가?
- 문서와 코드가 충돌하지 않는가?
