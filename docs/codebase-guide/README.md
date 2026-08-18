# Applied Energistics 2 Unofficial: codebase guide

## Why this exists

Applied Energistics 2 (AE2) looks like a collection of Minecraft blocks, but its core is a server-side graph and a set of services attached to each connected graph. The surrounding Forge 1.7.10 machinery, bytecode transformers, multipart emulation, optional-mod bridges, persistent world data, and client GUIs make that simple idea hard to see when entering through a random class.

This guide builds the mental model first and then follows real calls. It was checked against the working tree at commit `70e83130b` (`2026-08-08`, plus local working-tree state) on 2026-08-09. Links point to the current repository and important claims name a concrete symbol. Treat line anchors as snapshot aids: after code moves, search for the named symbol.

No external description is used as evidence for repository behavior. Short explanations of Forge/FML and Minecraft terms are background; every AE2-specific statement is tied to source, build configuration, metadata, a resource, or a test in the [evidence index](10-evidence-index.md).

## The ten-minute overview

This is the GT New Horizons fork of Applied Energistics 2 for Minecraft 1.7.10. The repository confirms all three parts of that description: the root [README](../../README.md#L21) names the GTNH fork and Minecraft version, [`AppEng`](../../src/main/java/appeng/core/AppEng.java#L63) restricts the mod to 1.7.10 and requires GTNHLib, and [`gradle.properties`](../../gradle.properties#L23) targets Forge `10.13.4.1614` with the GTNH convention build.

At runtime, think in five layers:

| Layer | Responsibility | Canonical starting points |
|---|---|---|
| Bootstrap | Make otherwise-impossible compatibility and UI changes before ordinary classes load. | [`AppEngCore`](../../src/main/java/appeng/transformer/AppEngCore.java#L29), [`ASMIntegration`](../../src/main/java/appeng/transformer/asm/ASMIntegration.java#L35), [`ASMTweaker`](../../src/main/java/appeng/transformer/asm/ASMTweaker.java#L34) |
| Mod lifecycle | Read configuration, construct and register features, recipes, handlers, integrations, network channel, world services, and commands. | [`AppEng`](../../src/main/java/appeng/core/AppEng.java#L63), [`Registration`](../../src/main/java/appeng/core/Registration.java#L136) |
| World hosts | Blocks create tile entities; cable-bus blocks contain up to seven multipart parts. Hosts own grid nodes and persist local state. | [`AEBaseTileBlock`](../../src/main/java/appeng/block/AEBaseTileBlock.java#L63), [`AEBaseTile`](../../src/main/java/appeng/tile/AEBaseTile.java#L56), [`TileCableBus`](../../src/main/java/appeng/tile/networking/TileCableBus.java#L51), [`CableBusContainer`](../../src/main/java/appeng/parts/CableBusContainer.java) |
| ME network | Nodes and connections form a `Grid`. Each grid owns caches that implement services such as pathing/channels, energy, storage, crafting, ticking, security, P2P, and item-flow tracking. | [`GridNode`](../../src/main/java/appeng/me/GridNode.java), [`GridConnection`](../../src/main/java/appeng/me/GridConnection.java), [`Grid`](../../src/main/java/appeng/me/Grid.java), cache registration in [`Registration.initialize`](../../src/main/java/appeng/core/Registration.java#L543) |
| Interaction | Server-side containers mediate player actions. Client GUIs render replicas and send intent packets; authoritative storage, security, energy, and crafting mutations happen on the server. | [`GuiBridge`](../../src/main/java/appeng/core/sync/GuiBridge.java#L146), [`AEBaseContainer`](../../src/main/java/appeng/container/AEBaseContainer.java), [`NetworkHandler`](../../src/main/java/appeng/core/sync/network/NetworkHandler.java#L27) |

The central invariant is: **a grid is a connected component, while its caches are the network-wide services for that component**. A drive is not “the network inventory.” It contributes one or more cell handlers to `GridStorageCache`; the cache builds a priority-ordered aggregate monitor over all contributors. Similarly, a crafting CPU is not the planner. `CraftingGridCache` discovers providers and CPUs, a selected planner computes a job, and a `CraftingCPUCluster` executes and persists it.

An analogy helps: treat the physical cables and machines as offices connected by corridors. `GridNode` and `GridConnection` describe the corridors; `Grid` says which offices form one building; caches are building-wide departments. Storage is the mailroom's index over many cupboards, crafting is a planning/execution department, and security is the access-control desk. The analogy stops at topology changes: splitting one network can create a new `Grid`, merge cache state, or divide persistent `GridStorage`, so the “building” object is not a permanent identity.

## How it starts, runs, stores state, and stops

1. FML loads the embedded coremod declared by [`coreModClass`](../../gradle.properties#L119). `AppEngCore` installs three class transformers and an access transformer before normal mod initialization.
2. FML creates [`AppEng`](../../src/main/java/appeng/core/AppEng.java#L92). `preInit` creates configuration objects and asks `Registration` to materialize feature definitions into Forge blocks, items, and tile entities.
3. `init` initializes stack network IDs, loads the custom recipe language, registers event handlers and grid-cache implementations, then initializes optional integrations.
4. `postInit` finishes spatial IDs and multipart support, initializes integrations, registers the GUI bridge, and creates the `AE2` packet channel.
5. Server ticks let [`TickHandler`](../../src/main/java/appeng/hooks/TickHandler.java#L165) ready deferred tiles, update every live grid, advance bounded crafting simulations, and run world/cross-world work queues.
6. Local machine state is written to tile/part NBT. Network-wide cache state goes through a persistent [`GridStorage`](../../src/main/java/appeng/me/GridStorage.java#L30), whose compressed data is stored in the save's `AE2/settings.cfg` by [`StorageData`](../../src/main/java/appeng/core/worlddata/StorageData.java#L35). Meteor/spawn and compass data use sibling save directories.
7. On stopping, `WorldData` saves or closes its world-scoped components; after stop, `AppEng` clears tick/network state, crafting notifications, locatable entries, and inventory-adaptor caches. See [startup and runtime](02-startup-and-runtime.md#server-lifecycle-and-shutdown).

## What to remember while tracing code

- `appeng.api` is the addon-facing contract bundled from this source tree; most implementations live elsewhere. [`AEApi.instance()`](../../src/main/java/appeng/api/AEApi.java#L25) deliberately reaches [`appeng.core.Api`](../../src/main/java/appeng/core/Api.java#L33) by reflection.
- `GridProxy` is a host-side convenience/guard, not the grid itself. It owns or exposes a node and cached service access for a tile or part.
- “Active” usually means more than connected: pathing/channel allocation and energy state can make a node inactive.
- Storage operations use `Actionable.SIMULATE` before `MODULATE` where rollback or capacity matters. A non-null result conventionally means a remainder, not success.
- Client code must never be the authority. A terminal sends the selected stack as context, but the server resolves it against the current monitor, charges power, and applies `NetworkInventoryHandler` permission checks using a `PlayerSource`.
- Registration is intentionally indirect. Constructing `ApiDefinitions` constructs feature objects and collects handlers; `Registration.preInitialize` later invokes those handlers. Multipart tile classes and optional interfaces can also be generated or stripped at runtime.
- “Legacy” is not synonymous with dead. Deprecated API overloads, `craftableItemsLegacy`, older crafting-job forms, and legacy NBT readers remain compatibility paths. Do not remove one based only on a call search.

## Guide map

1. [Project map](01-project-map.md) — packages, source sets, resources, API boundary, dependencies, and a component diagram.
2. [Startup and runtime](02-startup-and-runtime.md) — coremod, FML lifecycle, registration, proxies, ticks, persistence, and shutdown.
3. [ME network](03-me-network.md) — grids from first principles, topology, caches, channels, power, ticking, security, events, and persistence.
4. [Storage and crafting](04-storage-and-crafting.md) — typed stacks, federated storage, cells, monitors, planning variants, CPUs, and execution traces.
5. [Game objects and UI](05-game-objects-and-ui.md) — blocks, tiles, multipart parts, rendering, GUIs, containers, packets, and one complete click trace.
6. [Integrations, world, and services](06-integrations-world-and-services.md) — optional mods, recipes, world generation/data, commands, services, reflection, and transformer-only reachability.
7. [Development guide](07-development-guide.md) — prerequisites, verified Gradle tasks, test layers, debugging, and a first-contribution checklist.
8. [Refactor map](08-refactor-map.md) — ranked, evidence-backed pressure points with risks and prerequisite tests; no refactor is implemented.
9. [Glossary](09-glossary.md) — first-principles definitions for Minecraft, Forge, and AE2 vocabulary.
10. [Evidence index](10-evidence-index.md) — concept-to-interface/implementation/test/resource lookup table.

## Suggested reading paths

For a first contribution, read this page, [project map](01-project-map.md), [startup and runtime](02-startup-and-runtime.md), the subsystem you will change, then [development guide](07-development-guide.md). Use the [evidence index](10-evidence-index.md) as a jump table rather than reading it linearly.

For a network bug, go from [ME network](03-me-network.md) to either [storage and crafting](04-storage-and-crafting.md) or [game objects and UI](05-game-objects-and-ui.md), then consult the persistence and topology candidates in the [refactor map](08-refactor-map.md).

For addon or compatibility work, read the API boundary in [project map](01-project-map.md#public-api-versus-implementation), then the coremod and registration sections in [startup and runtime](02-startup-and-runtime.md), and finally [integrations](06-integrations-world-and-services.md).

## Scope and known uncertainty

The guide distinguishes a verified path from a plausible extension point. An **Uncertain** label means the current tree does not establish the answer without executing a particular mod combination or save migration. In particular, compile-only integrations cannot all be exercised in one local runtime, HorizonQA's reusable CI workflow is external to this repository, and old-world compatibility paths need representative historical saves before cleanup. The documentation identifies those missing proofs instead of filling them with assumptions.
