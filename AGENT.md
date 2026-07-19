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

## Developer Setup & Agent Customizations
This workspace utilizes the **Antigravity AI Agent** for pair programming and development automation.
The following custom skills are configured for this project in the `.agents/skills/` directory:
- `git-advanced-workflows`
- `monorepo-management`

### Re-establishing the Agent Environment
To enable these skills when developing in a new environment or machine:
1. Ensure the Antigravity agent is installed and configured on your machine.
2. The skills directory in this repository `.agents/skills/` contains symlinks that point to the agent's global skills installation folder.
3. If symlinks are broken on a new machine or environment, you can recreate them pointing to your local Antigravity installation (typically located at `~/.gemini/antigravity/skills/` or similar):
   ```bash
   ln -sfn ~/.gemini/antigravity/skills/git-advanced-workflows .agents/skills/git-advanced-workflows
   ln -sfn ~/.gemini/antigravity/skills/monorepo-management .agents/skills/monorepo-management
   ```

