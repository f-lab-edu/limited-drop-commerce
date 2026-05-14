# API 계약 명세 / API Contract

> 이 문서는 `limited-drop-commerce` 프로젝트의 공식 API 계약 기준 문서다.

사람 개발자와 AI 코드 생성 도구 모두 이 문서를 기준으로 구현한다.

---

## 1. 목적 / Purpose

- 이 문서는 URL 규칙, 요청/응답 형식, 오류 코드, 직렬화 규칙을 정의한다.
- 새로운 API를 추가하거나 수정할 때 이 문서와 충돌하면 코드 또는 문서를 함께 갱신해야 한다.

---

## 2. API Prefix 규칙 / API Prefix Rules

### 2.1 정의된 Prefix / Defined Prefixes

| Prefix | 설명 |
|---|---|
| `/api/v1` | 일반 공개 API |
| `/api/v1/admin` | 관리자 전용 API |
| `/api/v1/auth` | 인증 및 토큰 관리 API |

### 2.2 URL 설계 원칙 / URL Design Principles

- 모든 엔드포인트는 `/api/{version}` 형식을 기본 prefix로 사용해야 한다.
- 화면 라우팅 URI와 API URI를 혼용하지 않는다.
- 리소스 이름은 복수형 명사를 사용한다.
- 단일 리소스 식별자는 path variable로 표현한다.

```text
/api/v1/products
/api/v1/products/{productId}
/api/v1/admin/products
/api/v1/auth/login
/api/v1/auth/refresh
```

### 2.3 버저닝 정책 / Versioning Policy

- 하위 호환이 깨지는 변경이 있을 때만 메이저 버전을 올린다.
- 선택 필드 추가, 새 엔드포인트 추가, 선택 query parameter 추가만으로는 메이저 버전을 올리지 않는다.
- 필드명 변경, 필드 타입 변경, required/optional 변경, 의미 변경은 breaking change로 본다.

---

## 3. 공통 응답 구조 / Common Response Envelope

`204 No Content`를 제외한 모든 응답은 `ApiResponse<T>` 형식을 사용한다.

### 3.1 스키마 / Schema

```typescript
interface ApiResponse<T> {
  success: boolean;             // 비즈니스 성공 여부
  code: string;                 // 비즈니스 코드
  message: string;              // 사용자/개발자용 메시지
  data: T | null;               // 성공 payload
  errors: ErrorDetail[] | null; // 검증 또는 비즈니스 오류 상세
  timestamp: string;            // ISO 8601 UTC timestamp
}
```

### 3.2 성공 응답 예시 / Success Response Examples

**단건 조회 - `200 OK`**

```json
{
  "success": true,
  "code": "OK",
  "message": "조회에 성공했습니다.",
  "data": {
    "productId": 1,
    "name": "Limited Sneakers",
    "price": 150000,
    "status": "ACTIVE",
    "createdAt": "2026-04-07T10:00:00.000Z"
  },
  "errors": null,
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

**생성 - `201 Created`**

```json
{
  "success": true,
  "code": "OK",
  "message": "리소스가 생성되었습니다.",
  "data": {
    "productId": 10
  },
  "errors": null,
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

---

## 4. 오류 응답 구조 / Error Response Schema

### 4.1 스키마 / Schema

```typescript
interface ErrorDetail {
  field: string;   // 오류가 발생한 입력 필드
  value: unknown;  // 거절된 값
  reason: string;  // 오류 사유
}
```

### 4.2 실패 응답 예시 / Failure Response Example

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "입력값이 올바르지 않습니다.",
  "data": null,
  "errors": [
    {
      "field": "price",
      "value": -1000,
      "reason": "가격은 0 이상이어야 합니다."
    },
    {
      "field": "name",
      "value": "",
      "reason": "상품명은 필수입니다."
    }
  ],
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

### 4.3 오류 시나리오 / Error Scenarios

| 상황 | HTTP Status | `code` |
|---|---:|---|
| Bean Validation 실패 | `400` | `VALIDATION_ERROR` |
| 비즈니스 규칙 위반 | `400` | 도메인별 코드 |
| 인증 필요 | `401` | `UNAUTHORIZED` |
| 권한 없음 | `403` | `ACCESS_DENIED` |
| 리소스 없음 | `404` | 도메인별 코드 |
| 중복 또는 상태 충돌 | `409` | 도메인별 코드 |
| 처리되지 않은 서버 오류 | `500` | `INTERNAL_SERVER_ERROR` |

**404 예시**

```json
{
  "success": false,
  "code": "PRODUCT_NOT_FOUND",
  "message": "상품을 찾을 수 없습니다. (id: 999)",
  "data": null,
  "errors": null,
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

**500 예시**

```json
{
  "success": false,
  "code": "INTERNAL_SERVER_ERROR",
  "message": "예상하지 못한 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
  "data": null,
  "errors": null,
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

---

## 5. HTTP Status 매핑 / HTTP Status Mapping

| 상황 | HTTP Status |
|---|---|
| 조회 성공 | `200 OK` |
| 생성 성공 | `201 Created` |
| 삭제 성공 | `204 No Content` |
| 입력 검증 실패 | `400 Bad Request` |
| 인증 필요 | `401 Unauthorized` |
| 권한 없음 | `403 Forbidden` |
| 리소스 없음 | `404 Not Found` |
| 중복 또는 상태 충돌 | `409 Conflict` |
| 처리되지 않은 서버 오류 | `500 Internal Server Error` |

- 실제 결과가 4xx 또는 5xx인 경우 `200 OK`로 감싸서 반환하지 않는다.

---

## 6. 비즈니스 오류 코드 규칙 / Business Error Code Conventions

### 6.1 명명 규칙 / Naming Convention

- 오류 코드는 `SCREAMING_SNAKE_CASE`를 사용한다.
- 기본 패턴은 `{DOMAIN}_{MEANING}` 이다.
- 코드 값은 코드베이스 전체에서 고유해야 한다.

```text
PRODUCT_NOT_FOUND
ORDER_NOT_FOUND
STOCK_EXHAUSTED
DUPLICATE_ORDER
PAYMENT_FAILED
USER_NOT_FOUND
UNAUTHORIZED
ACCESS_DENIED
VALIDATION_ERROR
INTERNAL_SERVER_ERROR
```

### 6.2 예약된 시스템 코드 / Reserved System Codes

| Code | 설명 |
|---|---|
| `OK` | 일반 성공 코드 |
| `VALIDATION_ERROR` | Bean Validation 또는 `@Valid` 실패 |
| `UNAUTHORIZED` | 인증 필요 |
| `ACCESS_DENIED` | 권한 부족 |
| `INTERNAL_SERVER_ERROR` | 처리되지 않은 예외 |

### 6.3 도메인 코드 예시 / Domain Code Examples

| Domain | Code | HTTP |
|---|---|---:|
| Product | `PRODUCT_NOT_FOUND` | `404` |
| Product | `PRODUCT_ALREADY_EXISTS` | `409` |
| Order | `ORDER_NOT_FOUND` | `404` |
| Order | `DUPLICATE_ORDER` | `409` |
| Stock | `STOCK_EXHAUSTED` | `400` |
| Payment | `PAYMENT_FAILED` | `400` |
| User | `USER_NOT_FOUND` | `404` |
| Reservation | `RESERVATION_EXPIRED` | `400` |
| Reservation | `RESERVATION_INVALID_STATE` | `409` |
| Drop Event | `DROP_EVENT_NOT_FOUND` | `404` |
| Drop Event | `DROP_EVENT_NOT_OPEN` | `400` |
| Brand | `BRAND_NAME_DUPLICATED` | `409` |
| Brand | `BRAND_REGISTRATION_FORBIDDEN` | `403` |

---

## 7. 필드 직렬화 규칙 / Field Serialization Rules

### 7.1 필드명 / Field Naming

- 모든 JSON 필드명은 `camelCase`를 사용한다.
- `snake_case`와 `PascalCase`는 사용하지 않는다.

```json
{ "productId": 1, "createdAt": "2026-04-07T10:00:00.000Z" }
```

### 7.2 `null`과 빈 배열 / `null` vs Empty Array

| 상황 | 값 |
|---|---|
| 단건 데이터 없음 | `"data": null` |
| 목록 데이터 없음 | `"data": []` |
| 오류 없음 | `"errors": null` |
| 오류 있음 | `"errors": [...]` |

- 목록 응답에 항목이 없더라도 `null` 대신 빈 배열을 사용한다.

### 7.3 날짜/시간 형식 / Date and Time Format

- 모든 timestamp는 UTC 기준 ISO 8601 형식을 사용한다.
- timestamp 형식: `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`
- 날짜 전용 필드는 `yyyy-MM-dd` 형식을 사용한다.

```json
{
  "createdAt": "2026-04-07T10:00:00.000Z",
  "eventDate": "2026-04-07"
}
```

```yaml
spring:
  jackson:
    date-format: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
    time-zone: UTC
    serialization:
      write-dates-as-timestamps: false
```

### 7.4 Enum 직렬화 / Enum Serialization

- Enum은 숫자가 아니라 문자열 값으로 직렬화한다.

```json
{ "status": "ACTIVE" }
```

---

## 8. HTTP Method별 요청 규칙 / Request Rules by HTTP Method

### 8.1 `GET` - 조회

- request body를 사용하지 않는다.
- 필터링, 정렬, 페이징은 query parameter를 사용한다.
- 단건 조회 식별자는 path variable을 사용한다.

```text
GET /api/v1/products
GET /api/v1/products/{productId}
GET /api/v1/products?status=ACTIVE&page=0&size=20&sort=price,desc
```

### 8.2 `POST` - 생성

- 요청 본문은 `application/json`을 사용한다.
- 요청 DTO에는 `@Valid` 적용을 기본으로 한다.
- 성공 시 `201 Created`를 반환한다.
- 가능하면 생성된 리소스 식별자를 `data`에 포함한다.

```http
POST /api/v1/products
Content-Type: application/json
```

```json
{
  "name": "Limited Sneakers",
  "price": 150000,
  "stockQuantity": 100,
  "status": "DRAFT"
}
```

### 8.3 `PUT` - 전체 수정

- 전체 리소스 표현을 요청 본문에 포함한다.
- 성공 시 `200 OK`를 반환한다.
- 대상 리소스가 없으면 `404`를 반환한다.

### 8.4 `PATCH` - 부분 수정

- 변경할 필드만 요청 본문에 포함한다.
- 성공 시 `200 OK`를 반환한다.
- 대상 리소스가 없으면 `404`를 반환한다.

### 8.5 `DELETE` - 삭제

- 성공 시 응답 본문 없이 `204 No Content`를 반환한다.
- 이미 삭제되었거나 존재하지 않는 리소스 정책은 도메인별로 정의하되, 문서와 구현을 일치시킨다.

---

## 9. 문서화 원칙 / Documentation Rules

- 모든 새 API는 요청 예시, 성공 응답 예시, 실패 응답 예시를 포함하는 것이 좋다.
- API 문서의 필드명, enum 값, 상태 전이 이름은 코드와 정확히 일치해야 한다.
- 문서와 구현이 충돌하면 어느 한쪽만 남기지 말고 동시에 정리한다.

---

## 10. 구현 체크리스트 / Implementation Checklist

- 새 API가 `/api/v1` 규칙을 따르는가?
- 응답이 `ApiResponse<T>` 형식을 따르는가?
- 상태 코드가 실제 결과와 일치하는가?
- 오류 코드가 고유하고 의미가 분명한가?
- JSON 필드가 `camelCase`인가?
- 날짜/시간이 UTC ISO 8601 형식인가?
- 목록 응답에서 빈 결과를 `[]`로 반환하는가?

---

## 11. 엔드포인트 예시 / Endpoint Examples

### 11.1 `POST /api/v1/brands` — 브랜드 등록

기업 회원이 본인 회사의 브랜드를 등록한다. 인증된 사용자만 호출 가능하며, 사용자 식별자는 JWT의 principal(`Long userId`)로 전달된다.

**요청 / Request**

```http
POST /api/v1/brands
Authorization: Bearer <access-token>
Content-Type: application/json
```

```json
{
  "name": "Mist",
  "description": "한정 드롭 전용 브랜드"
}
```

요청 필드 제약:

| 필드 | 타입 | 제약 |
|---|---|---|
| `name` | `string` | 필수, 공백 불가, 최대 100자 |
| `description` | `string` | 선택, 최대 255자 |

**성공 응답 / Success — `201 Created`**

```json
{
  "success": true,
  "code": "OK",
  "message": "리소스가 생성되었습니다.",
  "data": {
    "brandId": 42,
    "name": "Mist",
    "description": "한정 드롭 전용 브랜드",
    "companyId": 7,
    "createdAt": "2026-05-14T10:00:00.000Z"
  },
  "errors": null,
  "timestamp": "2026-05-14T10:00:00.000Z"
}
```

**검증 실패 / Validation Failure — `400 Bad Request`**

요청 본문이 `@NotBlank`/`@Size` 제약을 위반할 때.

```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "입력값이 올바르지 않습니다.",
  "data": null,
  "errors": [
    {
      "field": "name",
      "value": "",
      "reason": "공백일 수 없습니다"
    }
  ],
  "timestamp": "2026-05-14T10:00:00.000Z"
}
```

**미인증 / Unauthenticated — `401 Unauthorized`**

`Authorization` 헤더 없이 호출하면 Spring Security가 처리한다. (응답 본문은 `/api/**` 엔트리포인트가 기본 401만 보장하며, 후속 작업에서 본문 표준화 가능)

**권한 없음 / Forbidden — `403 Forbidden`**

다음 중 하나일 때:

- 인증된 사용자가 `userType != COMPANY`
- 기업 사용자이나 소속 `Company`가 없는 경우

```json
{
  "success": false,
  "code": "BRAND_REGISTRATION_FORBIDDEN",
  "message": "Brand registration forbidden for user: 1",
  "data": null,
  "errors": null,
  "timestamp": "2026-05-14T10:00:00.000Z"
}
```

**브랜드명 중복 / Duplicated — `409 Conflict`**

동일 회사 내에 같은 `name`의 브랜드가 이미 존재할 때.

```json
{
  "success": false,
  "code": "BRAND_NAME_DUPLICATED",
  "message": "Duplicated brand name 'Mist' for company: 7",
  "data": null,
  "errors": null,
  "timestamp": "2026-05-14T10:00:00.000Z"
}
```
