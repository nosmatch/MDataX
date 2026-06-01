# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

MDataX is a lightweight data management platform (数据管理平台). It is a B端 (enterprise) web platform for data integration, development, asset management, querying, quality monitoring, and access control. The product language is Chinese.

Architecture: MySQL sources → data integration → ClickHouse storage → SQL development / data query, with RBAC permission control. The backend is a Spring Boot application (~200 Java files) with a Vue 3 frontend under `mdatax-web/`.

## Build & Development

This is a Spring Boot 2.7 / Java 8 Maven project. The frontend lives in `mdatax-web/` (Vue 3 + Vite).

```bash
# Backend: compile / run / package
mvn compile
mvn spring-boot:run            # main class: com.mogu.data.MDataXApplication
mvn package

# Frontend (in mdatax-web/)
npm install
npm run dev
npm run build
```

Java 8 is the target and source version. The active Spring profile is `dev` (`application-dev.yml`).

## Code Conventions

- All Java source files must include an `@author fengzhu` annotation in the class-level Javadoc.
- Package root: `com.mogu.data`

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

Current state: actively developed. The backend has ~200 Java files organized under `com.mogu.data` (`auth`, `system`, `metadata`, `query`, `integration`, `quality`, `report`, `dashboard`, `common`, `config`, `aop`). The Spring Boot entry point is `MDataXApplication`. Test coverage is currently low.
