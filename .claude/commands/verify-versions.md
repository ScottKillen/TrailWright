---
description: Confirm and pin toolchain versions for Minecraft 1.20.1 (milestone M0)
---
<!-- Purpose: M0 version verification. Dependencies: gradle.properties, docs/FORGE.md. -->

Verify every version in `gradle.properties` against live sources. Do not trust memory.

1. Fetch https://fabricmc.net/develop/ (or the Fabric meta API at
   `https://meta.fabricmc.net/v2/versions/loader/1.20.1`) for the latest stable Fabric Loader
   compatible with 1.20.1.
2. Find the newest `fabric-api` version ending in `+1.20.1` on Modrinth or
   maven.fabricmc.net.
3. Find a Loom version that supports 1.20.1 and the Gradle version it requires.
4. Confirm Sinytra Connector and Forgified Fabric API publish **Forge** (not only NeoForge)
   builds for 1.20.1. Record exact versions and download links in `docs/FORGE.md`.
5. Update `gradle.properties`, generate the wrapper, run `./gradlew build`.
6. Report each version with the URL you confirmed it from. Flag anything you could not confirm.
