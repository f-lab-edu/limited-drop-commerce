# 브랜치 전략 (GitHub Flow)

## 개요

이 프로젝트는 **GitHub Flow**를 브랜치 전략으로 사용합니다.
GitHub Flow는 단순하고 지속적인 배포에 적합한 경량 워크플로우입니다.

---

## 핵심 원칙

- `main` 브랜치는 항상 배포 가능한 상태를 유지합니다.
- 모든 작업은 `main`에서 분기한 별도의 브랜치에서 진행합니다.
- 작업이 완료되면 Pull Request를 통해 `main`으로 병합합니다.

---

## 브랜치 구조

```
예시
main
 ├── feat/#3
 ├── fix/#12
 ├── docs/#21
 └── chore/#34
```

| 브랜치 | 설명 |
|--------|------|
| `main` | 항상 배포 가능한 상태. 직접 커밋 금지 |
| `feat/#<issue-number>` | 새로운 기능 개발 |
| `fix/#<issue-number>` | 버그 수정 |
| `chore/#<issue-number>` | 설정, 의존성, 빌드 등 비기능 변경 |
| `docs/#<issue-number>` | 문서 작성 및 수정 |
| `refactor/#<issue-number>` | 기능 변경 없는 코드 구조 개선 |

브랜치 이름 규칙:

- 브랜치명은 반드시 `타입/#이슈번호` 형식을 사용한다.
- 이슈 번호는 GitHub Issue 번호를 기준으로 한다.
- 하나의 브랜치는 하나의 이슈만 담당한다.

예시:

- `feat/#3`
- `fix/#12`
- `docs/#21`
- `refactor/#44`

---

## 워크플로우

### 1. 브랜치 생성

`main`에서 최신 코드를 받아 목적에 맞는 브랜치를 생성합니다.

```bash
git checkout main
git pull origin main
git checkout -b feat/#3
```

### 2. 작업 및 커밋

작업 단위가 명확하도록 커밋을 작성합니다.

```bash
git add .
git commit -m "feat: 상품 상세 조회 API 구현"
```

### 3. 원격 브랜치에 Push

```bash
git push origin feat/#3
```

### 4. Pull Request 생성

- GitHub에서 `main` 대상으로 PR을 생성합니다.
- PR 제목과 설명에 변경 내용과 이유를 명확히 작성합니다.
- 리뷰어를 지정하여 코드 리뷰를 요청합니다.

### 5. 코드 리뷰 및 승인

- 리뷰어는 코드 품질, 로직, 컨벤션 등을 검토합니다.
- 요청된 수정 사항은 해당 브랜치에 추가 커밋으로 반영합니다.
- 1명 이상의 Approve를 받은 후 병합합니다.

### 6. main에 병합

- Squash and Merge 또는 Merge Commit 방식을 사용합니다.
- 병합 후 원격 브랜치를 삭제합니다.

### 7. 배포

- `main`에 병합되면 CI/CD 파이프라인을 통해 자동 배포됩니다.

---

## 커밋 메시지 컨벤션

```
<type>: <subject>
```

| type | 설명 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 |
| `chore` | 빌드, 설정, 의존성 변경 |
| `docs` | 문서 변경 |
| `test` | 테스트 코드 추가 및 수정 |
| `style` | 코드 포맷, 세미콜론 등 스타일 변경 |

**예시**
```
feat: 상품 목록 조회 API 구현
fix: 재고 차감 시 동시성 이슈 수정
chore: QueryDSL 의존성 추가
```

## 커밋 규칙
- 커밋 메시지는 명확하고 간결하게 작성합니다.
- 커밋 단위는 하나의 논리적 변경으로 제한합니다.

## 커밋 본문 작성 지침

변경 범위가 테스트, 설정, 문서, 여러 계층의 코드를 함께 포함하는 경우 제목만으로 의도를 파악하기 어렵기 때문에 커밋 본문을 함께 작성합니다.

- 첫 줄 제목은 `<type>: <subject>` 형식을 유지합니다.
- 본문에는 `변경 내용`, `검증`, `참고 사항`을 구분해 작성합니다.
- `변경 내용`에는 실제로 바뀐 코드와 문서의 핵심을 항목별로 적습니다.
- `검증`에는 실행한 테스트 명령과 결과를 적습니다.
- 실행하지 못한 검증이 있으면 생략하지 않고 사유를 적습니다.
- `참고 사항`에는 후속 작업, 제외한 범위, 운영상 주의점이 있을 때만 적습니다.
- 본문은 변경 이유와 영향 범위를 설명하되 구현 코드 전체를 반복하지 않습니다.

예시:

```text
feat: 사용자 Repository 구현

변경 내용:
- 사용자, OAuth 계정, Refresh Token 세션 Repository를 추가
- EmailVerification 최신 조회 정렬을 createdAt DESC, id DESC로 정의
- Repository 통합 테스트와 Redis 저장소 테스트를 추가

검증:
- ./gradlew test --tests "com.mist.commerce.domain.user.repository.UserRepositoryTest" -x processTestAot -x compileAotTestJava -x processAotTestResources

참고 사항:
- 전체 테스트는 AOT 단계의 Testcontainers 포트 바인딩 문제로 별도 조정 필요
```

---

## PR 규칙

- PR 단위는 하나의 기능 또는 하나의 버그 수정으로 제한합니다.
- PR 제목은 커밋 메시지 컨벤션을 따릅니다.
- 셀프 머지는 금지합니다 (긴급 상황 제외).
- CI 검사가 통과된 PR만 병합합니다.

---

## main 브랜치 보호 규칙

- `main` 브랜치에 직접 Push 금지
- PR 병합 전 코드 리뷰 1명 이상 필수
- CI (빌드 및 테스트) 통과 필수
