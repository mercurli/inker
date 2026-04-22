# AGENTS.md - Development Guidelines for Inker

## Project Overview
- **Project Name**: Inker (研墨)
- **Type**: Full-stack web application (Vue/Vite frontend + Java/SpringBoot backend)
- **Purpose**: Stock market fundamental analysis tool for automated financial screening, DuPont analysis, and global market comparison

---

## Build Commands

### Frontend (Vue/Vite)
```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Run linting
npm run lint

# Run type checking
npm run type-check
```

### Backend (Java/SpringBoot)
```bash
# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=TestClassName

# Run a single test method
./mvnw test -Dtest=TestClassName#testMethodName
```

---

## Running Single Tests

### Frontend (Vitest)
```bash
# Run a single test file
npm run test -- tests/unit/MyComponent.test.ts

# Run tests matching a pattern
npm run test -- --grep "pattern"

# Run in watch mode
npm run test -- --watch
```

### Backend (JUnit)
```bash
# Single test class
./mvnw test -Dtest=MyServiceTest

# Single test method
./mvnw test -Dtest=MyServiceTest#testMethodName
```
