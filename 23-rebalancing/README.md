# Challenge 23: rebalancing

## Description
A new Kotlin Spring Boot challenge

## Getting Started

### Project Structure
```
23-rebalancing/
├── src/
│   ├── main/
│   │   ├── kotlin/com/example/     # Your Kotlin source files go here
│   │   └── resources/
│   │       └── application.yml     # Application configuration
│   └── test/
│       ├── kotlin/com/example/     # Your test files go here
│       └── resources/
│           └── application-test.yml # Test configuration
├── build.gradle.kts                # Module-specific dependencies
└── README.md                       # This file
```

### Implementation Steps
1. Create your main Spring Boot application class in `src/main/kotlin/com/example/`
2. Implement your controllers, services, and other components
3. Add any required dependencies to `build.gradle.kts`
4. Write tests in `src/test/kotlin/com/example/`

### Run the Application
```bash
./gradlew :23-rebalancing:run
```

### Test the Application
```bash
# Run all tests
./gradlew :23-rebalancing:test

# Build the project
./gradlew :23-rebalancing:build
```

## Challenge Tasks

### Basic Requirements
- [ ] Create main Spring Boot application class
- [ ] Implement the core functionality
- [ ] Add proper error handling
- [ ] Write comprehensive tests
- [ ] Add API documentation

### Advanced Requirements
- [ ] Add input validation
- [ ] Implement logging
- [ ] Add metrics/monitoring
- [ ] Performance optimization

## Common Dependencies

Add these to your `build.gradle.kts` as needed:

```kotlin
dependencies {
}
```

## Notes
- Add your implementation notes here
- Document any assumptions or design decisions
- Include references to useful resources

## Resources
- [Kotlin Documentation](https://kotlinlang.org/docs/)
- [Spring Framework Reference](https://docs.spring.io/spring-framework/docs/current/reference/html/)
