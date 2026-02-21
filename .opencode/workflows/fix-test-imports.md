---
workflow_name: fix-test-imports
task_types: [fix_test, test_compilation_error]
languages: [java]
frameworks: [spring-boot, junit5]
priority: 100
---

# Fix Test Imports Workflow (Collateral Module)

## Prerequisites
- Java 11 environment
- Maven 3.9+
- Test file identified from error message

## Step 1: Read Test File
**Action**: Read the failing test file
**Tool**: read
**Target**: Extract filename from compilation error or user input
**Validation**: File exists and is a Java test file (.java in src/test/)

## Step 2: Identify Missing Imports
**Action**: Compare existing imports with required imports from compilation errors
**Pattern**: test-import-order (from PatternForge)
**Logic**:
  - Check for common collateral-module test imports:
    - `com.gsft.libraries.test.util.AfterTestExecution`
    - `com.gsft.libraries.test.util.TestAuthUtils`
    - `com.gsft.collateralmodule.api.*`
  - Verify import order: java.* → org.* → com.gsft.* → static imports

## Step 3: Add Missing Imports in Correct Position
**Action**: Insert imports using edit tool
**Tool**: edit
**Template**: |
  import com.gsft.libraries.test.util.AfterTestExecution;
  import com.gsft.libraries.test.util.TestAuthUtils;
**Position**: After `com.gsft.libraries.*` imports, before `Api*` imports
**Validation**: Import order matches checkstyle.xml LeftCurly rule

## Step 4: Code Review (MANDATORY)
**Action**: Invoke Java code reviewer
**Tool**: invoke_agent
**Agent**: @java-code-reviewer
**Wait**: true  # Block until user approves
**Validation**: Review passes with no critical issues

## Step 5: Maven Clean (MANDATORY for Lombok/jOOQ)
**Action**: Run Maven clean build
**Tool**: bash
**Command**: export JAVA_HOME=$(/usr/libexec/java_home -v 11) && mvn clean
**Validation**: Exit code 0
**Rationale**: Lombok and jOOQ require clean rebuild after changes

## Step 6: Run Tests (Verification)
**Action**: Run the specific test or test class
**Tool**: bash
**Command**: export JAVA_HOME=$(/usr/libexec/java_home -v 11) && mvn test -Dtest=TestClassName
**Validation**: Tests pass

## Quality Gates
- code_review: MANDATORY, blocking=true
- user_approval: MANDATORY, blocking=true
- mvn_clean: MANDATORY, blocking=false (can continue if fails)
- mvn_test: RECOMMENDED, blocking=false

## Pattern References
- test-import-order
- java-test-annotations
- clocksupplier-for-time (if test involves time)
- objects-nonnull-check (for null safety)
