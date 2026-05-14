# 도구 사용 지침

이 문서는 개발 작업 중 사용하는 도구들의 사용 기준을 정의한다.
특정 도구에 대한 세부 기준은 도구별 섹션에 추가한다.

---

## code-review-graph

이 프로젝트는 코드 지식 그래프를 사용한다. 코드베이스를 탐색할 때는 Grep, Glob, Read보다 code-review-graph MCP 도구를 먼저 사용한다.

그래프 도구는 코드 구조, 호출 관계, 영향 범위, 테스트 커버리지 맥락을 빠르게 제공한다. 그래프가 필요한 정보를 제공하지 못할 때만 파일 검색과 직접 읽기로 보완한다.

### 우선 사용 상황

- 코드 탐색: `semantic_search_nodes` 또는 `query_graph`
- 변경 영향 확인: `get_impact_radius`
- 코드 리뷰: `detect_changes`와 `get_review_context`
- 호출, import, 테스트 관계 확인: `query_graph`
- 아키텍처 파악: `get_architecture_overview`와 `list_communities`

### 주요 도구

| Tool | Use when |
|---|---|
| `detect_changes` | 변경 사항을 위험도 기준으로 리뷰할 때 |
| `get_review_context` | 리뷰에 필요한 소스 스니펫과 맥락이 필요할 때 |
| `get_impact_radius` | 변경의 영향 범위를 확인할 때 |
| `get_affected_flows` | 변경된 파일이 어떤 실행 흐름에 영향을 주는지 확인할 때 |
| `query_graph` | 호출자, 피호출자, import, 테스트 관계를 추적할 때 |
| `semantic_search_nodes` | 함수, 클래스, 파일을 이름이나 키워드로 찾을 때 |
| `get_architecture_overview` | 전체 구조와 커뮤니티 경계를 파악할 때 |
| `refactor_tool` | rename, dead code 탐색, 리팩터링 제안이 필요할 때 |

### 기본 흐름

1. 변경 리뷰는 `detect_changes`로 시작한다.
2. 영향 범위는 `get_affected_flows` 또는 `get_impact_radius`로 확인한다.
3. 테스트 커버리지는 `query_graph`의 `tests_for` 패턴으로 확인한다.
4. 그래프가 부족한 경우에만 `rg`나 직접 파일 읽기로 보완한다.
