# Development guide

## Why this exists

This repository combines a modern Gradle/toolchain wrapper with a Minecraft 1.7.10 runtime, convention-generated tasks, a coremod, several test environments, and compatibility-sensitive persisted data. This guide gives commands verified from the current task graph and a change workflow that avoids the most common false confidence: a successful compilation is not proof that transformed, reflected, client-only, dedicated-server, or save-load paths work.

For architectural context, start with the [overview](README.md), [project map](01-project-map.md), and the subsystem guide for the code you intend to change.

## Prerequisites and first checkout

Use the checked-in Gradle wrapper rather than a system Gradle. The current wrapper is Gradle 9.4.0 ([`gradle-wrapper.properties`](../../gradle/wrapper/gradle-wrapper.properties#L3)); the Gradle daemon toolchain is configured for Java 25 ([`gradle-daemon-jvm.properties`](../../gradle/gradle-daemon-jvm.properties#L2)). The build enables Jabel so source may use modern Java syntax while producing Java 8-compatible bytecode for the legacy game ([`gradle.properties`](../../gradle.properties#L42)).

You need:

- a JDK/toolchain that the wrapper can use or permission for Gradle's configured Foojay resolver to obtain one;
- network access on the first dependency/toolchain resolution;
- enough disk and memory for a deobfuscated Minecraft workspace; and
- an IDE that imports the Gradle project rather than treating `src/main/java` as a standalone module.

Run commands from the repository root. A conservative first pass is:

```bash
./gradlew tasks --all
./gradlew classes test
./gradlew spotlessCheck checkstyleMain checkstyleTest
```

`tasks --all` was executed against the current tree and confirms the task names below. It does not prove that every launch succeeds with an offline cache or every optional integration installed.

## Build system facts that affect daily work

[`build.gradle`](../../build.gradle) only applies `com.gtnewhorizons.gtnhconvention`; the pinned settings convention is `2.0.26` in [`settings.gradle`](../../settings.gradle#L19). Most task implementation is therefore outside this repository. Locally visible configuration is spread across:

| File | Development consequence |
|---|---|
| [`gradle.properties`](../../gradle.properties) | Mod/Forge/mapping versions, generated `BuildTags`, `apiPackage`, access transformer, coremod manifest wiring, formatting, publishing, and parallel/configuration-cache switches. |
| [`dependencies.gradle`](../../dependencies.gradle) | Required implementations, compile-only optional APIs, development runtime mod set, JUnit layers, and HorizonQA. A compile-only dependency does not make its integration active. |
| [`addon.gradle`](../../addon.gradle) | Defines and packages `functionalTest`, then wraps real client/server runs with a property. |
| [`repositories.gradle`](../../repositories.gradle) | Adds local/K-4U/OpenComputers repositories; the convention adds its well-known repository set. |
| [`src/main/resources/META-INF/appeng_at.cfg`](../../src/main/resources/META-INF/appeng_at.cfg) | Access changes consumed before ordinary runtime. A compile/run mismatch here can appear as access errors only in game. |

Generated `appeng.core.BuildTags` is configured by `generateGradleTokenClass`; `mcmod.info` contains token placeholders. Do not edit files under `build/generated` or copied recipe directories. Update checked-in configuration or resources and regenerate.

The current root README still mentions old ForgeGradle-era setup commands such as `setupDecompWorkspace`, `setupDevWorkspace`, `genIntellijRuns`, and Eclipse setup. Some related convention tasks may exist, but that prose is not the authoritative modern workflow. Use `./gradlew tasks --all` for this checkout.

## Verified tasks and commands

The following names were observed in `./gradlew tasks --all --offline --console=plain` on 2026-08-09. “Verified” here means present in the configured task graph, not that every heavyweight task was executed during this documentation-only investigation.

### Compile, package, and static checks

```bash
./gradlew classes
./gradlew apiClasses apiJar
./gradlew assemble
./gradlew build
./gradlew check
./gradlew test
./gradlew spotlessCheck
./gradlew spotlessApply
./gradlew checkstyleMain checkstyleTest checkstyleApi checkstyleFunctionalTest
./gradlew reobfJar
./gradlew generateAssets
```

Use `spotlessApply` only when you intend to rewrite formatting and will inspect the diff. For a focused change, avoid formatting unrelated files. `build`/`check` aggregate convention-owned work and may evolve with the pinned plugin.

### Development launches

```bash
./gradlew runClient
./gradlew runServer
./gradlew runClient --username=AnotherPlayer
./gradlew runClient17
./gradlew runClient21
./gradlew runClient25
./gradlew runServer17
./gradlew runServer21
./gradlew runServer25
```

The task graph also contains obfuscated launch variants. Prefer a normal deobfuscated launch for iteration and use an obfuscated/reobfuscated path when testing transformer/name behavior or packaging compatibility. A dedicated `runServer` is essential for detecting accidental `appeng.client` classloading that an integrated client can hide.

Workspace/bootstrap tasks include `setupDecompWorkspace` and `setupCIWorkspace`; `jitpack.yml` uses the latter. IDE run configuration behavior is convention-driven, so reimport Gradle after dependency/source-set changes.

### Functional-test launches

[`addon.gradle`](../../addon.gradle#L33) defines wrappers:

```bash
./gradlew runFunctionalTestServer
./gradlew runFunctionalTestClient
```

They delegate to the normal launch with `-Pae2.withFunctionalTests=true`. Equivalent direct forms are:

```bash
./gradlew runServer -Pae2.withFunctionalTests=true
./gradlew runClient -Pae2.withFunctionalTests=true
```

The test mod writes legacy JUnit XML to `junit-out/`. On the server, a failure throws and fails the launch; the client path reports but does not use the same server-side throw. Prefer the server wrapper for an automated pass unless the behavior is explicitly client-only.

### HorizonQA game tests

There is no repository-defined one-command local task named `horizonqa`. HorizonQA is a `devOnlyNonPublishable` dependency, the tests are under main source, and the external reusable CI workflow enables it with `horizonqa: true` ([`build-and-test.yml`](../../.github/workflows/build-and-test.yml#L12)). Use the GTNH/HorizonQA launch facilities provided by the configured development environment or CI; do not invent a Gradle task name.

**Uncertain:** the reusable workflow is referenced at a moving external `master`, so its exact internal commands are not established by this tree. The repository does establish the opt-in and test/resource locations.

## Test layers and what they prove

| Layer | Location and runner | Good for | Does not prove |
|---|---|---|---|
| Fast unit tests | [`src/test/java`](../../src/test/java), JUnit 4 via `./gradlew test` | Pure utilities, version parsing, formatting, IDs/name encoding. | Forge registration, class transformation, Minecraft state, grid topology, packets, or save reload. |
| Functional tests | [`src/functionalTest/java`](../../src/functionalTest/java), JUnit 5 launched by the `appeng-tests` mod | Real game/server environment with mocks and extensive crafting-v2 scenarios. | Full player/UI interaction or every physical topology/integration combination. |
| HorizonQA in-game tests | [`src/main/java/appeng/gametests`](../../src/main/java/appeng/gametests) plus [`horizonqastructures`](../../src/main/resources/assets/appliedenergistics2/horizonqastructures) | Structure-based I/O port, P2P, drive/cell, buses, interfaces, network core/power, crafting execution, and AE2FC behavior. | Every GUI/protocol, transformer, persistence migration, or optional-mod version. |
| Manual client | `runClient*` | Rendering, GUI, input, integrated-server lifecycle, UX. | Dedicated-server classloading or adversarial packet/security behavior. |
| Manual dedicated server | `runServer*` | Server-only classpath/lifecycle, commands, save/restart, multiplayer authority. | Client rendering/input. |

[`AppengTestMod`](../../src/functionalTest/java/appeng/test/AppengTestMod.java#L30) launches JUnit Platform at `FMLServerStartedEvent`, selects package `appeng.test`, cleans prior XML, and writes a summary. [`CraftingV2Tests`](../../src/functionalTest/java/appeng/test/CraftingV2Tests.java) provides broad planner cases. [`NetworkInventoryHandlerFunctionalTest`](../../src/functionalTest/java/appeng/test/me/storage/NetworkInventoryHandlerFunctionalTest.java) currently covers only a small portion of federated storage behavior; do not interpret its name as comprehensive network-storage coverage.

There are currently 58 `@GameTest` methods across ten main-source holders. Their production source-set placement is historical/tooling-driven: do not use those test classes as a normal runtime API, and verify packaging assumptions before moving them.

## Choosing the right validation

Match tests to the architecture boundary, not the file count:

- pure parser/value change: unit test plus formatting/static checks;
- recipe resource/handler: launch-time recipe parse and representative recipe lookup, including the GTNH/fallback root when relevant;
- block/tile/part: HorizonQA structure or focused game test, save/reload, placement/removal, and dedicated-server check;
- grid/cache: topology split/merge, node add/remove, channel/power transition, and persistence reconstruction;
- storage: simulate/modulate, priority, capacity/remainder, permission, monitor delta, recursion, and different stack types;
- crafting: planner functional tests plus real CPU execution, cancellation/failure, ingredients returned, chunk unload, and save/reload;
- GUI/packet: sender and receiver, stale/malformed input, permission/power loss, server authority, and client replica update;
- integration/coremod: dependency absent/present, dedicated server and client, transformed member/class shape, and supported mod versions;
- world data: two start/stop cycles in one JVM plus a real save/reload and historical fixture when format changes.

## Debugging entry points

Start at the boundary that owns the symptom:

| Symptom | First breakpoints/logging sites |
|---|---|
| Mod fails before pre-init | [`AppEngCore.getASMTransformerClass`](../../src/main/java/appeng/transformer/AppEngCore.java#L51), transformer `transform` methods, generated manifest/AT. |
| Feature absent or wrong metadata | [`ApiDefinitions`](../../src/main/java/appeng/core/ApiDefinitions.java), [`DefinitionConstructor`](../../src/main/java/appeng/core/api/definitions/DefinitionConstructor.java), [`Registration.preInitialize`](../../src/main/java/appeng/core/Registration.java#L136), `AEConfig` feature state. |
| Optional integration disabled/failed | [`IntegrationNode.call`](../../src/main/java/appeng/integration/IntegrationNode.java#L47), `IntegrationRegistry`, the module constructor/init, and the AE2 integration config category. |
| Tile never joins network | `validate`/`onReady`, [`TickHandler.addInit`](../../src/main/java/appeng/hooks/TickHandler.java#L88), [`AENetworkProxy.onReady`](../../src/main/java/appeng/me/helpers/AENetworkProxy.java#L108), `GridNode.updateState`. |
| Network split/channel issue | [`GridConnection.destroy`](../../src/main/java/appeng/me/GridConnection.java), `GridNode.updateState`, [`PathGridCache`](../../src/main/java/appeng/me/cache/PathGridCache.java), grid event posts. |
| Storage action wrong | [`NetworkInventoryHandler.injectItems`](../../src/main/java/appeng/me/storage/NetworkInventoryHandler.java), `extractItems`, permission check, handler priority list, monitor change posts. |
| Crafting stalls | planner task/result, [`CraftingGridCache`](../../src/main/java/appeng/me/cache/CraftingGridCache.java), [`CraftingCPUCluster`](../../src/main/java/appeng/me/cluster/implementations/CraftingCPUCluster.java), CPU inventory/link NBT. |
| GUI appears stale | client sender, packet parser/handler, server container validation, `postChange`, `PacketMEInventoryUpdate`, client repository update. |
| Save/restart regression | [`AppEng.serverAboutToStart`](../../src/main/java/appeng/core/AppEng.java#L241), `WorldData`, `StorageData`, cache `populateGridStorage`/`onUpdateTick`/save paths, `serverStopping` and `serverStopped`. |

Useful runtime diagnostics are dispatched by [`AECommand`](../../src/main/java/appeng/server/AECommand.java), including profiling, path debugging, timing, and chunk logging. Their permission levels differ. Use them on a disposable development save; some debug feature classes are config-gated rather than dead code.

Configuration is rooted at the Forge config directory's `AppliedEnergistics2` folder, created in [`AppEng.preInit`](../../src/main/java/appeng/core/AppEng.java#L138). Normal files include `AppliedEnergistics2.cfg`, meteorite loot JSON, facades, and custom-recipe configuration. Save-wide data is under the world's `AE2` directory. Logs use [`AELog`](../../src/main/java/appeng/core/AELog.java); integration failure details are also added to crash diagnostics.

## How to make a small feature or fix

1. **State the authority and lifetime.** Is the truth in an item stack, tile/part NBT, a grid cache, `GridStorage`, or save-wide `WorldData`? Is it client presentation or server state?
2. **Trace both directions before editing.** Find registration → construction → mutation → save/sync and the inverse load/receiver path. Use the [evidence index](10-evidence-index.md).
3. **Respect indirect activation.** Search `@TileEvent`, `@Integration.*`, enum registries, resources, reflection naming, feature handlers, and transformers.
4. **Keep the patch focused.** Do not combine behavior with repository-wide formatting or cleanup. Preserve legacy overloads/NBT/ordinals unless a migration is part of the task.
5. **Use the established extension point.** A definition/feature handler for game objects, cache registry for grid services, integration node for optional mods, `SyncManager`/packet conventions for UI, and correct recipe resource root for recipes.
6. **Validate on the authoritative side.** Packet data is intent. Re-resolve current server objects, bounds, permissions, power, inventory, and topology before mutation.
7. **Write the smallest test that crosses the risk boundary.** For graph/persistence/protocol work, a pure unit test is rarely sufficient.
8. **Inspect generated/runtime effects.** Check registry/meta values, resources, transformed access, dedicated-server classloading, and save output as applicable.
9. **Run formatting/static checks and inspect the entire diff.** `git diff --check` is necessary but does not detect semantic scope creep.

## Compatibility and contribution constraints

- Registry names, item damage/meta values, NBT keys, grid-storage keys/IDs, dimension IDs, recipes, packet/GUI/action enum ordinals, and API signatures can be persistent or wire contracts.
- `appeng.api` is a separately exposed addon boundary. Deprecated accessors and compatibility defaults may still be used by external binaries; [`ApiRepairer`](../../src/main/java/appeng/transformer/asm/ApiRepairer.java) exists precisely because old addons embedded API classes.
- A class can be server-safe only after transformation removes an optional interface/method. Preserve its annotations and absent-dependency test.
- Client classes must stay behind `CommonHelper.proxy`, side checks, reflection reached only on the client, or other established separation.
- The build convention and CI workflows are external inputs. Pin changes deliberately and verify the task/release behavior rather than inferring it from the five-line `build.gradle`.
- [`CONTRIBUTING.md`](../../CONTRIBUTING.md#L8) points to current GTNH-wide contribution guidance and asks for focused commits, full diff inspection, and documented testing. Its later tabs/brace examples conflict with the current [`.editorconfig`](../../.editorconfig) and source. Follow enforced Spotless/Checkstyle/EditorConfig behavior, not those stale examples.
- The old README's artifact coordinates and workspace instructions may also be stale. Verify publishing metadata before documenting dependencies for addon users.

## First-contribution checklist

Before coding:

- [ ] Read the overview, project map, relevant subsystem guide, and nearby tests.
- [ ] Confirm the current working tree and preserve unrelated changes.
- [ ] Locate the API/registration entry and every load/save or sender/receiver pair.
- [ ] Identify server authority, client replica, thread, physical side, and lifecycle owner.
- [ ] List compatibility surfaces: API, registry/meta, NBT, wire ordinal, resource, reflection, ASM, optional dependency.

Before handing off:

- [ ] Run the focused unit/functional/HorizonQA/manual validation appropriate to the boundary.
- [ ] Run a dedicated-server path for side-sensitive work and a client path for rendering/UI work.
- [ ] Exercise save/reload and two lifecycle cycles for persistent/global-state changes.
- [ ] Run `spotlessCheck`, relevant Checkstyle tasks, and `git diff --check`.
- [ ] Inspect every changed file; verify no generated output or unrelated formatting entered the patch.
- [ ] Document commands, outcomes, untested combinations, and any migration/compatibility assumptions.

## Known test gaps

The current tree has strong targeted crafting-v2 and several useful in-game machine scenarios, but relatively little direct coverage for packet/GUI validation, hostile or out-of-order input, coremod transformation output, optional-mod version matrices, historical save migration, grid split/merge cache-state behavior, repeated integrated-server start/stop, and broad storage priority/permission/recursion behavior. These are gaps, not evidence that those areas are broken. They should influence the test plan and refactor order in the [refactor map](08-refactor-map.md).

## Related guides

- [Startup and runtime](02-startup-and-runtime.md)
- [ME network](03-me-network.md)
- [Storage and crafting](04-storage-and-crafting.md)
- [Game objects and UI](05-game-objects-and-ui.md)
- [Refactor map](08-refactor-map.md)
- [Evidence index](10-evidence-index.md)
