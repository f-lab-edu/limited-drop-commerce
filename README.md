## 프로젝트 소개

고동시성 환경에서 **선착순 주문**, **재고 선점**, **결제 만료 복구**를 처리하는 이벤트 기반 한정 판매 커머스 백엔드 시스템입니다.

특정 시간에 오픈되는 한정 수량 상품을 드롭(Drop) 방식으로 판매하며, 대량 트래픽 속에서도 정합성 있는 재고 관리와 주문 처리를 목표로 합니다.

---

## 프로젝트 목표


---

## 핵심 기능

- 특정 시간에 판매 시작되는 한정 드롭 이벤트
- 선착순 재고 선점 (동시성 처리)
- 주문 상태 기반 흐름 관리 (예약 → 주문 → 결제 → 완료)
- 미결제 주문 만료 및 재고 자동 복구
- 이벤트 기반 비동기 처리 (Kafka)
- 분산 환경 동시성 제어 (Redis 분산 락)

---

## 기술 스택

| 분류 | 기술 |
| --- | --- |
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

---

## 시스템 아키텍처

```
Client
   ↓
Nginx
   ↓
Spring Boot API
  ├ EventSale Service
  ├ Inventory Service
  ├ Order Service
  ├ Payment Service
  └ Notification Service
   ↓
Database
  ├ 주문 데이터
  ├ 재고 데이터
  └ 결제 데이터

Redis
  ├ 재고 선점
  ├ 분산 락
  └ 임시 상태 관리

Kafka
  ├ OrderCreated Event
  ├ PaymentCompleted Event
  └ OrderExpired Event
```

---

## 패키지 구조

도메인 중심 구조를 채택합니다.

> [패키지 구조.md](docs/패키지구조.md)

---

## 도메인 관계

```
user + product → reservation → order → payment
```

| 도메인 | 역할 |
| --- | --- |
| user | 회원 가입 / 인증 |
| product | 상품 정보, 재고 관리 |
| reservation | 한정 수량 선점 / 예약 |
| order | 주문 확정, 상태 관리 |
| payment | PG 연동, 결제 / 취소 / 환불 |

---

## 로컬 실행 방법

**사전 요구사항**

- Docker & Docker Compose
- Java 21

**실행**

```bash
# 인프라 실행 (MySQL)
docker-compose up -d mysql

# 애플리케이션 실행
./gradlew bootRun
```

**테스트**

```bash
./gradlew test
```

> 테스트는 Testcontainers를 사용하여 별도의 DB 설정 없이 실행됩니다.


---

## 브랜치 전략 / 커밋 컨벤션

GitHub Flow를 사용합니다.

> [브랜치 전략/ 커밋 컨벤션 .md](docs/브랜치전략.md)
