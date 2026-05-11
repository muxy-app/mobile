# Muxy Mobile

React Native + Expo companion app for **Muxy**, the desktop terminal multiplexer. Connects to Muxy's WebSocket server on the local network to control sessions remotely.

## Stack

- Expo, React Native, React
- TypeScript

## Commands

- `npm run typecheck` — `tsc --noEmit`
- `npm run lint` — `expo lint`

Always run typecheck and lint before declaring a change done.

## Top Level Rules

- Security first
- Native Only
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
- Upload screenshots or recordings for the PRs.
- Never answer any question without a proper investigation and exploring the codebase.
- Prioritize problem comprehension over premature implementation. Validate the approach before execution to avoid rework
- Plan properly before executing to not double work

## Code Review

- Review the PRs/Code against the purpose of the PR/Issue/Asked. If you find unrelated issues to the PR during the review, Report them in a separate section.
- Apply review recommendations only after user's confirmation.
