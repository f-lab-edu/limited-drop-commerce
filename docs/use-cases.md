# 유스케이스 — Limited Drop Commerce

> 유스케이스는 도메인별 파일로 분리되어 있다. 각 파일에서 상세 내용을 확인한다.

---

## 도메인별 유스케이스 목록

| 파일 | 도메인 | 유스케이스 |
|---|---|---|
| [user.md](use-cases/user.md) | 회원 (User / Company) | UC-01 일반 회원가입 (소셜), UC-02 기업 회원가입, UC-03 로그인 |
| [drop.md](use-cases/drop.md) | 드롭 이벤트 (Drop) | UC-04 드롭 이벤트 목록 조회, UC-05 드롭 이벤트 상세 조회, UC-13 드롭 이벤트 등록 (관리자) |
| [reservation.md](use-cases/reservation.md) | 예약 / 재고 선점 (Reservation) | UC-06 재고 선점 (선착순), UC-07 예약 취소, UC-15 만료·취소 주문 재고 복구 (시스템) |
| [order.md](use-cases/order.md) | 주문 (Order) | UC-08 주문 생성, UC-09 주문 내역 조회, UC-10 주문 취소, UC-14 미결제 주문 만료 처리 (시스템) |
| [payment.md](use-cases/payment.md) | 결제 (Payment) | UC-11 결제 요청, UC-12 결제 취소 / 환불 |

---

## UC 번호 빠른 참조

| UC | 제목 | 파일 |
|---|---|---|
| UC-01 | 일반 회원가입 (소셜) | [user.md](use-cases/user.md#uc-01-일반-회원가입-소셜) |
| UC-02 | 기업 회원가입 | [user.md](use-cases/user.md#uc-02-기업-회원가입) |
| UC-03 | 로그인 | [user.md](use-cases/user.md#uc-03-로그인) |
| UC-04 | 드롭 이벤트 목록 조회 | [drop.md](use-cases/drop.md#uc-04-드롭-이벤트-목록-조회) |
| UC-05 | 드롭 이벤트 상세 조회 | [drop.md](use-cases/drop.md#uc-05-드롭-이벤트-상세-조회) |
| UC-06 | 재고 선점 (선착순) | [reservation.md](use-cases/reservation.md#uc-06-재고-선점-선착순) |
| UC-07 | 예약 취소 | [reservation.md](use-cases/reservation.md#uc-07-예약-취소) |
| UC-08 | 주문 생성 | [order.md](use-cases/order.md#uc-08-주문-생성) |
| UC-09 | 주문 내역 조회 | [order.md](use-cases/order.md#uc-09-주문-내역-조회) |
| UC-10 | 주문 취소 | [order.md](use-cases/order.md#uc-10-주문-취소) |
| UC-11 | 결제 요청 | [payment.md](use-cases/payment.md#uc-11-결제-요청) |
| UC-12 | 결제 취소 / 환불 | [payment.md](use-cases/payment.md#uc-12-결제-취소--환불) |
| UC-13 | 드롭 이벤트 등록 (관리자) | [drop.md](use-cases/drop.md#uc-13-드롭-이벤트-등록-관리자) |
| UC-14 | 미결제 주문 만료 처리 (시스템) | [order.md](use-cases/order.md#uc-14-미결제-주문-만료-처리-시스템) |
| UC-15 | 만료/취소 주문 재고 복구 (시스템) | [reservation.md](use-cases/reservation.md#uc-15-만료취소-주문-재고-복구-시스템) |
