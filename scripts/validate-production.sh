#!/bin/bash

# ============================================
# PRODUCTION VALIDATION SCRIPT (Bash)
# ============================================
# Tests the complete Airline Tracking System end-to-end
#
# Requirements:
# - Docker Compose services running (docker-compose up -d)
# - curl command available
# - jq command available (for JSON parsing)
#
# Usage:
#   ./scripts/validate-production.sh
#
# Exit Codes:
#   0 = All tests passed
#   1 = One or more tests failed

set -o pipefail

# Configuration
TEST_FLIGHT="${1:-UAL123}"
TIMEOUT_SECONDS="${2:-60}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# Test counters
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0

# Test results array
declare -a TEST_RESULTS

# ============================================
# HELPER FUNCTIONS
# ============================================

print_header() {
    echo -e "\n${CYAN}========================================${NC}"
    echo -e "${CYAN}  $1${NC}"
    echo -e "${CYAN}========================================${NC}\n"
}

print_test_step() {
    echo -e "${CYAN}[TEST] $1${NC}"
}

print_success() {
    echo -e "${GREEN}✅ $1${NC}"
}

print_failure() {
    echo -e "${RED}❌ $1${NC}"
}

print_warning() {
    echo -e "${YELLOW}⚠️  $1${NC}"
}

print_info() {
    echo -e "${CYAN}$1${NC}"
}

test_assertion() {
    local test_name="$1"
    local condition="$2"
    local success_msg="$3"
    local failure_msg="$4"

    ((TOTAL_TESTS++))

    if eval "$condition"; then
        ((PASSED_TESTS++))
        print_success "$test_name - $success_msg"
        TEST_RESULTS+=("PASS:$test_name:$success_msg")
        return 0
    else
        ((FAILED_TESTS++))
        print_failure "$test_name - $failure_msg"
        TEST_RESULTS+=("FAIL:$test_name:$failure_msg")
        return 1
    fi
}

check_dependencies() {
    if ! command -v curl &> /dev/null; then
        print_failure "curl command not found. Please install curl."
        exit 1
    fi

    if ! command -v jq &> /dev/null; then
        print_warning "jq command not found. JSON parsing will be limited."
        print_warning "Install jq for better output: sudo apt-get install jq (Ubuntu) or brew install jq (Mac)"
    fi
}

http_get() {
    local url="$1"
    local expected_code="${2:-200}"
    local timeout="${3:-10}"

    response=$(curl -s -w "\n%{http_code}" --max-time "$timeout" "$url" 2>/dev/null)
    http_code=$(echo "$response" | tail -n1)
    body=$(echo "$response" | sed '$d')

    if [ "$http_code" = "$expected_code" ]; then
        echo "$body"
        return 0
    else
        return 1
    fi
}

# ============================================
# MAIN SCRIPT
# ============================================

print_header "AIRLINE TRACKING SYSTEM - PRODUCTION VALIDATION"
print_info "Test Flight: $TEST_FLIGHT"
print_info "Timeout: $TIMEOUT_SECONDS seconds"
print_info "Timestamp: $(date '+%Y-%m-%d %H:%M:%S')\n"

check_dependencies

# ============================================
# PHASE 1: INFRASTRUCTURE HEALTH CHECKS
# ============================================

print_header "PHASE 1: Infrastructure Health Checks"

# Test 1.1: Service Registry (Eureka)
test_assertion "Eureka Server" \
    "http_get 'http://localhost:8761/actuator/health' 200 10 >/dev/null" \
    "Eureka Server is UP (Port 8761)" \
    "Eureka Server is DOWN or unreachable"

# Test 1.2: API Gateway
test_assertion "API Gateway" \
    "http_get 'http://localhost:8080/actuator/health' 200 10 >/dev/null" \
    "API Gateway is UP (Port 8080)" \
    "API Gateway is DOWN or unreachable"

# Test 1.3: FlightData Service
test_assertion "FlightData Service" \
    "http_get 'http://localhost:8081/actuator/health' 200 10 >/dev/null" \
    "FlightData Service is UP (Port 8081)" \
    "FlightData Service is DOWN or unreachable"

# Test 1.4: LLM Summary Service
test_assertion "LLM Summary Service" \
    "http_get 'http://localhost:8082/actuator/health' 200 10 >/dev/null" \
    "LLM Summary Service is UP (Port 8082)" \
    "LLM Summary Service is DOWN or unreachable"

# Test 1.5: Prometheus Monitoring
test_assertion "Prometheus" \
    "http_get 'http://localhost:9090/-/healthy' 200 10 >/dev/null" \
    "Prometheus is UP (Port 9090)" \
    "Prometheus is DOWN or unreachable"

# ============================================
# PHASE 2: SERVICE DISCOVERY
# ============================================

print_header "PHASE 2: Service Discovery Verification"

# Test 2.1: All services registered with Eureka
health_response=$(http_get "http://localhost:8080/actuator/health" 200 10)
if [ $? -eq 0 ] && command -v jq &> /dev/null; then
    flightdata_count=$(echo "$health_response" | jq -r '.components.discoveryComposite.components.eureka.details.applications."FLIGHTDATA-SERVICE" // 0')
    llm_count=$(echo "$health_response" | jq -r '.components.discoveryComposite.components.eureka.details.applications."LLM-SUMMARY-SERVICE" // 0')
    gateway_count=$(echo "$health_response" | jq -r '.components.discoveryComposite.components.eureka.details.applications."API-GATEWAY" // 0')

    test_assertion "Service Registration" \
        "[ $flightdata_count -ge 1 ] && [ $llm_count -ge 1 ] && [ $gateway_count -ge 1 ]" \
        "All 3 services registered with Eureka" \
        "Services not properly registered with Eureka"
else
    print_warning "Skipping service registration test (jq not available or health check failed)"
fi

# ============================================
# PHASE 3: END-TO-END FLOW TEST
# ============================================

print_header "PHASE 3: End-to-End Flow Test"

print_info "Testing complete flow:"
print_info "  1. API Gateway → FlightData Service"
print_info "  2. FlightAware API call (or cache)"
print_info "  3. Redis caching"
print_info "  4. Kafka event publishing"
print_info "  5. LLM Summary Service (async)"
print_info "  6. OpenAI summary generation"
print_info "  7. PostgreSQL persistence\n"

# Test 3.1: Get Flight Data (via API Gateway)
print_test_step "Step 1: Requesting flight data for $TEST_FLIGHT..."
flight_url="http://localhost:8080/api/v1/flight/$TEST_FLIGHT"
flight_data=$(http_get "$flight_url" 200 15)

test_assertion "Flight Data Retrieval" \
    "[ -n '$flight_data' ]" \
    "Successfully retrieved flight data for $TEST_FLIGHT" \
    "Failed to retrieve flight data"

# Test 3.2: Verify Flight Data Structure
if [ -n "$flight_data" ] && command -v jq &> /dev/null; then
    ident=$(echo "$flight_data" | jq -r '.ident // empty')
    fa_flight_id=$(echo "$flight_data" | jq -r '.fa_flight_id // empty')
    status=$(echo "$flight_data" | jq -r '.status // empty')

    test_assertion "Flight Data - ident field" \
        "[ '$ident' = '$TEST_FLIGHT' ]" \
        "Flight ident matches: $ident" \
        "Flight ident mismatch or missing"

    test_assertion "Flight Data - fa_flight_id field" \
        "[ -n '$fa_flight_id' ]" \
        "fa_flight_id present: $fa_flight_id" \
        "fa_flight_id missing or empty"

    test_assertion "Flight Data - status field" \
        "[ -n '$status' ]" \
        "Status present: $status" \
        "Status missing or empty"
fi

# Test 3.3: Wait for Async Processing (Kafka → LLM Service)
print_test_step "Step 2: Waiting for Kafka event processing (max $TIMEOUT_SECONDS seconds)..."
echo -e "${WHITE}  (FlightData Service publishes → Kafka → LLM Summary Service consumes)${NC}"

sleep 5  # Initial wait for Kafka processing

# Test 3.4: Retrieve LLM Summary (with retry logic)
print_test_step "Step 3: Requesting AI-generated summary..."

summary_retrieved=false
max_attempts=12  # 12 attempts * 5 seconds = 60 seconds
attempt=0

summary_url="http://localhost:8080/api/v1/flight/$TEST_FLIGHT/summary"

while [ "$summary_retrieved" = false ] && [ $attempt -lt $max_attempts ]; do
    ((attempt++))
    echo -e "${WHITE}  Attempt $attempt/$max_attempts...${NC}"

    summary_data=$(http_get "$summary_url" 200 10)

    if [ $? -eq 0 ] && [ -n "$summary_data" ]; then
        summary_retrieved=true

        test_assertion "LLM Summary Retrieval" \
            "true" \
            "Successfully retrieved AI summary after $attempt attempts" \
            "N/A"

        if command -v jq &> /dev/null; then
            summary_ident=$(echo "$summary_data" | jq -r '.ident // empty')
            summary_text=$(echo "$summary_data" | jq -r '.summary_text // empty')
            generated_at=$(echo "$summary_data" | jq -r '.generated_at // empty')
            text_length=${#summary_text}

            test_assertion "Summary - ident field" \
                "[ '$summary_ident' = '$TEST_FLIGHT' ]" \
                "Summary ident matches: $summary_ident" \
                "Summary ident mismatch"

            test_assertion "Summary - summary_text field" \
                "[ $text_length -gt 20 ]" \
                "Summary text present ($text_length chars)" \
                "Summary text missing or too short"

            test_assertion "Summary - generated_at field" \
                "[ -n '$generated_at' ]" \
                "Timestamp present: $generated_at" \
                "Timestamp missing"

            echo -e "\n${CYAN}📝 Generated Summary:${NC}"
            echo -e "${WHITE}  $summary_text${NC}\n"
        fi

        break
    else
        if [ $attempt -lt $max_attempts ]; then
            sleep 5
        fi
    fi
done

if [ "$summary_retrieved" = false ]; then
    test_assertion "LLM Summary Retrieval" \
        "false" \
        "N/A" \
        "Summary not generated within $TIMEOUT_SECONDS seconds (async processing issue)"
fi

# ============================================
# PHASE 4: CACHING VERIFICATION
# ============================================

print_header "PHASE 4: Redis Caching Verification"

print_test_step "Testing cache hit (second request should be faster)..."

# Measure first request (should hit cache now)
start_time=$(date +%s%3N)
cached_result1=$(http_get "$flight_url" 200 10)
end_time=$(date +%s%3N)
duration1=$((end_time - start_time))

# Measure second request (definitely from cache)
sleep 0.1
start_time=$(date +%s%3N)
cached_result2=$(http_get "$flight_url" 200 10)
end_time=$(date +%s%3N)
duration2=$((end_time - start_time))

test_assertion "Cache Performance" \
    "[ -n '$cached_result1' ] && [ -n '$cached_result2' ] && [ $duration2 -lt 500 ]" \
    "Cache hit confirmed (Request: ${duration1}ms, Cached: ${duration2}ms)" \
    "Caching not working as expected"

# ============================================
# PHASE 5: MONITORING & METRICS
# ============================================

print_header "PHASE 5: Monitoring & Metrics"

# Test 5.1: Prometheus Targets
targets_response=$(http_get "http://localhost:9090/api/v1/targets" 200 10)
if [ $? -eq 0 ] && command -v jq &> /dev/null; then
    up_targets=$(echo "$targets_response" | jq '[.data.activeTargets[] | select(.health=="up")] | length')

    test_assertion "Prometheus Targets" \
        "[ $up_targets -ge 4 ]" \
        "Prometheus monitoring active ($up_targets targets UP)" \
        "Prometheus targets not healthy"
else
    print_warning "Skipping Prometheus targets test (jq not available or request failed)"
fi

# ============================================
# FINAL REPORT
# ============================================

print_header "VALIDATION REPORT"

success_rate=$(awk "BEGIN {printf \"%.2f\", ($PASSED_TESTS / $TOTAL_TESTS) * 100}")

print_info "Total Tests: $TOTAL_TESTS"
print_success "Passed:      $PASSED_TESTS ✅"
if [ $FAILED_TESTS -eq 0 ]; then
    print_success "Failed:      $FAILED_TESTS ❌"
else
    print_failure "Failed:      $FAILED_TESTS ❌"
fi
print_info "Success Rate: ${success_rate}%\n"

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}  ✅ ALL TESTS PASSED!${NC}"
    echo -e "${GREEN}  System is fully operational.${NC}"
    echo -e "${GREEN}========================================${NC}\n"
    exit 0
else
    echo -e "${RED}========================================${NC}"
    echo -e "${RED}  ❌ SOME TESTS FAILED${NC}"
    echo -e "${RED}  Review errors above for details.${NC}"
    echo -e "${RED}========================================${NC}\n"

    echo -e "\n${RED}Failed Tests:${NC}"
    for result in "${TEST_RESULTS[@]}"; do
        if [[ $result == FAIL:* ]]; then
            test_name=$(echo "$result" | cut -d: -f2)
            message=$(echo "$result" | cut -d: -f3-)
            echo -e "${RED}  - $test_name: $message${NC}"
        fi
    done
    echo ""

    exit 1
fi
