# Repository Guidelines

## Project Structure & Module Organization
`src/` hosts the TypeScript React application. Routing lives in `src/App.tsx`, while domain views are grouped under `src/components/` (for example, `DriverViewPage`, `HomePage`, `ProfilePage`, and shared utilities). Data contracts and API helpers belong in `src/models/`. Static assets ship from `public/`, and reference screenshots for documentation reside in `Resources/img/`. Place environment configuration alongside the app: general values in `.env`, sensitive overrides in `.env.local`, both ignored by Git except for local usage.

## Build, Test, and Development Commands
- `npm install` – install dependencies before any local work.
- `npm start` – launch the React development server on `http://localhost:3000` with hot reload against the configured Roadrunner backend.
- `npm run build` – produce an optimized production bundle in `build/` for deployment.
- `npx eslint "src/**/*.{ts,tsx}"` – optional lint sweep using the bundled CRA ESLint rules to catch style and safety issues early.

## Coding Style & Naming Conventions
Prefer functional React components in TypeScript. Use 2-space indentation, trailing semicolons, and single quotes for strings to match the existing codebase. Name components and folders with `PascalCase` (`DriverViewPage.tsx`), hooks/utilities in `camelCase`, and constants in `UPPER_SNAKE_CASE`. Co-locate component-specific styles as `.css` siblings and keep shared helpers inside `components/Utils/`.

## Testing Guidelines
Create Jest/_React Testing Library_ specs alongside the code under test using the `*.test.tsx` suffix (for utilities use `*.test.ts`). Run suites with `npx react-scripts test --watch=false` to mirror CI behavior. When possible, cover authentication guards, map interactions, and formatting helpers; favor mocking remote APIs so tests stay deterministic. Highlight any untested critical paths in the pull request description until coverage exists.

## Commit & Pull Request Guidelines
Follow the existing history: short, imperative subjects (`Fix dropdown layout`) and optional body lines for context. Scope each commit to a single concern so reviewers can trace regressions quickly. Pull requests should link related Roadrunner issues, describe configuration impacts, include screenshots or GIFs for UI changes, and list verification steps (`npm start`, `npx react-scripts test`). Request review before merging and confirm the build completes cleanly.

## Security & Configuration Tips
Keep Auth0 and Mapbox secrets out of version control. Reference keys via `REACT_APP_*` variables in `.env.local`, and document any new configuration knobs in `README.md` and deployment manifests. Treat external API URLs as configurable inputs so staging and production can diverge safely.
