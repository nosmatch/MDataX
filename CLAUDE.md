# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MDataX is a lightweight data management platform (数据管理平台). It is a B端 (enterprise) web platform for data integration, development, asset management, querying, quality monitoring, and access control. The product language is Chinese.

Architecture: MySQL sources → data integration → ClickHouse storage → SQL development / data query, with RBAC permission control. The backend is a Spring Boot application with a Vue 3 frontend under `mdatax-web/`.

## Build & Development

This is a Spring Boot 2.7 / Java 8 Maven multi-module project.

```bash
# Compile all modules
mvn compile

# Run tests
mvn test
mvn test -Dtest=ClassName           # single test class
mvn test -Dtest=ClassName#method    # single test method

# Package
mvn package
```

### Modules

| Module | Purpose | Main Class | Default Port |
|--------|---------|------------|--------------|
| `mdatax-core` | Shared constants/utilities (no main class) | — | — |
| `mdatax-server` | Main REST API server | `com.mogu.data.MDataXApplication` | 7081 |
| `mdatax-schedulerx` | Independent DAG scheduler system | `com.mogu.data.schedulerx.SchedulerXApplication` | 8081 |

Both server modules are executable Spring Boot jars and can be run independently. The main server depends on `mdatax-core`; `mdatax-schedulerx` also depends on `mdatax-core` but runs as a separate service.

```bash
# Run main server
mvn spring-boot:run -pl mdatax-server

# Run schedulerx (in another terminal)
mvn spring-boot:run -pl mdatax-schedulerx
```

### Frontend

The frontend lives in `mdatax-web/` (Vue 3 + Vite + Pinia + Element Plus).

```bash
cd mdatax-web
npm install
npm run dev          # dev server on port 5173, proxies /api → localhost:7081
npm run build
```

All API calls use `/api` as the base path (matched by the server's `server.servlet.context-path=/api`).

Java 8 is the target and source version. The active Spring profile is `dev` (`application-dev.yml`).

## Code Conventions

- All Java source files must include an `@author fengzhu` annotation in the class-level Javadoc.
- Package root: `com.mogu.data`

## Architecture

### Multi-DataSource Setup

The server uses two manually-configured Druid datasources (see `DataSourceConfig.java`):

- **MySQL** (`@Primary`, bean name `dataSource`): Application metadata, managed by MyBatis-Plus.
- **ClickHouse** (bean name `clickHouseDataSource`): Analytics queries, accessed via a dedicated `JdbcTemplate` (`clickHouseJdbcTemplate`).

ClickHouse is configured via the custom `clickhouse.datasource.*` prefix in `application-dev.yml`. The `@Primary` annotation on MySQL is required because ClickHouse JDBC presence would otherwise cause Spring Boot auto-configuration to skip MySQL entirely.

### Unified SQL Execution Engine

`ClickHouseQueryService` (in `query` package) is the single shared engine for both ad-hoc queries and scheduled SQL tasks. It:

- Enforces read-only mode for query paths (rejects non-SELECT).
- Auto-appends `LIMIT` and `max_execution_time` settings.
- Normalizes column names to lowercase.
- Returns `QueryResultVO` with columns, rows, row count, and execution time.

Both the **数据查询** (Data Query) and **数据开发** (SQL Task) modules route ClickHouse SQL through this service.

### Authentication & Authorization

- **JWT-based auth**: `JwtInterceptor` validates `Authorization: Bearer <token>` on every request. Valid tokens populate a thread-local `LoginUser` (userId, username, admin flag), which is cleaned up in `afterCompletion`.
- **RBAC**: `@RequirePermission(table = "...", type = "READ|WRITE")` can be placed on controller methods for table-level permission checks. The permission system lives in the `system` package (users → roles → table permissions).
- **Global exception handling**: `GlobalExceptionHandler` returns unified `Result` wrappers with `ResultCode` enums.

### Scheduling Architecture

Three scheduling mechanisms coexist:

1. **Local cron scheduler** (`TaskSchedulerManager`, `SqlTaskSchedulerManager`, `SyncTaskSchedulerManager`): Spring `@Scheduled` tasks that execute SQL/sync jobs directly within the main server JVM. Controlled by `scheduler.type=local` in `application.yml`.
2. **DolphinScheduler integration** (`DolphinSchedulerClient`): When `scheduler.type=dolphinscheduler`, the server creates DS workflow definitions (SHELL tasks that curl back to MDataX) and schedules them via DS's cron API. DS handles the actual triggering; MDataX handles execution via `InternalTaskController` callbacks.
3. **SchedulerX** (`mdatax-schedulerx` module): A separate Spring Boot application with its own DAG engine (`DagEngine`), topology resolver, state machine, and event-driven task executor. It manages DAG definitions, instances, and task instances independently. Communicates with the main server via HTTP callbacks.

### Operation Logging

All POST/PUT/DELETE controller methods are automatically logged via `OperationLogAspect` (AOP). Logs are written to the `operation_log` table in MySQL and include user, IP, method, params, result, duration, and status.

### MyBatis-Plus Configuration

- Logic delete is enabled globally: field `deleted`, delete value `1`, not-deleted value `0`.
- Mapper XML locations: `classpath*:/mapper/**/*.xml`
- ID type: `auto`
- Camel-case mapping enabled.

## Development Plan

The detailed development plan is in `DEVELOPMENT_PLAN.md`. Follow the staged schedule (基础框架 → 系统基础 → 数据资产 → 数据查询 → 数据集成 → 数据开发 → 首页工作台) when adding features.

## Design Context

Product requirements and UI prototypes are documented in Chinese under the `design/` directory:

- `design/数据管理平台 PRD（MVP版本）` — Product requirements, module specs, acceptance criteria, and risk notes.
- `design/整体产品结构` — Information architecture, page layouts, core interaction flows, and design principles.

Key modules (implemented):
1. **数据集成** (`integration`) — Data source management (MySQL), full/incremental sync tasks to ClickHouse, DolphinScheduler integration.
2. **数据开发** (`integration`, `query`) — SQL editor and task management with cron scheduling.
3. **数据资产** (`metadata`) — Data catalog and table metadata (auto-discovered from ClickHouse).
4. **数据查询** (`query`) — SQL query interface against ClickHouse via the shared `ClickHouseQueryService`.
5. **数据质量监控** (`quality`) — Rule templates, scheduled checks, quality reports/dashboard, webhook alerts.
6. **报表** (`report`) — Report editing, viewing, collaborators, and permission applications.
7. **权限管理** (`system`) — RBAC: users → roles → table-level read/write permissions, with approval workflow.
8. **系统管理** (`system`, `aop`) — Operation logs (AOP) and settings.

Current state: actively developed. The backend has ~200 Java files organized under `com.mogu.data` (`auth`, `system`, `metadata`, `query`, `integration`, `quality`, `report`, `dashboard`, `common`, `config`, `aop`). Test coverage is currently low.
