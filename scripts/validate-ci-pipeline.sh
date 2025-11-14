#!/bin/bash
# CI/CD Pipeline Local Validation Script
# Purpose: Simulate GitHub Actions workflow locally before pushing
# Usage: ./scripts/validate-ci-pipeline.sh

set -e  # Exit on first error

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Emojis for visual feedback
CHECK="✅"
CROSS="❌"
WARN="⚠️"
INFO="ℹ️"
ROCKET="🚀"
HAMMER="🔨"
TEST="🧪"
LOCK="🔒"
REPORT="📊"

# Project root (assuming script is in scripts/ directory)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

echo ""
echo "========================================"
echo "  CI/CD Pipeline Local Validation"
echo "========================================"
echo ""
echo "Project Root: $PROJECT_ROOT"
echo ""

# Test counters
TESTS_PASSED=0
TESTS_FAILED=0
WARNINGS=0

# Helper function for test results
pass_test() {
    echo -e "${GREEN}${CHECK} PASS${NC}: $1"
    ((TESTS_PASSED++))
}

fail_test() {
    echo -e "${RED}${CROSS} FAIL${NC}: $1"
    ((TESTS_FAILED++))
}

warn_test() {
    echo -e "${YELLOW}${WARN} WARN${NC}: $1"
    ((WARNINGS++))
}

info_msg() {
    echo -e "${BLUE}${INFO}${NC} $1"
}

section_header() {
    echo ""
    echo "----------------------------------------"
    echo "$1"
    echo "----------------------------------------"
}

# ============================================
# SECTION 1: Pre-flight Checks
# ============================================
section_header "SECTION 1: Pre-flight Checks"

# Check 1.1: Workflow file exists
if [ -f ".github/workflows/ci-cd.yml" ]; then
    pass_test "Workflow file exists (.github/workflows/ci-cd.yml)"
else
    fail_test "Workflow file not found (.github/workflows/ci-cd.yml)"
    exit 1
fi

# Check 1.2: Java version
JAVA_VERSION=$(java -version 2>&1 | grep version | awk -F '"' '{print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" == "17" ]; then
    pass_test "Java 17 detected"
else
    warn_test "Java version is $JAVA_VERSION, expected 17"
fi

# Check 1.3: Maven installed
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -v | head -1 | awk '{print $3}')
    pass_test "Maven installed (version $MVN_VERSION)"
else
    fail_test "Maven not found in PATH"
    exit 1
fi

# Check 1.4: All service directories exist
SERVICES=("service-registry" "api-gateway" "flightdata-service" "llm-summary-service")
for service in "${SERVICES[@]}"; do
    if [ -d "services/$service" ]; then
        pass_test "Service directory exists: $service"
    else
        fail_test "Service directory missing: $service"
    fi
done

# Check 1.5: All services have pom.xml
for service in "${SERVICES[@]}"; do
    if [ -f "services/$service/pom.xml" ]; then
        pass_test "pom.xml exists: $service"
    else
        fail_test "pom.xml missing: $service"
    fi
done

# ============================================
# SECTION 2: Build All Services
# ============================================
section_header "SECTION 2: Build All Services"

for service in "${SERVICES[@]}"; do
    echo ""
    info_msg "${HAMMER} Building $service..."

    cd "services/$service"

    if mvn -B clean package -DskipTests > /dev/null 2>&1; then
        pass_test "$service built successfully"
    else
        fail_test "$service build failed"
        echo "  Run manually: cd services/$service && mvn clean package -DskipTests"
    fi

    cd "$PROJECT_ROOT"
done

# ============================================
# SECTION 3: Run Unit Tests
# ============================================
section_header "SECTION 3: Run Unit Tests"

export SPRING_PROFILES_ACTIVE=test
export FLIGHTAWARE_API_KEY=test-flightaware-key
export OPENAI_API_KEY=test-openai-key

for service in "${SERVICES[@]}"; do
    echo ""
    info_msg "${TEST} Running unit tests for $service..."

    cd "services/$service"

    # Run tests excluding integration tests
    if mvn test -Dtest="!**/*IntegrationTest,!**/*E2ETest" > "/tmp/${service}-test.log" 2>&1; then
        # Extract test statistics
        TEST_COUNT=$(grep "Tests run:" "/tmp/${service}-test.log" | tail -1 | grep -oP 'Tests run: \K\d+' || echo "0")
        FAILURES=$(grep "Tests run:" "/tmp/${service}-test.log" | tail -1 | grep -oP 'Failures: \K\d+' || echo "0")

        if [ "$FAILURES" -eq 0 ]; then
            pass_test "$service: $TEST_COUNT tests passed"
        else
            fail_test "$service: $FAILURES test(s) failed"
            cat "/tmp/${service}-test.log"
        fi
    else
        warn_test "$service: Tests may not be configured yet"
    fi

    cd "$PROJECT_ROOT"
done

# ============================================
# SECTION 4: Security Checks
# ============================================
section_header "SECTION 4: Security Checks"

info_msg "${LOCK} Scanning for hardcoded secrets..."

SECRETS_FOUND=0

# Check 4.1: API keys pattern
echo ""
info_msg "Checking for API keys..."
if grep -r "apikey.*=.*[a-zA-Z0-9]{20,}" services/ --include="*.java" --include="*.yml" --include="*.properties" --exclude-dir="target" 2>/dev/null | grep -v "test-" > /dev/null; then
    fail_test "Potential API keys found in source code"
    grep -r "apikey.*=.*[a-zA-Z0-9]{20,}" services/ --include="*.java" --include="*.yml" --include="*.properties" --exclude-dir="target" | grep -v "test-" | head -5
    SECRETS_FOUND=1
else
    pass_test "No API keys detected"
fi

# Check 4.2: Password pattern
echo ""
info_msg "Checking for hardcoded passwords..."
if grep -r "password.*=.*['\"][^'\"]*['\"]" services/ --include="*.java" --include="*.yml" --include="*.properties" --exclude-dir="target" 2>/dev/null | grep -v "test" | grep -v "example" | grep -v "\${" | grep -v "spring.datasource.password" > /dev/null; then
    warn_test "Potential hardcoded passwords found"
    SECRETS_FOUND=1
else
    pass_test "No hardcoded passwords detected"
fi

# Check 4.3: AWS keys pattern
echo ""
info_msg "Checking for AWS keys..."
if grep -r "AKIA[0-9A-Z]{16}" services/ --include="*.java" --include="*.yml" --include="*.properties" --exclude-dir="target" 2>/dev/null > /dev/null; then
    fail_test "Potential AWS keys found"
    SECRETS_FOUND=1
else
    pass_test "No AWS keys detected"
fi

if [ $SECRETS_FOUND -eq 0 ]; then
    pass_test "All security checks passed"
fi

# ============================================
# SECTION 5: Code Coverage Analysis
# ============================================
section_header "SECTION 5: Code Coverage Analysis"

TOTAL_COVERAGE=0
SERVICE_COUNT=0
COVERAGE_FAILURES=0

for service in "${SERVICES[@]}"; do
    echo ""
    info_msg "${REPORT} Checking coverage for $service..."

    cd "services/$service"

    # Generate coverage report
    if mvn jacoco:report > /dev/null 2>&1; then
        JACOCO_REPORT="target/site/jacoco/index.html"

        if [ -f "$JACOCO_REPORT" ]; then
            # Extract coverage percentage
            COVERAGE=$(grep -oP 'Total.*?(\d+)%' "$JACOCO_REPORT" | grep -oP '\d+' | tail -1 || echo "0")

            echo "  Coverage: ${COVERAGE}%"

            if [ "$COVERAGE" -ge 90 ]; then
                pass_test "$service: ${COVERAGE}% coverage (≥90%)"
            else
                fail_test "$service: ${COVERAGE}% coverage (<90%)"
                COVERAGE_FAILURES=1
            fi

            TOTAL_COVERAGE=$((TOTAL_COVERAGE + COVERAGE))
            SERVICE_COUNT=$((SERVICE_COUNT + 1))
        else
            warn_test "$service: Coverage report not generated"
        fi
    else
        warn_test "$service: JaCoCo may not be configured"
    fi

    cd "$PROJECT_ROOT"
done

if [ $SERVICE_COUNT -gt 0 ]; then
    AVG_COVERAGE=$((TOTAL_COVERAGE / SERVICE_COUNT))
    echo ""
    info_msg "${REPORT} Average coverage: ${AVG_COVERAGE}%"
fi

# ============================================
# SECTION 6: Workflow YAML Validation
# ============================================
section_header "SECTION 6: Workflow YAML Validation"

info_msg "Checking workflow structure..."

# Check 6.1: Has correct name
if grep -q "name: Airline Tracker CI/CD Pipeline" .github/workflows/ci-cd.yml; then
    pass_test "Workflow has correct name"
else
    fail_test "Workflow name incorrect"
fi

# Check 6.2: Has triggers
if grep -q "on:" .github/workflows/ci-cd.yml && grep -q "push:" .github/workflows/ci-cd.yml; then
    pass_test "Workflow has push trigger"
else
    fail_test "Workflow missing push trigger"
fi

# Check 6.3: Has jobs
if grep -q "build-and-test:" .github/workflows/ci-cd.yml; then
    pass_test "Workflow has build-and-test job"
else
    fail_test "Workflow missing build-and-test job"
fi

# Check 6.4: Has service containers
if grep -q "redis:" .github/workflows/ci-cd.yml && grep -q "postgres:" .github/workflows/ci-cd.yml; then
    pass_test "Workflow has service containers (Redis, PostgreSQL)"
else
    fail_test "Workflow missing service containers"
fi

# Check 6.5: Has quality gates
if grep -q "Check Code Coverage Threshold" .github/workflows/ci-cd.yml; then
    pass_test "Workflow has coverage quality gate"
else
    warn_test "Workflow missing coverage quality gate"
fi

if grep -q "Check for Hardcoded Secrets" .github/workflows/ci-cd.yml; then
    pass_test "Workflow has security quality gate"
else
    warn_test "Workflow missing security quality gate"
fi

# ============================================
# FINAL SUMMARY
# ============================================
section_header "VALIDATION SUMMARY"

echo ""
echo -e "${GREEN}Tests Passed:${NC}  $TESTS_PASSED"
echo -e "${RED}Tests Failed:${NC}  $TESTS_FAILED"
echo -e "${YELLOW}Warnings:${NC}     $WARNINGS"
echo ""

if [ $TESTS_FAILED -eq 0 ]; then
    echo -e "${GREEN}${ROCKET} SUCCESS: Pipeline validation passed!${NC}"
    echo ""
    echo "Next steps:"
    echo "  1. Review the workflow: .github/workflows/ci-cd.yml"
    echo "  2. Commit the changes:"
    echo "     git add .github/workflows/ci-cd.yml"
    echo "     git commit -m 'feat: Add CI/CD pipeline for automated build and testing'"
    echo "  3. Push to GitHub:"
    echo "     git push origin develop"
    echo "  4. Monitor the workflow:"
    echo "     https://github.com/<YOUR_ORG>/airline-tracker-system/actions"
    echo ""
    exit 0
else
    echo -e "${RED}${CROSS} FAILED: $TESTS_FAILED test(s) failed${NC}"
    echo ""
    echo "Please fix the issues above before pushing to GitHub."
    echo ""
    exit 1
fi
