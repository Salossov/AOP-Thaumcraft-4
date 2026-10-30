# Working on AOP: Thaumcraft 4

- NeoForge loader ID: `thaumcraft_aop`. Keep the existing content namespace `thaumcraft` and configuration filenames (user decision, 2026-11-01).

## Project scope

This repository contains an unofficial port of Thaumcraft 4.2.3.5 from Minecraft 1.7.10 to Minecraft 1.21.1 / NeoForge. The working root is this `repo` directory, not its parent directory containing archives and reports.

- `api/src/main` contains the public API; `api/src/test` contains rule checks.
- `mod/src/main/java/thaumcraft` contains the gameplay implementation.
- `mod/src/main/resources` contains resources and game data; `mod/src/main/templates` contains metadata.
- `mod/src/productionVerification`, `mod/src/verification` and `mod/src/smoke` are separate verification source sets. Keep them in the repository, but exclude them from the gameplay JAR.
- Original JARs, external ports, extraction scripts, historical reports, caches and worlds belong outside the repository. Do not add them to Git.

## Working rules

- Work without subagents unless the user explicitly requests them.
- Ask before making a product or architecture decision that has not already been agreed. Do not ask again about accepted decisions.
- Obtain confirmation before deleting user data, modifying existing worlds or taking another irreversible action.
- Do not search the entire project when the task scope is known. Select the relevant module or directory first and limit `rg` to that scope.
- Obtain confirmation before a project-wide search or scan. If the user names a specific file, restrict changes to that file.
- Do not reread complete files unnecessarily; use the relevant excerpt when checking a detail.
- On Windows, use PowerShell. Do not run CMD.exe or `.bat` files without explicit permission; use `gradlew.ps1` for Gradle.
- Do not run Impeccable or `cargo fmt`. Do not reformat unrelated files.
- Do not create a worktree unless requested. Place requested worktrees in `<repository_root>/.worktree/`.
- Do not create commits, rewrite history or publish code without the corresponding user request.
- Write AGENTS.md files in English and preserve the meaning of their instructions when translating them.

## Building

JDK 21 is required. Minecraft, NeoForge, Curios and port versions are defined in `gradle.properties`; the Gradle version is defined in `gradle/wrapper/gradle-wrapper.properties`. Do not update unrelated versions as part of another task.

Build a compatible AOP: UI 0.7.x first. Pass its gameplay JAR through `aopUiJar`; the library is required for building and on both client and server.

From the repository root in PowerShell:

```powershell
.\gradlew.ps1 build -PaopUiJar=C:/dependencies/aopui-1.21.1-0.7.0.jar
.\gradlew.ps1 :mod:runClient -PaopUiJar=C:/dependencies/aopui-1.21.1-0.7.0.jar
.\gradlew.ps1 :mod:runServer -PaopUiJar=C:/dependencies/aopui-1.21.1-0.7.0.jar
```

`gradlew.ps1` looks for Java in the local `.tools/java21`, then the parent's `.tools/java21`, `JAVA_HOME` and `PATH`. It may use the parent Gradle cache when available. Do not commit JDK installations or caches.

The gameplay JAR is written to `mod/build/libs/` and includes the API. The separate `api/build/libs/` artifacts are for addon development. Curios is required; TerraBlender is optional. TerraBlender integration and the fallback biome-placement Mixin must remain mutually exclusive.

## Gameplay changes

- Preserve the behavior of Thaumcraft 4.2.3.5. Do not change original values, random-call order, radii, recipes or geometry for convenience without agreement.
- A class existing or decompiling successfully does not establish that its mechanics work.
- Preserve compatibility of NBT, SavedData, attachments and item components. Do not fix problems by deleting saves.
- Keep world changes, inventory changes and resource consumption under server-thread control. The client handles presentation and input.
- Do not block the server tick while waiting for chunks to generate. Prepare portals and natural generation incrementally while preserving original radii and geometry.
- When relevant, check cancellation, target removal, chunk unloading, world restart and release of task-owned tickets.

### Accepted user decisions

- The void robe has the fixed purple color `#6A3880`. It must not have a dyeing recipe; ordinary robes remain dyeable.
- Original distillation intervals remain 40 ticks, or 20 ticks with alumentum.
- HUD animations have equal duration at 30/60/144 FPS while preserving their appearance at 60 FPS.
- Configuring the bore with a wand points it opposite the player's look direction, independently of the clicked block face.
- A remote essentia bank must not delay the use of other valid infusion sources.
- Debug gameplay checks remain in the repository at the user's request.

## Verification

- Run checks appropriate to the change. Documentation changes do not require a full gameplay regression; verify commands, paths and links.
- A normal `build` verifies compilation and API/essentia rules; it does not replace gameplay checks.
- Server scenarios can use `.\gradlew.ps1 :mod:runR3Server -Pr3Phase=production -PaopUiJar=C:/dependencies/aopui-1.21.1-0.7.0.jar`. Choose the phase from the changed system and its verification handler, not by guessing.
- Gameplay scenarios modify worlds. Use isolated test profiles under `runs/`, never user saves. Do not accept the EULA without the user's agreement.
- For save/restart scenarios, prepare the scenario's expected initial fixture. Do not treat leftovers from another test as evidence.
- Check the actual `passed` field in a fresh JSON report, server errors and process exit code. State which checks were actually executed.
- Rendering changes require real-client verification in addition to a build. Check the relevant directions, states, transparency, shadows and animation durations. Do not substitute browser checks for gameplay checks.
- Keep verification code, worlds, logs and reports out of the gameplay JAR. Ensure the delivered artifact contains the code that was verified.

## Documentation and attribution

- Write `README.md` in English and `README.ru.md` in Russian. Maintain both versions: features, installation, building, limitations and authors. Preserve the exact Personal use only block and the Thaumcraft 3: Reforged inspiration link.
- Preserve Azanor's authorship and the original mod's credits; do not attribute original assets to this project.
- Follow `NOTICE.md` and the scope of `TEMPLATE_LICENSE.txt`. The template license does not make the entire mod MIT-licensed.
