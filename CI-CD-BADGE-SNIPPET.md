# GitHub Actions CI/CD Badge

## Badge Snippet for README.md

Add this badge at the top of your `README.md` file to show the CI/CD pipeline status:

```markdown
[![CI/CD Pipeline](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml)
```

## Customization Steps

1. **Replace `<YOUR_USERNAME>`** with your GitHub username or organization name
   - Example: If your repo is `https://github.com/johndoe/airline-tracker-system`
   - Use: `johndoe`

2. **Replace `airline-tracker-system`** if your repository has a different name
   - Match the exact repository name from GitHub

## Example

If your repository is at `https://github.com/acme-corp/airline-tracker-system`:

```markdown
[![CI/CD Pipeline](https://github.com/acme-corp/airline-tracker-system/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/acme-corp/airline-tracker-system/actions/workflows/ci-cd.yml)
```

## Badge Variants

### Show Specific Branch Status
```markdown
[![CI/CD Pipeline](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml/badge.svg?branch=main)](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml)
```

### Show Event Type (push only)
```markdown
[![CI/CD Pipeline](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml/badge.svg?event=push)](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml)
```

## Where to Place in README

**Option 1: At the very top (recommended)**
```markdown
# Airline Tracker System

[![CI/CD Pipeline](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml)

Real-time flight tracking and AI-powered summary generation system.
```

**Option 2: In a badges section**
```markdown
# Airline Tracker System

## Status

[![CI/CD Pipeline](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml)
[![Code Coverage](https://img.shields.io/badge/coverage-92.3%25-brightgreen)](./FINAL-TEST-RESULTS.md)
[![Java Version](https://img.shields.io/badge/java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot](https://img.shields.io/badge/spring--boot-3.x-green)](https://spring.io/projects/spring-boot)
```

## What the Badge Shows

- ✅ **Green checkmark (passing):** All tests passed, build successful
- ❌ **Red X (failing):** Tests failed or build broke
- ⚪ **Gray circle (no runs):** No workflow runs yet
- 🟡 **Yellow dot (in progress):** Workflow currently running

## Clicking the Badge

When clicked, the badge takes users to:
- The workflow runs page showing all CI/CD executions
- Recent run history with pass/fail status
- Detailed logs for each step

## Multiple Badge Example (Full Status Bar)

```markdown
[![CI/CD Pipeline](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml/badge.svg)](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions/workflows/ci-cd.yml)
[![License](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)
[![Java 17](https://img.shields.io/badge/java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.x](https://img.shields.io/badge/spring--boot-3.x-green)](https://spring.io/projects/spring-boot)
[![Code Coverage](https://img.shields.io/badge/coverage-92.3%25-brightgreen)](./FINAL-TEST-RESULTS.md)
```

## Badge Status Examples

### Passing Build
![passing](https://img.shields.io/badge/build-passing-brightgreen)

### Failing Build
![failing](https://img.shields.io/badge/build-failing-red)

### In Progress
![in progress](https://img.shields.io/badge/build-in%20progress-yellow)

## Troubleshooting

### Badge Shows "No Status"
- **Cause:** Workflow hasn't run yet
- **Fix:** Push a commit or manually trigger workflow via GitHub Actions UI

### Badge Shows Wrong Status
- **Cause:** Badge is cached
- **Fix:** Force refresh browser (Ctrl+F5) or wait 5-10 minutes

### Badge Link is Broken
- **Cause:** Incorrect repository path or workflow filename
- **Fix:** Verify:
  - Repository owner/name is correct
  - Workflow file is named exactly `ci-cd.yml`
  - Workflow is in `.github/workflows/` directory

## Advanced: Custom Badge with Shields.io

For more customization options:

```markdown
[![Build Status](https://img.shields.io/github/actions/workflow/status/<YOUR_USERNAME>/airline-tracker-system/ci-cd.yml?branch=main&label=build&logo=github)](https://github.com/<YOUR_USERNAME>/airline-tracker-system/actions)
```

Options:
- `label=build` - Custom label text
- `logo=github` - Add GitHub logo
- `branch=main` - Show specific branch status
- `style=flat-square` - Different badge style

## Quick Copy-Paste (Replace YOUR_USERNAME)

```markdown
# Airline Tracker System

[![CI/CD Pipeline](https://github.com/YOUR_USERNAME/airline-tracker-system/actions/workflows/ci-cd.yml/badge.svg?branch=main)](https://github.com/YOUR_USERNAME/airline-tracker-system/actions)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java 17](https://img.shields.io/badge/java-17-blue)](https://openjdk.org/projects/jdk/17/)
[![Spring Boot 3.x](https://img.shields.io/badge/spring--boot-3.x-green)](https://spring.io/projects/spring-boot)
[![Code Coverage](https://img.shields.io/badge/coverage-92.3%25-brightgreen)](./FINAL-TEST-RESULTS.md)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](CONTRIBUTING.md)

> Real-time flight tracking and AI-powered summary generation system built with Spring Boot microservices.

## Status: Production Ready ✅

**Last Updated:** 2024-11-14  
**Version:** 1.0.0  
**CI/CD:** Automated testing and deployment
```

---

**Remember:** After adding the badge, commit and push to see it live on GitHub!