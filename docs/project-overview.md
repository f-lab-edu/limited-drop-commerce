# Project: limited-drop-commerce

## 프로젝트 개요

`limited-drop-commerce`는 한정 수량 상품을 드롭 방식으로 판매하는 백엔드 시스템이다.
이 프로젝트는 고동시성 환경에서도 재고 초과 판매를 방지하고, 결제 전 선점된 재고를 안정적으로 관리하며, 만료 또는 취소 시 자동 복구하는 것을 목표로 한다.

핵심 관심사는 다음과 같다.

- 높은 동시 요청 상황에서 정합성 있는 재고 관리
- 선착순 재고 선점과 결제 유효 시간 관리
- 주문, 결제, 만료, 복구의 일관된 상태 전이
- Redis, Kafka 등 외부 인프라를 활용한 이벤트 기반 처리

## 기술 스택

| 구분 | 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0 |
| ORM | Spring Data JPA, Hibernate 7 |
| Database | MySQL 8.0 |
| Cache / Lock | Redis |
| Message Queue | Kafka |
| Security | Spring Security |
| Test | JUnit 5, Testcontainers |
| Infra | Docker, Docker Compose |
| Build | Gradle |

## 문서 안내

세부 내용은 아래 문서를 기준으로 확인한다.

| 문서 | 역할 |
|---|---|
| `docs/architecture.md` | 패키지 구조, 계층 책임, 트랜잭션 경계, 구현 원칙 |
| `docs/api-contract.md` | API URL 규칙, 요청/응답 포맷, 오류 코드, 직렬화 규칙 |
| `docs/use-cases.md` | 액터별 유스케이스, 기본 흐름, 예외 흐름, 사후 조건 |
| `docs/erd.md` | 테이블 구조, 관계, 제약 조건, 데이터 모델 설명 |
| `docs/branch-rules.md` | 브랜치 전략, 커밋 메시지 규칙, PR 규칙 |
| `docs/development-workflow.md` | 작업 완료 기준, TDD/검증 흐름 |
| `docs/tooling-guidelines.md` | 개발 도구 사용 지침 |

## 범위

이 문서는 프로젝트의 빠른 이해를 위한 입문 문서다.
도메인 흐름, 상태 전이, API 세부 계약, 테이블 구조 같은 상세 내용은 각 전용 문서에서 관리한다.


## 패키지

### 배치 규칙

port(인터페이스)는 그것을 필요로 하는 도메인에 두고, adapter(구현체)는 `infra/`에 둔다. 도메인이 인프라에 의존하지 않게 하기 위한 것이다.

| 대상 | 위치 |
|---|---|
| port (인터페이스와 그 입출력 타입) | 해당 도메인 하위 (`domain/payment/gateway/`, `domain/reservation/repository/`) |
| 여러 도메인이 공유하는 기술 adapter | 루트 `infra/` (Redis, Kafka) |
| 특정 도메인 전용 외부 연동 | 해당 도메인 하위 `infra/` (`domain/payment/infra/toss/`) |

`domain/payment/infra/toss/`는 Toss 연동이 결제에서만 쓰이기 때문에 둔 예외다. Redis와 Kafka는 여러 도메인이 공유하므로 루트 `infra/`에 모은다.

```aiignore
com.mist.commerce
├── CommerceApplication.java
│
├── global/                       공통 웹/설정/예외 기반
│   ├── config/
│   ├── entity/
│   ├── exception/
│   ├── filter/
│   ├── interceptor/
│   ├── response/
│   ├── util/
│   └── web/
│
├── common/                       도메인 간 공유 기능
│   ├── idempotency/
│   │   ├── exception/
│   │   ├── model/
│   │   ├── port/                 IdempotencyStore (port)
│   │   └── web/                  IdempotencyInterceptor, RequestResolver
│   └── json/
│
├── infra/                        공유 기술 adapter
│   ├── kafka/
│   │   ├── KafkaConfig.java
│   │   └── KafkaPaymentEventPublisher.java
│   └── redis/
│       ├── RedisKeyExpirationConfig.java
│       ├── RedisScriptLoader.java
│       ├── idempotency/          IdempotencyStore 구현
│       └── reservation/          OptionStockStore, ReservationExpiryStore 구현
│
└── domain/
    ├── reservation/
    │   ├── application/          service, listener, idempotency, support
    │   ├── dto/
    │   ├── entity/
    │   ├── exception/
    │   ├── presentation/
    │   ├── repository/           OptionStockStore, ReservationExpiryStore (port)
    │   └── scheduler/
    │
    ├── order/
    │   ├── controller/
    │   ├── dto/
    │   ├── entity/
    │   ├── exception/
    │   ├── repository/
    │   └── service/
    │
    └── payment/
        ├── application/          listener, idempotency, support
        ├── dto/
        ├── entity/
        ├── exception/
        ├── gateway/              PaymentGateway, PaymentEventPublisher (port)
        ├── infra/                결제 전용 외부 연동
        │   └── toss/             TossPaymentClient (PaymentGateway 구현)
        ├── presentation/
        ├── repository/
        └── service/
```

`user`, `product`, `brand`, `event`, `company` 도메인은 `controller / service / repository / entity / dto / exception` 기본 구성을 따른다.
