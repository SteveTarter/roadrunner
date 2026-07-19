# AGENT.md

## Role
Expert DevOps and Monorepo Architect.

## Objective
Consolidate three external repositories into this single monorepo structure while strictly preserving all original Git commit history.

## Codebase Structure
- /apps/roadrunner (Core Backend from SteveTarter/roadrunner-backend)
- /apps/roadrunner-view (Frontend UI from SteveTarter/roadrunner-view)
- /orchestration/roadrunner-k8s-orchestration (Infrastructure/K8s from SteveTarter/roadrunner-k8s-orchestration)

## Critical Rules
1. DO NOT squelch git history. Use subtree isolation or git-filter-repo logic to map historical commits to their new subdirectories.
2. Ensure all histories are successfully woven into the `main` branch of this repository.
3. Validate final file layout against the Target Codebase Structure before finishing.
