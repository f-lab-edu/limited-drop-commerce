# 공통 API 요청/응답 포맷 설계 가이드

> Spring Boot 실무 기준으로 작성된 RESTful API 요청/응답 포맷 설계 문서입니다.

---

## 목차

1. [요청 포맷 원칙](#1-요청-포맷-원칙)
2. [응답 포맷 원칙](#2-응답-포맷-원칙)
3. [성공/실패 응답 JSON 예시](#3-성공실패-응답-json-예시)
4. [에러 시나리오별 예시](#4-에러-시나리오별-예시)
5. [필드 컨벤션](#5-필드-컨벤션)
6. [Spring Boot 구현 예시](#6-spring-boot-구현-예시)
7. [실무 주의사항](#7-실무-주의사항)
8. [최종 권장안](#8-최종-권장안)

---

## 1. 요청 포맷 원칙

### 1-1. Path / Query / Body 역할 구분

| 위치 | 역할 | 사용 기준 |
|------|------|-----------|
| **Path Variable** | 리소스 식별자 | 특정 리소스를 지목할 때 (단일 ID) |
| **Query Parameter** | 필터링, 검색, 정렬, 페이지네이션 | 조회 조건, 선택적 파라미터 |
| **Request Body** | 상태 변경 데이터 | 등록·수정·삭제 시 전달할 구조화된 데이터 |
| **Header** | 인증·컨텍스트 정보 | Authorization, Accept-Language, Idempotency-Key 등 |

> **원칙**: 조회(GET)는 Body를 사용하지 않습니다. 검색 조건이 복잡하더라도 Query Parameter로 처리합니다.

---

### 1-2. CRUD별 요청 설계

#### 목록 조회
```
GET /api/v1/products
GET /api/v1/products?status=ACTIVE&categoryId=10
```

#### 단건 조회
```
GET /api/v1/products/{productId}
```

#### 등록
```
POST /api/v1/products
Content-Type: application/json

{
  "name": "한정판 스니커즈",
  "price": 150000,
  "stockQuantity": 100,
  "status": "DRAFT"
}
```

#### 전체 수정 (PUT)
```
PUT /api/v1/products/{productId}
Content-Type: application/json

{
  "name": "한정판 스니커즈 (수정)",
  "price": 160000,
  "stockQuantity": 80,
  "status": "ACTIVE"
}
```

#### 부분 수정 (PATCH)
```
PATCH /api/v1/products/{productId}
Content-Type: application/json

{
  "status": "INACTIVE"
}
```

#### 삭제
```
DELETE /api/v1/products/{productId}
```

---

### 1-3. 검색 / 정렬 / 페이지네이션

```
GET /api/v1/products
  ?keyword=스니커즈
  &status=ACTIVE
  &minPrice=50000
  &maxPrice=200000
  &sort=price,desc
  &sort=createdAt,desc
  &page=0
  &size=20
```

| 파라미터 | 설명 | 기본값 |
|----------|------|--------|
| `page` | 0-based 페이지 번호 | 0 |
| `size` | 페이지당 항목 수 | 20 |
| `sort` | `필드명,asc\|desc` 형식, 다중 정렬 가능 | 없음 |
| `keyword` | 전문 검색어 | 없음 |

> Spring Data의 `Pageable`을 파라미터로 받으면 `page`, `size`, `sort`가 자동 바인딩됩니다.

```java
@GetMapping("/products")
public ApiResponse<Page<ProductResponse>> getProducts(
        @ModelAttribute ProductSearchRequest request,
        Pageable pageable) { ... }
```

---

### 1-4. 공통 요청 래퍼(Request Wrapper) 장단점

일부 팀에서 아래처럼 모든 요청을 단일 래퍼로 감싸는 방식을 사용합니다.

```json
{
  "header": { "requestId": "uuid", "timestamp": "2026-04-07T10:00:00Z" },
  "body": { "name": "상품명", "price": 10000 }
}
```

| 구분 | 내용 |
|------|------|
| **장점** | 요청 추적(requestId) 표준화, 메타데이터 일관성 |
| **단점** | REST 관례 위반 (메타 정보는 Header에 두는 것이 표준), Swagger 문서 복잡도 증가, 클라이언트 구현 부담 |

**실무 권장**: 요청 래퍼는 사용하지 않습니다. `requestId`는 `X-Request-Id` HTTP 헤더로 처리하고, MDC에 저장해 로그에 포함합니다.

---

## 2. 응답 포맷 원칙

### 2-1. 공통 응답 필드 정의

```json
{
  "success": true,
  "code": "PRODUCT_001",
  "message": "상품이 성공적으로 등록되었습니다.",
  "data": { },
  "errors": null,
  "timestamp": "2026-04-07T10:00:00.000Z"
}
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `success` | `boolean` | 비즈니스 성공 여부 |
| `code` | `string` | 비즈니스 코드 (HTTP Status와 별개) |
| `message` | `string` | 사람이 읽을 수 있는 결과 메시지 |
| `data` | `object \| array \| null` | 성공 시 응답 페이로드 |
| `errors` | `array \| null` | 실패 시 에러 상세 목록 |
| `timestamp` | `string` | 응답 생성 시각 (ISO 8601) |

---

### 2-2. HTTP Status vs Business Code

**HTTP Status**와 **Business Code**는 역할이 다릅니다. 두 가지를 모두 사용해야 합니다.

| 구분 | 역할 | 예시 |
|------|------|------|
| **HTTP Status** | 전송 계층 결과, 인프라/클라이언트가 해석 | `400`, `404`, `500` |
| **Business Code** | 도메인 결과, 애플리케이션이 해석 | `ORDER_001`, `STOCK_EXHAUSTED` |

```
HTTP 400  →  code: "VALIDATION_ERROR"     (입력값 검증 실패)
HTTP 400  →  code: "STOCK_EXHAUSTED"      (재고 부족 - 비즈니스 규칙 위반)
HTTP 404  →  code: "PRODUCT_NOT_FOUND"
HTTP 409  →  code: "DUPLICATE_ORDER"
HTTP 500  →  code: "INTERNAL_SERVER_ERROR"
```

**Business Code 네이밍 규칙**: `{도메인}_{의미}` 형식의 SCREAMING_SNAKE_CASE

---

## 3. 성공/실패 응답 JSON 예시

### 단건 조회 성공

```http
HTTP/1.1 200 OK
```
```json
{
  "success": true,
  "code": "OK",
  "message": "성공",
  "data": {
    "productId": 1,
    "name": "한정판 스니커즈",
    "price": 150000,
    "status": "ACTIVE",
    "createdAt": "2026-04-07T10:00:00.000Z"
  },
  "errors": null,
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

### 목록 조회 성공 (페이지네이션)

```http
HTTP/1.1 200 OK
```
```json
{
  "success": true,
  "code": "OK",
  "message": "성공",
  "data": {
    "content": [
      { "productId": 1, "name": "스니커즈 A", "price": 150000 },
      { "productId": 2, "name": "스니커즈 B", "price": 130000 }
    ],
    "page": 0,
    "size": 20,
    "totalElements": 42,
    "totalPages": 3,
    "last": false
  },
  "errors": null,
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

### 등록 성공

```http
HTTP/1.1 201 Created
```
```json
{
  "success": true,
  "code": "OK",
  "message": "상품이 등록되었습니다.",
  "data": {
    "productId": 10
  },
  "errors": null,
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

### 삭제 성공

```http
HTTP/1.1 204 No Content
```
> 삭제 성공은 Body 없이 `204 No Content`를 반환합니다.

### 실패 응답

```http
HTTP/1.1 400 Bad Request
```
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
      "reason": "가격은 0원 이상이어야 합니다."
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

---

## 4. 에러 시나리오별 예시

### Validation 오류 (`@Valid` 실패)

```http
HTTP/1.1 400 Bad Request
```
```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "입력값이 올바르지 않습니다.",
  "data": null,
  "errors": [
    { "field": "email", "value": "not-an-email", "reason": "올바른 이메일 형식이 아닙니다." }
  ],
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

### 인가 오류 (권한 없음)

```http
HTTP/1.1 403 Forbidden
```
```json
{
  "success": false,
  "code": "ACCESS_DENIED",
  "message": "접근 권한이 없습니다.",
  "data": null,
  "errors": null,
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

### 리소스 없음 (404)

```http
HTTP/1.1 404 Not Found
```
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

### 비즈니스 규칙 위반 (재고 부족)

```http
HTTP/1.1 400 Bad Request
```
```json
{
  "success": false,
  "code": "STOCK_EXHAUSTED",
  "message": "재고가 부족합니다.",
  "data": null,
  "errors": null,
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

### 서버 오류 (500)

```http
HTTP/1.1 500 Internal Server Error
```
```json
{
  "success": false,
  "code": "INTERNAL_SERVER_ERROR",
  "message": "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.",
  "data": null,
  "errors": null,
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

> **주의**: 500 응답에 예외 스택 트레이스나 내부 구현 정보를 절대 포함하지 않습니다.

---

## 5. 필드 컨벤션

### 필드명

- **camelCase** 사용 (JSON 표준 관례)
- `snake_case`는 사용하지 않습니다.

```json
// Good
{ "productId": 1, "createdAt": "..." }

// Bad
{ "product_id": 1, "created_at": "..." }
```

### null vs 빈 배열

| 상황 | 처리 방법 |
|------|-----------|
| 단일 객체가 없는 경우 | `"data": null` |
| 목록이 비어있는 경우 | `"data": []` (null 금지) |
| 에러가 없는 경우 | `"errors": null` |
| 에러 목록이 있는 경우 | `"errors": [...]` |

> 빈 배열(`[]`)과 `null`은 의미가 다릅니다. 목록 응답에서 결과가 없으면 `null`이 아닌 `[]`를 반환합니다.

### 날짜 포맷

- **ISO 8601** 형식 사용: `yyyy-MM-dd'T'HH:mm:ss.SSS'Z'`
- 시간대는 **UTC**를 기준으로 반환하고, 클라이언트가 변환합니다.
- 날짜만 필요한 경우: `yyyy-MM-dd`

```json
{
  "createdAt": "2026-04-07T10:00:00.000Z",
  "eventDate": "2026-04-07"
}
```

Spring Boot 설정:
```yaml
spring:
  jackson:
    date-format: yyyy-MM-dd'T'HH:mm:ss.SSS'Z'
    time-zone: UTC
    serialization:
      write-dates-as-timestamps: false
```

### Enum 처리

- **문자열(String)** 으로 직렬화합니다. 숫자 코드로 내려보내지 않습니다.
- 클라이언트가 의미를 알 수 없는 숫자보다 명시적 문자열이 낫습니다.

```json
// Good
{ "status": "ACTIVE" }

// Bad
{ "status": 1 }
```

---

## 6. Spring Boot 구현 예시

### ApiResponse\<T\>

```java
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;
    private final List<ErrorDetail> errors;
    private final String timestamp;

    private ApiResponse(boolean success, String code, String message,
                        T data, List<ErrorDetail> errors) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
        this.errors = errors;
        this.timestamp = Instant.now().toString();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", "성공", data, null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, "OK", "등록되었습니다.", data, null);
    }

    public static ApiResponse<Void> fail(String code, String message) {
        return new ApiResponse<>(false, code, message, null, null);
    }

    public static ApiResponse<Void> fail(String code, String message,
                                         List<ErrorDetail> errors) {
        return new ApiResponse<>(false, code, message, null, errors);
    }
}
```

### ErrorDetail

```java
@Getter
@AllArgsConstructor
public class ErrorDetail {
    private final String field;
    private final Object value;
    private final String reason;

    public static ErrorDetail of(FieldError error) {
        return new ErrorDetail(
            error.getField(),
            error.getRejectedValue(),
            error.getDefaultMessage()
        );
    }
}
```

### 요청 DTO (Bean Validation)

```java
@Getter
@NoArgsConstructor
public class ProductCreateRequest {

    @NotBlank(message = "상품명은 필수입니다.")
    @Size(max = 100, message = "상품명은 100자 이내여야 합니다.")
    private String name;

    @NotNull(message = "가격은 필수입니다.")
    @Min(value = 0, message = "가격은 0원 이상이어야 합니다.")
    private Long price;

    @NotNull(message = "재고는 필수입니다.")
    @Min(value = 0, message = "재고는 0개 이상이어야 합니다.")
    private Integer stockQuantity;
}
```

### @RestControllerAdvice

```java
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // Bean Validation 실패 (@Valid)
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<ErrorDetail> errors = ex.getBindingResult().getFieldErrors()
                .stream()
                .map(ErrorDetail::of)
                .toList();

        return ResponseEntity.badRequest()
                .body(ApiResponse.fail("VALIDATION_ERROR", "입력값이 올바르지 않습니다.", errors));
    }

    // 비즈니스 예외 (직접 정의한 예외)
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.fail(ex.getCode(), ex.getMessage()));
    }

    // 리소스 없음
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("NOT_FOUND", ex.getMessage()));
    }

    // Spring Security 인가 실패
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("ACCESS_DENIED", "접근 권한이 없습니다."));
    }

    // 그 외 모든 예외
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError()
                .body(ApiResponse.fail("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."));
    }
}
```

### BusinessException (공통 비즈니스 예외)

```java
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public BusinessException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}

// 도메인별 예외
public class ProductNotFoundException extends BusinessException {
    public ProductNotFoundException(Long id) {
        super("PRODUCT_NOT_FOUND", "상품을 찾을 수 없습니다. (id: " + id + ")", HttpStatus.NOT_FOUND);
    }
}

public class StockExhaustedException extends BusinessException {
    public StockExhaustedException() {
        super("STOCK_EXHAUSTED", "재고가 부족합니다.", HttpStatus.BAD_REQUEST);
    }
}
```

### Controller

```java
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping("/{productId}")
    public ResponseEntity<ApiResponse<ProductResponse>> getProduct(@PathVariable Long productId) {
        ProductResponse product = productService.getProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok(product));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> getProducts(
            @ModelAttribute ProductSearchRequest request,
            Pageable pageable) {
        Page<ProductResponse> products = productService.getProducts(request, pageable);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.of(products)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductCreateResponse>> createProduct(
            @RequestBody @Valid ProductCreateRequest request) {
        ProductCreateResponse response = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created(response));
    }

    @DeleteMapping("/{productId}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 7. 실무 주의사항

### 모든 응답을 200으로 처리하는 문제

```json
// 안티패턴: HTTP 400 상황인데 200으로 응답
HTTP/1.1 200 OK
{ "success": false, "code": "NOT_FOUND", ... }
```

**문제점**
- Spring Security, API Gateway, 모니터링 툴이 HTTP Status를 기준으로 동작합니다.
- Nginx의 `proxy_cache`, 브라우저 캐시 정책이 깨집니다.
- Grafana/Datadog 등 APM 도구의 에러율 집계가 불가능합니다.

**권장**: HTTP Status는 반드시 실제 상황을 반영합니다.

---

### message 필드를 기준으로 분기하는 문제

```javascript
// 안티패턴 (클라이언트 코드)
if (response.message === "재고가 부족합니다.") { ... }
```

**문제점**: `message`는 사람이 읽는 용도입니다. 다국어 처리나 메시지 수정 시 클라이언트 코드가 깨집니다.

**권장**: 클라이언트는 항상 `code`를 기준으로 분기합니다.

```javascript
// 올바른 방법
if (response.code === "STOCK_EXHAUSTED") { ... }
```

---

### 조회 API에서 Request Body 사용 문제

```
// 안티패턴
GET /api/v1/products
Body: { "status": "ACTIVE", "keyword": "스니커즈" }
```

**문제점**
- HTTP 명세상 GET의 Body는 정의되지 않았습니다.
- 프록시, 캐시 서버, 일부 HTTP 클라이언트가 Body를 무시합니다.
- Swagger/OpenAPI 문서화가 어렵습니다.

**권장**: 검색 조건은 Query Parameter로 처리합니다. 조건이 매우 복잡하다면 POST로 설계하되 URI에 `/search`를 명시합니다.
```
POST /api/v1/products/search
```

---

### 과도한 공통화 문제

```java
// 안티패턴: 모든 응답을 하나의 클래스로 처리하려는 시도
ApiResponse<Object> response = new ApiResponse<>(
    true, "OK", "성공", data, null, requestId, version, traceId, ...
);
```

**문제점**
- 필드가 늘어날수록 사용하지 않는 `null` 필드가 많아집니다.
- API마다 다른 메타데이터가 필요해지면 공통 구조가 오히려 복잡해집니다.

**권장**: 공통 응답 필드는 최소화합니다 (`success`, `code`, `message`, `data`, `errors`, `timestamp`). 특수 메타데이터는 HTTP Header로 처리합니다 (`X-Request-Id`, `X-Trace-Id`).

---

## 8. 최종 권장안

### 응답 JSON 구조

**성공 (단건)**
```json
{
  "success": true,
  "code": "OK",
  "message": "성공",
  "data": {
    "productId": 1,
    "name": "한정판 스니커즈",
    "price": 150000,
    "status": "ACTIVE",
    "createdAt": "2026-04-07T10:00:00.000Z"
  },
  "errors": null,
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

**실패**
```json
{
  "success": false,
  "code": "VALIDATION_ERROR",
  "message": "입력값이 올바르지 않습니다.",
  "data": null,
  "errors": [
    { "field": "price", "value": -1, "reason": "가격은 0원 이상이어야 합니다." }
  ],
  "timestamp": "2026-04-07T10:01:00.000Z"
}
```

### HTTP Status 매핑 기준

| 상황 | HTTP Status |
|------|-------------|
| 조회 성공 | `200 OK` |
| 등록 성공 | `201 Created` |
| 삭제 성공 | `204 No Content` |
| 입력값 검증 실패 | `400 Bad Request` |
| 인증 필요 | `401 Unauthorized` |
| 권한 없음 | `403 Forbidden` |
| 리소스 없음 | `404 Not Found` |
| 상태 충돌 (중복 등) | `409 Conflict` |
| 서버 오류 | `500 Internal Server Error` |

### 최종 Java 구현 요약

```java
// 1. 공통 응답
ApiResponse.ok(data)              // 200
ApiResponse.created(data)         // 201
ApiResponse.fail(code, message)   // 4xx/5xx

// 2. 예외 계층
BusinessException                 // 비즈니스 규칙 위반 (도메인별 상속)
  └─ ProductNotFoundException      // 404
  └─ StockExhaustedException       // 400

// 3. 전역 예외 처리
@RestControllerAdvice GlobalExceptionHandler
  └─ MethodArgumentNotValidException  → 400 VALIDATION_ERROR
  └─ BusinessException               → 도메인 정의 코드/상태
  └─ AccessDeniedException           → 403 ACCESS_DENIED
  └─ Exception                       → 500 INTERNAL_SERVER_ERROR

// 4. Controller 패턴
return ResponseEntity.ok(ApiResponse.ok(result));
return ResponseEntity.status(CREATED).body(ApiResponse.created(result));
return ResponseEntity.noContent().build();   // DELETE
```
