---
description: Implement one milestone from docs/PLAN.md end to end
argument-hint: <milestone number, e.g. 2>
---
<!-- Purpose: drive a single milestone. Dependencies: docs/PLAN.md, DESIGN.md, TESTING.md. -->

Implement milestone M$ARGUMENTS from `docs/PLAN.md`.

1. Re-read `docs/DESIGN.md` sections relevant to M$ARGUMENTS and its checks in `docs/TESTING.md`.
2. Confirm prior milestones are marked done. If not, stop and say so.
3. State a short plan (files to touch, risks) before editing.
4. Implement. Keep the hot-path rules in `CLAUDE.md`.
5. Run `./gradlew build` and `./gradlew test`, plus `runGametest` from M4 on.
6. List which TESTING.md checks you ran and their results, and which need the owner in-game.
7. Update `PLAN.md` (mark done only if all automated checks pass) and `DESIGN.md` if behavior changed.
