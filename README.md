# AOP: Thaumcraft 4

**Fully ported version with first addon (Thaumic Tinkerer) will be available in coming days. Stay tuned!**

<img src="assets/branding/aop-logo.png" alt="AOP: Thaumcraft 4" width="256" height="256">

**AOP projects:** [Thaumcraft 4](https://github.com/Salossov/AOP-Thaumcraft-4) · [Compact](https://github.com/Salossov/AOPTC4-Compact) · [Thaumic Tinkerer](https://github.com/Salossov/AOPTC4-Thaumic-Tinkerer) · [AOP UI](https://github.com/Salossov/AOP-UI) · [Forbidden Magic](https://github.com/Salossov/AOPTC4-Forbidden-Magic) · [Thaumic Energistics](https://github.com/Salossov/AOPTC4-Thaumic-Energistics)

Unofficial port of **Thaumcraft 4.2.3.5** to **Minecraft 1.21.1**, built on NeoForge and Java 21.

[Русский](README.ru.md) | [Addon development](docs/addons/README.en.md) | [Attribution](NOTICE.md)

> **Personal use only.** Thaumcraft and all of its assets belong to Azanor. This repository contains the original mod's logic and assets and is not intended for redistribution.

The project was inspired by [Thaumcraft 3: Reforged](https://github.com/notlikethiss/thaumcraft-3-reforged).
NeoForge mod ID: **`thaumcraft_aop`**. Registry and resource namespace: **`thaumcraft`**. Existing item/block IDs and configuration filenames are retained.


## Overview

The port includes research, aspects, aura nodes, wands and staves, alchemy, infusion, essentia networks, golems, equipment and the Outer Lands. A separate public API and addon SDK provide extension points for additional content.

Version **1.1.0** is available for testing. Automated verification covers API rules and selected gameplay scenarios. Manual visual and multiplayer acceptance remains in progress. Hidden Magic Box storage is not implemented.

## Requirements

| Component | Supported version | Requirement |
| --- | --- | --- |
| Minecraft | 1.21.1 | Required |
| Java | 21 | Required |
| NeoForge | 21.1.166 ≤ version < 21.2 | Required |
| Curios | 9.5.0+1.21.1 ≤ version < 10 | Required |
| AOP: UI | Compatible 0.8.x build | Required on client and server |
| TerraBlender | 4.1.0.8 ≤ version < 4.2 | Optional |

Dependencies must target Minecraft 1.21.1 and NeoForge. Without TerraBlender, the built-in biome placement fallback is used.

## Installation

1. Install Minecraft, Java, NeoForge and the required dependencies.
2. Place `thaumcraft-1.21.1-1.1.0.jar` in the instance's `mods` directory on both client and server.
3. Install a compatible AOP: UI gameplay JAR on both sides.

The gameplay JAR includes the public API. Separate API and sources JARs are development artifacts and must not be installed as mods. Existing worlds should be backed up before testing an update.

### Related projects

- [**Thaumic Tinkerer**](https://github.com/Salossov/AOPTC4-Thaumic-Tinkerer) provides additional devices, foci and KAMI content. It requires a compatible core build.
- [**AOP:TC4 Compact**](https://github.com/Salossov/AOPTC4-Compact) provides optional JEI/EMI recipe integration and Jade/WTHIT tooltips. These integrations are separate from the core.
- [**AOP: UI**](https://github.com/Salossov/AOP-UI) provides the shared configuration interface.

## Configuration and controls

The Thaumonomicon introduces research and progression. The Thaumometer discovers aspects through scanning.

| Default key | Action |
| --- | --- |
| F | Select a focus |
| H | Toggle the harness |
| G | Change architect mode |
| Shift+G | Change architect axis |

Key bindings can be reassigned in Minecraft settings.

Client configuration is stored in `config/thaumcraft-client.toml`; server configuration is stored with the world. The shared settings interface opens from Mods, the Thaumonomicon gear or the AOP: UI button in the pause menu.

Client changes are saved immediately. Server changes are committed with **Save** or **Esc**. Multiplayer editing requires OP level 4; the singleplayer world owner can edit without enabling cheats. Options marked for restart retain their current effective value until restart. Existing configuration keys and defaults are preserved.

## Building from source

Build AOP: UI first and provide its gameplay JAR as a Gradle dependency. With JDK 21 available, run from this repository:

```powershell
./gradlew.ps1 build -PaopUiJar=C:/dependencies/aopui-1.21.1-0.8.0.jar
```

On Linux and macOS, use `sh ./gradlew` with the same task and properties. Dependencies are downloaded during the initial build. Minecraft and NeoForge versions are defined in `gradle.properties`.

| Output | Purpose |
| --- | --- |
| `mod/build/libs/thaumcraft-1.21.1-1.1.0.jar` | Gameplay mod |
| `api/build/libs/thaumcraft-api-1.1.0.jar` | Addon development dependency |
| `mod/build/libs/thaumcraft-1.21.1-1.1.0-sources.jar` | Source archive |

The development client uses `:mod:runClient`; the addon SDK uses `addonSdk`. Both commands accept the same `aopUiJar` property. The [addon development guide](docs/addons/README.en.md) documents SDK installation, data formats and public API contracts.

## Verification and continuous integration

`build` compiles the project and executes the API and standalone rule checks. Gameplay verification uses separate source sets excluded from the gameplay JAR. Build success and in-game verification are separate results.

Source and build changes trigger CI; documentation-only changes do not. Pushes to `main` retain the separate nightly channel.

In **Actions → Build and release → Run workflow**, enable **release** to publish an ordinary GitHub release with tag `v<version>`. Leave **version** blank to use `mod_version` from `gradle.properties`, or enter `major.minor.patch` to override it for that run. The selected version is used in the JAR metadata, filenames and tag; the workflow does not modify the source files. Prerelease suffixes are rejected in release mode. Existing release tags are never overwritten. Pushes to `main` and manual runs with **release** disabled update the single `nightly` prerelease. Nightly JAR versions use `<mod_version>-nightly.<source_commit>`. Pull requests build artifacts without publishing. Local `act` runs never publish. Ordinary release notes list every commit since the nearest previous ordinary release on the selected history, with commit links and a comparison link. The first release includes the full history. Nightly notes omit this list.

Individual CI and **Build mod suite** use the same build recipes and `aop-suite-manifest.json` format. An individual workflow builds or reuses only its component and required dependencies, then publishes only its own component. The suite can import its JARs from that repository's Releases when source revision and contents, version, toolchain, recipe and dependency checksums match. Repository caches are independent; published JARs provide reuse across repositories. A nightly run accepts matching release or nightly artifacts; an ordinary release accepts only release artifacts, including dependencies. The `force_rebuild` input bypasses ready JARs and preserves Gradle caching. Keep `.github/scripts/suite.py`, `suite_bundle.py` and `release.py` identical across the four repositories when updating the shared artifact contract.

**Actions → Build mod suite** builds and checks **AOP: UI → TC4 and API → Thaumic Tinkerer → Compact** in one workflow. It passes the verified JARs to the next stage and publishes each component in its own repository only after all four stages pass. With **release** unchecked it updates each `nightly`; with **release** checked it publishes ordinary releases. The four optional version fields default independently to each repository's `gradle.properties`. Existing ordinary releases are reused only when their attached suite manifest matches; conflicting tags are rejected before publication starts.

The suite first checks exact artifact caches and published release manifests. Identity includes source revisions and contents, selected versions, Java/platform, build recipes and dependency JAR checksums. Nightly builds can reuse matching release or nightly JARs; ordinary releases accept only release JARs. A missing, mismatched or damaged artifact is rebuilt using the Gradle dependency and task cache. Suite-built nightly versions use a source-commit suffix so unchanged JARs can be reused across runs. Existing artifacts without provenance are rebuilt once. **force_rebuild** bypasses JAR reuse while preserving Gradle caching. Every publication includes `aop-suite-manifest.json` and checksums; the workflow also saves the complete suite as an Actions artifact. Publishing four repositories is sequential, not an atomic operation; a publication failure is reported and may leave an already-published subset.

For the suite, configure `DEPENDENCY_TOKEN` in this repository with **Contents: Read and write** for all four repositories. For an individual build, read access to its dependency repositories is sufficient. Start the suite with `gh workflow run suite.yml --repo Salossov/AOP-Thaumcraft-4 --ref main -f release=false`.

For local `act` runs of the individual workflow, place compatible UI sources at `.dependencies/aopui`. The suite additionally requires `.dependencies/tinkerer` and `.dependencies/compact`; `act workflow_dispatch -W .github/workflows/suite.yml` verifies the complete sequence without publishing. Source inventories for exported checkouts are documented in [the suite helper](.github/scripts/suite.py). A persistent `SUITE_CACHE_DIR` and Gradle user-home volume allow local cache-reuse checks. Local runs skip GitHub artifact/cache APIs and publication; those are verified on GitHub.

| Repository variable | Purpose |
| --- | --- |
| `AOPUI_REPOSITORY` | AOP: UI source; `Salossov/AOP-UI` |
| `AOPUI_REF` | AOP: UI source revision; defaults to `main` |
| `TINKERER_REPOSITORY`, `TINKERER_REF` | Suite addon source; `Salossov/AOPTC4-Thaumic-Tinkerer`, `main` |
| `COMPACT_REPOSITORY`, `COMPACT_REF` | Suite integration source; `Salossov/AOPTC4-Compact`, `main` |

Bug reports should include component versions, reproduction steps, expected and actual behavior, and the relevant log or crash report. Credentials and personal information should be removed from attachments.

Compact and the complete suite automatically check out and build Thaumic Energistics from source after UI/core and before Compact. No dependency ZIP URL or SHA-256 variables are required. Its source identity and verified JAR checksums participate in Compact’s cache key; this compile dependency is not embedded in Compact. `DEPENDENCY_TOKEN` also needs Contents read access to the ThE repository while it is private. Override its source with `THAUMIC_ENERGISTICS_REPOSITORY` / `THAUMIC_ENERGISTICS_REF`. Local `act` also needs `.dependencies/thaumicenergistics` (or `SUITE_THE_ROOT`). The suite publishes the same four components; ThE remains an independent release.

## Credits and terms

- [Azanor](https://www.curseforge.com/minecraft/mc-mods/thaumcraft) - original Thaumcraft, gameplay and assets.
- CovertJaguar, Dan Scott, Lorc, bonemouse, Scott Killen, Rorax and chicken_bones - original project credits.
- [Salossov](https://github.com/Salossov) - Minecraft 1.21.1 port and maintenance.
- [NeoForge](https://github.com/neoforged/NeoForge), Curios and TerraBlender - platform and integrations.

[NOTICE.md](NOTICE.md) describes attribution and license scope. `TEMPLATE_LICENSE.txt` applies to the build template only; it does not license Thaumcraft or its assets under MIT. This project is unofficial.
