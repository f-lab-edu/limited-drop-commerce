# AGENTS.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

고동시성 환경에서 선착순 주문, 재고 선점, 결제 만료 복구를 처리하는 이벤트 기반 한정 판매 커머스 백엔드 시스템

## Commands

```bash
# 빌드
./gradlew build

# 애플리케이션 실행 (local 프로파일)
docker-compose up -d app

# 전체 테스트 실행
./gradlew test

# 단일 테스트 클래스 실행
./gradlew test --tests "com.mist.commerce.domain.product.repository.ProductRepositoryTest"

# 단일 테스트 메서드 실행
./gradlew test --tests "com.mist.commerce.domain.product.repository.ProductRepositoryTest.findAll"

# 인프라(MySQL) 실행
docker-compose up -d mysql
```

## References Documents
Details on architecture, coding conventions, testing patterns, and other guidelines are explained in the following document

docs 폴더 내에 설계 가이드를 따른다.
- @docs/project-overview.md : 프로젝트 개요, 기술 스택, 실행 방법, 프로필 설명
- @docs/api-contract.md : API 계약 명세
- @docs/architecture.md : 프로젝트 아키텍처와 주요 패턴 설명
- @docs/use-cases.md : 주요 기능별 시나리오와 흐름 설명
- @docs/branch-rules.md : 브랜치 전략과 커밋 메시지 규칙 설명

<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**IMPORTANT: This project has a knowledge graph. ALWAYS use the
code-review-graph MCP tools BEFORE using Grep/Glob/Read to explore
the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file
scanning cannot.

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Fall back to Grep/Glob/Read **only** when the graph doesn't cover what you need.

### Key Tools

| Tool | Use when |
|------|----------|
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

### Workflow

1. The graph auto-updates on file changes (via hooks).
2. Use `detect_changes` for code review.
3. Use `get_affected_flows` to understand impact.
4. Use `query_graph` pattern="tests_for" to check coverage.
