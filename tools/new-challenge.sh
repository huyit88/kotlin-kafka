#!/bin/bash

# new-challenge.sh - Generate a new Kotlin Spring Boot challenge
# Usage: ./tools/new-challenge.sh <challenge-name> [description]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Function to print colored output
print_info() {
    echo -e "${BLUE}ℹ️  $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_error() {
    echo -e "${RED}❌ $1${NC}"
}

# Function to show usage
show_usage() {
    echo "Usage: $0 <challenge-name> [description]"
    echo ""
    echo "Examples:"
    echo "  $0 rest-api \"Create a REST API with CRUD operations\""
    echo "  $0 database-integration \"Integrate with PostgreSQL database\""
    echo "  $0 security \"Implement Spring Security with JWT\""
    echo ""
    echo "The script will:"
    echo "  - Find the next available challenge number"
    echo "  - Create directory structure: XX-challenge-name/"
    echo "  - Create basic build.gradle.kts"
    echo "  - Create a README with challenge description"
    echo "  - Update project to include the new challenge"
}

# Check if we're in the right directory
if [[ ! -f "settings.gradle.kts" ]]; then
    print_error "This script must be run from the project root directory"
    exit 1
fi

# Check for help flag
if [[ "$1" == "--help" || "$1" == "-h" ]]; then
    show_usage
    exit 0
fi

# Check arguments
if [[ $# -lt 1 ]]; then
    print_error "Challenge name is required"
    show_usage
    exit 1
fi

CHALLENGE_NAME="$1"
DESCRIPTION="${2:-A new Kotlin Spring Boot challenge}"

# Validate challenge name (alphanumeric, hyphens, underscores only)
if [[ ! "$CHALLENGE_NAME" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    print_error "Challenge name can only contain letters, numbers, hyphens, and underscores"
    exit 1
fi

print_info "Creating new challenge: $CHALLENGE_NAME"

# Find the next challenge number
NEXT_NUM=1
for dir in */; do
    if [[ "$dir" =~ ^([0-9]{2})- ]]; then
        num=${BASH_REMATCH[1]}
        if (( 10#$num >= NEXT_NUM )); then
            NEXT_NUM=$((10#$num + 1))
        fi
    fi
done

# Format number with leading zero
CHALLENGE_NUM=$(printf "%02d" $NEXT_NUM)
CHALLENGE_DIR="${CHALLENGE_NUM}-${CHALLENGE_NAME}"

print_info "Next challenge number: $CHALLENGE_NUM"
print_info "Challenge directory: $CHALLENGE_DIR"

# Check if directory already exists
if [[ -d "$CHALLENGE_DIR" ]]; then
    print_error "Directory $CHALLENGE_DIR already exists"
    exit 1
fi

# Create directory structure
print_info "Creating directory structure..."
mkdir -p "$CHALLENGE_DIR/src/main/kotlin/com/example"
mkdir -p "$CHALLENGE_DIR/src/test/kotlin/com/example"
mkdir -p "$CHALLENGE_DIR/src/main/resources"
mkdir -p "$CHALLENGE_DIR/src/test/resources"

# Create build.gradle.kts (empty, inherits from parent)
cat > "$CHALLENGE_DIR/build.gradle.kts" << 'EOF'
// This module inherits configuration from the parent build.gradle.kts
// Add any module-specific dependencies or configurations here if needed

dependencies {
    // Add challenge-specific dependencies here
    // Example:
    // implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // implementation("org.postgresql:postgresql")
}
EOF

# Create basic application.yml
cat > "$CHALLENGE_DIR/src/main/resources/application.yml" << EOF
server:
  port: 8080

spring:
  application:
    name: $CHALLENGE_NAME

logging:
  level:
    com.example: DEBUG
EOF

# Create test application.yml
cat > "$CHALLENGE_DIR/src/test/resources/application-test.yml" << EOF
server:
  port: 0  # Use random port for tests

logging:
  level:
    com.example: DEBUG
    org.springframework.web: DEBUG
EOF

# Create README.md
cat > "$CHALLENGE_DIR/README.md" << EOF
# Challenge $CHALLENGE_NUM: $CHALLENGE_NAME

## Description
$DESCRIPTION

## Getting Started

### Project Structure
\`\`\`
$CHALLENGE_DIR/
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
\`\`\`

### Implementation Steps
1. Create your main Spring Boot application class in \`src/main/kotlin/com/example/\`
2. Implement your controllers, services, and other components
3. Add any required dependencies to \`build.gradle.kts\`
4. Write tests in \`src/test/kotlin/com/example/\`

### Run the Application
\`\`\`bash
./gradlew :$CHALLENGE_DIR:run
\`\`\`

### Test the Application
\`\`\`bash
# Run all tests
./gradlew :$CHALLENGE_DIR:test

# Build the project
./gradlew :$CHALLENGE_DIR:build
\`\`\`

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

Add these to your \`build.gradle.kts\` as needed:

\`\`\`kotlin
dependencies {
}
\`\`\`

## Notes
- Add your implementation notes here
- Document any assumptions or design decisions
- Include references to useful resources

## Resources
- [Kotlin Documentation](https://kotlinlang.org/docs/)
- [Spring Framework Reference](https://docs.spring.io/spring-framework/docs/current/reference/html/)
EOF

print_success "Challenge structure created successfully!"

# Print summary
echo ""
print_success "🎉 Challenge '$CHALLENGE_NAME' created successfully!"
echo ""
echo "📁 Location: $CHALLENGE_DIR/"
echo "📖 Read instructions: $CHALLENGE_DIR/README.md"
echo "🏗️  Add dependencies: $CHALLENGE_DIR/build.gradle.kts"
echo "💻 Write code: $CHALLENGE_DIR/src/main/kotlin/com/example/"
echo "🧪 Write tests: $CHALLENGE_DIR/src/test/kotlin/com/example/"
echo ""
print_info "The challenge is automatically included in the build thanks to settings.gradle.kts"
print_info "Start by creating your main Spring Boot application class!"
print_info "Run './gradlew :$CHALLENGE_DIR:bootRun' once you have a main class"
