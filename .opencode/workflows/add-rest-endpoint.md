---
workflow_name: add-rest-endpoint
task_types: [add_endpoint, add_api, create_controller]
languages: [java]
frameworks: [spring-boot]
priority: 100
---

# Add REST Endpoint Workflow (Collateral Module)

## Prerequisites
- Spring Boot application context
- Endpoint specification from user
- Service layer exists (or will be created)

## Step 1: Clarify Requirements
**Action**: Ask user for endpoint details if not provided
**Required Info**:
  - HTTP method (GET/POST/PUT/DELETE)
  - URL path (e.g., `/api/v1/collateral/{id}`)
  - Request body structure (if POST/PUT)
  - Response body structure
  - Business logic requirements

## Step 2: Identify or Create Controller
**Action**: Find existing controller or determine where to create new one
**Tool**: grep
**Search Pattern**: `@RestController.*Collateral`
**Decision Logic**:
  - If controller exists → Add method to existing controller
  - If no controller → Create new controller file

## Step 3: Design Endpoint Method
**Action**: Create method signature
**Pattern**: rest-endpoint-structure (from PatternForge)
**Template**: |
  @GetMapping("/{id}")
  public ResponseEntity<ApiResponse<CollateralDto>> getCollateral(
      @PathVariable("id") Long collateralId
  ) {
      // implementation
  }
**Standards**:
  - Use `Objects.nonNull()` for null checks
  - Use `ClockSupplier` for timestamps
  - Use full exception variable names
  - Never use `var` keyword

## Step 4: Implement Service Layer Call
**Action**: Add service method invocation
**Pattern**: service-layer-separation
**Logic**:
  - Controller ONLY handles HTTP concerns
  - Delegate business logic to service layer
  - Use `@RequiredArgsConstructor` for dependency injection

## Step 5: Add Input Validation
**Action**: Add validation annotations
**Pattern**: input-validation-spring
**Annotations**:
  - `@Valid` on request body
  - `@NotNull`, `@NotBlank`, etc. in DTO
  - Custom validators if needed

## Step 6: Add Exception Handling
**Action**: Ensure proper exception handling
**Pattern**: exception-handling-spring
**Logic**:
  - Use `@ControllerAdvice` for global exception handling
  - Return consistent error response format
  - Log exceptions with full variable names

## Step 7: Write Unit Tests
**Action**: Create controller unit tests
**Tool**: invoke_agent
**Agent**: @java-test-fixer
**Test Requirements**:
  - Test happy path
  - Test validation failures
  - Test error scenarios
  - Use `@WebMvcTest` for controller tests
  - Mock service layer

## Step 8: Code Review (MANDATORY)
**Action**: Invoke Java code reviewer
**Tool**: invoke_agent
**Agent**: @java-code-reviewer
**Wait**: true  # Block until user approves
**Validation**: Review passes with no critical issues

## Step 9: Maven Clean Build (MANDATORY)
**Action**: Run Maven clean build
**Tool**: bash
**Command**: export JAVA_HOME=$(/usr/libexec/java_home -v 11) && mvn clean compile
**Validation**: Exit code 0
**Rationale**: Lombok and jOOQ require clean rebuild

## Step 10: Run Tests (Verification)
**Action**: Run all tests to ensure no regressions
**Tool**: bash
**Command**: export JAVA_HOME=$(/usr/libexec/java_home -v 11) && mvn test
**Validation**: All tests pass

## Quality Gates
- code_review: MANDATORY, blocking=true
- user_approval: MANDATORY, blocking=true
- mvn_clean: MANDATORY, blocking=false
- mvn_test: MANDATORY, blocking=false
- test_coverage: RECOMMENDED, blocking=false

## Pattern References
- rest-endpoint-structure
- service-layer-separation
- input-validation-spring
- exception-handling-spring
- objects-nonnull-check
- clocksupplier-for-time
- java-test-annotations

## Common Pitfalls to Avoid
1. ❌ Using `var` keyword → Use explicit types
2. ❌ Using `!= null` → Use `Objects.nonNull()`
3. ❌ Short exception names (e.g., `e`) → Use full names (e.g., `ioException`)
4. ❌ Skipping Maven clean → Lombok/jOOQ won't generate code
5. ❌ Business logic in controller → Move to service layer
6. ❌ Inconsistent response format → Use `ApiResponse<T>` wrapper
