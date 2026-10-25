---
description: Prepare the Forge-via-Connector smoke test (milestone M10)
---
<!-- Purpose: M10 owner checklist. Dependencies: docs/FORGE.md, docs/TESTING.md T10.x. -->

1. Build the jar (`./gradlew build`).
2. Audit `fabric.mod.json` `depends`: list every Fabric API module used and confirm each is
   present in the Forgified Fabric API release recorded in `docs/FORGE.md`.
3. Grep the source for any Fabric API usage not declared in `depends`.
4. Write a step-by-step owner checklist into `docs/FORGE.md`: Forge 1.20.1 version, Connector
   version, Forgified Fabric API version, where to place jars, and checks T10.1–T10.3.
5. Do not claim Forge compatibility. Only the owner's test run establishes it.
