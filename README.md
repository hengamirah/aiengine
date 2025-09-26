# AI Engine Spring Boot Application

A Spring Boot application for AI name and ID processing and management.

## Overview

This project is a Spring Boot application that provides AI services and features. It uses a modern tech stack including Spring Security, JPA, and multiple database integrations.

## Technology Stack

- **Java 17**
- **Spring Boot 3.5.5**
- **Spring Security** (with authentication disabled)
- **Spring Data JPA**
- **MyBatis**
- **Thymeleaf** for template rendering
- **Databases**:
  - PostgreSQL
  - H2 (for development)
  - Oracle

## Getting Started

### Prerequisites

- JDK 17 or later
- Maven 3.6+
- Your preferred IDE (IntelliJ IDEA, Eclipse, VS Code)

### Setup and Installation

1. **Clone the repository**:
   ```bash
   git clone [repository-url]
   cd aiengine
   ```

2. **Build the project**:
   ```bash
   mvn clean install
   ```

3. **Run the application**:
   ```bash
   mvn spring-boot:run
   ```

   The application will start at `http://localhost:8080`

4.  **To enable debug logging, run:**
    ```
    $env:NAME_VERIF_DEBUG='true'; mvn spring-boot:run
    ```
### Configuration

The application can be configured through `application.properties`  in the `src/main/resources` directory.

#### Database Configuration

Example database configuration:

```properties

# Or Oracle
# spring.datasource.url=jdbc:oracle:thin:@localhost:1521:XE
# spring.datasource.username=system
# spring.datasource.password=password
```

## API Reference

Document your API endpoints here once they're available.

## Development

### Testing

Run tests with:

```bash
mvn test
```

### Building for Production

```bash
mvn clean package -P production
```

## License

[Your License Information]

## Contributing

Instructions for contributors would go here.
