# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Inker (研墨) is a stock market fundamental analysis tool for automated financial screening, DuPont analysis, and global market comparison. It's a full-stack application with a Vue/Vite frontend and Java/Spring Boot backend.

## Development Commands

### Frontend (from `frontend/` directory)
```bash
npm install          # Install dependencies
npm run dev          # Start dev server
npm run build        # Build for production (runs type-check first)
npm run preview      # Preview production build
```

Note: The frontend currently has no linting or testing configured in package.json.

### Backend (from `backend/` directory)
```bash
./mvnw clean package              # Build project
./mvnw spring-boot:run            # Run application
./mvnw test                       # Run all tests
./mvnw test -Dtest=ClassName      # Run single test class
./mvnw test -Dtest=ClassName#methodName  # Run single test method
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

## Architecture

### Backend Structure
- **Package**: `com.inker.backend`
- **Entry point**: `InkerApplication.java` (enables scheduling with `@EnableScheduling`)
- **Controller layer**: REST endpoints under `/api/v1` (e.g., `StockController`)
- **Service layer**: Business logic split into focused services:
  - `StockImportService` - imports stock data from providers
  - `StockQueryService` - queries and retrieves stock data
  - `StockBootstrapService` - initialization logic
- **Provider pattern**: `StockProvider` interface with implementations like `EastMoneyStockProvider` for external data sources
- **Repository layer**: Spring Data JPA repositories
- **Scheduler**: `StockSyncScheduler` for automated data synchronization
- **Database**: Supports both H2 (dev) and MySQL (production)
- **Tech stack**: Spring Boot 3.2.0, Java 17, JPA, Lombok, Bean Validation

### Frontend Structure
- **Framework**: Vue 3 with Composition API (`<script setup>`)
- **Build tool**: Vite
- **Language**: TypeScript (strict mode)
- **HTTP client**: Axios
- **Entry point**: `main.ts`
- Currently minimal structure - expected directories per AGENTS.md:
  - `src/api/` - API calls
  - `src/components/` - Vue components
  - `src/composables/` - Vue composables
  - `src/router/` - Vue Router config
  - `src/stores/` - Pinia stores
  - `src/types/` - TypeScript types
  - `src/views/` - Page components

### Key Business Logic
- Stock import filters out ST stocks (special treatment) and Beijing Stock Exchange listings
- Only imports stocks from SSE (Shanghai) and SZSE (Shenzhen) exchanges
- Uses provider pattern to abstract external data sources (currently EastMoneyStockProvider)

## Code Style

### Vue/TypeScript
- Use `<script setup>` syntax with Composition API
- Organize script sections: imports → types → props → emits → state → computed → methods → lifecycle
- Component names: PascalCase
- Composables: camelCase with `use` prefix
- Use absolute imports with `@/` alias
- Avoid `any` type

### Java/Spring Boot
- Follow standard Java conventions (PascalCase classes, camelCase methods)
- Class member order: constants → fields → constructors → public methods → private methods
- Use Lombok to reduce boilerplate
- Use constructor injection (not `@Autowired` fields)
- REST endpoints follow `/api/v1/` prefix convention
- Implement proper error handling with meaningful messages
