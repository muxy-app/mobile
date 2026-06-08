# Muxy Mobile

Companion app for Muxy (github.com/muxy-app/muxy)

## Stack

### Current

- Expo, React Native, React
- TypeScript

### Commands

- `npm run typecheck` — `tsc --noEmit`
- `npm run lint` — `expo lint`

### New

- Swift, SwiftTerm for iOS (ios/)
- Kotlin for Android (android/)

### Commands

- `ios/scripts/run.sh test`
- `android/scripts/run.sh test`

## API Docs

- https://muxy.app/llms.txt
- https://muxy.app/docs/remote-server/overview/plain

## Top Level Rules

- Security first
- Maintainability
- Scalability
- Clean Code
- Clean Architecture
- Best Practices
- No Hacky Solutions

## Main Rules

- No commenting allowed in the codebase
- All code must be self-explanatory and cleanly structured
- Use early returns instead of nested conditionals
- Don't patch symptoms, fix root causes
- For every task, Consider how it will impact the architecture and code quality, not just the immediate problem
- Use logs for debugging.
- If the feature is testable, then you must write tests.
- Avoid long PR descriptions. It is for humans and keep it in 3 lines maximum.
- Upload screenshots or recordings for the PRs (Contributors)
- Never answer any question without a proper investigation and exploring the codebase.
- Prioritize problem comprehension over premature implementation. Validate the approach before execution to avoid rework
- Plan properly in the plan mode before executing to not double work

## Code Review

- Review the PRs/Code against the purpose of the PR/Issue/Asked. If you find unrelated issues to the PR during the review, Report them in a separate section.
- Apply review recommendations only after user's confirmation.
