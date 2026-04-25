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

## 범위

이 문서는 프로젝트의 빠른 이해를 위한 입문 문서다.
도메인 흐름, 상태 전이, API 세부 계약, 테이블 구조 같은 상세 내용은 각 전용 문서에서 관리한다.
