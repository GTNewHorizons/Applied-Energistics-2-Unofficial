# Architecture Modernization Specification

## Document status and authority

**Status:** implementation-grade architecture specification. Decisions labeled **Accepted decision** are normative for modernization work governed by this document. Items labeled **Proposal** are not approved designs and may not be used to justify a compatibility or authority change until their named gate is passed. Items labeled **Open decision** block the associated gate.

**Scope:** the server, shared, client, persistence, public API, integration, and build/test surfaces in this repository. This document does not authorize a Minecraft/Forge upgrade, a new wire protocol, a save-format epoch, or removal of a supported addon/API surface.

**Audience:** maintainers, reviewers, addon authors, and contributors implementing modernization slices.

**Authority in review:** when a modernization pull request conflicts with an accepted decision here, the pull request must either conform or update this document in a separately reviewable decision. This document does not override release policy or maintainer governance. The repository does not currently contain an authoritative compatibility range, supported-addon matrix, or numeric performance budget; those are explicit gate inputs below, not facts to infer.

**Evidence date:** 2026-08-10. Current-state claims are grounded in repository paths and symbols listed inline and in the evidence index. A target name prefixed with `[TARGET]` is intentionally absent from the current tree.

### Classification used throughout

- **Current state** — verified behavior or structure in this repository.
- **Constraint** — behavior or identity that must be treated as compatibility-sensitive until policy says otherwise.
- **Accepted decision** — the target direction to implement.
- **Migration mechanism** — the required way to move authority without two writers.
- **Proposal** — a plausible design that requires evidence or a decision before implementation.
- **Open decision** — a named uncertainty with an owner and blocking gate.

`MUST`, `MUST NOT`, `SHOULD`, and `MAY` are normative only inside accepted decisions and migration gates. They do not turn an unresolved compatibility assumption into a guarantee.

## Executive decision summary

The repository is a Forge 1.7.10 mod organized mostly by technical role (`tile`, `parts`, `me`, `container`, `core`, `integration`), with feature behavior spread across those packages. Runtime ownership is implicit: Forge owns the process and event lifecycle, `AppEng` and `Registration` compose global services, `WorldData` is a per-server/save static singleton, `TickHandler` owns global and per-world queues, and each `Grid` constructs its caches. Persistence is not one store: Minecraft chunk/item NBT, `AE2/settings.cfg`, grid-storage blobs, meteorite files, spatial IDs, and compass index files have different authorities and recovery properties.

The modernization will therefore use **feature slices inside the existing Forge shell**, not a framework rewrite or package-move campaign. The binding architectural rules are:

1. Every mutable artifact and operation has one declared authority and, if persisted, one writer.
2. Forge/Minecraft and optional-mod types stop at explicit feature boundary adapters when a slice is migrated; unchanged code is not required to be wrapped pre-emptively.
3. New pure policy depends on stable value types and narrow ports, never on tiles, containers, packets, static service locators, or concrete addon classes.
4. A compatibility ledger and identity snapshot precede authority-changing work.
5. A slice moves through characterize, introduce seam, shadow/compare where safe, cut over one entry point, soak, then remove. Shadow paths never mutate authoritative state.
6. Save compatibility, Java API compatibility, addon/reflection compatibility, wire compatibility, and gameplay equivalence are separate contracts with separate evidence.
7. Determinism is required where ordering is observable or needed for repeatable tests. It is not permission to change established seeded generation or priority behavior.
8. Target package and class names in this document are illustrative. Renaming or moving compatibility-sensitive classes is a distinct, gated change.

## 1. Vocabulary and boundary model

These definitions are review rules, not a request to create one interface for every class.

| Term | Meaning in this specification |
|---|---|
| **Feature** | A cohesive behavior with its own invariants and migration gate, such as grid topology, storage transfer, crafting, spatial transfer, or world generation. |
| **Host** | A Forge/Minecraft object with lifecycle callbacks: mod instance, world, tile, part, item, container, packet handler, or event subscriber. Hosts adapt lifecycle and data into a feature boundary. |
| **Runtime scope** | An explicitly created and destroyed owner for services with the same lifetime: process, server/save, world/dimension, grid, host, or operation. |
| **Service** | Feature-owned policy and orchestration. A service is not automatically a singleton and does not imply a service-locator framework. |
| **Port** | A narrow interface defined by the policy that consumes it, for an unstable dependency that must be substituted, observed, or tested. |
| **Adapter** | Code at the boundary that implements a port using Forge, Minecraft, an addon, legacy code, a packet, or a concrete store. |
| **Capability** | In this document, an authority-bearing operation exposed through a narrow interface. It is not automatically Forge's later capability system and is not the same thing as the existing `appeng.capabilities` package, which uses GTNHLib `CapabilityProvider`/ItemIO. |
| **Codec** | The owner of serialization semantics for one artifact or owned compound: version detection, decode, validation, residual-field policy, encode, and failure reporting. |
| **Store** | Persistence mechanics for one artifact family: file/config/NBT access, durability behavior, backup/quarantine, and flush lifecycle. A store does not decide gameplay policy. |
| **Authority** | The single component permitted to decide or mutate a state transition during a migration phase. |
| **Writer** | The single path permitted to persist an authoritative artifact during a migration phase. |
| **Snapshot** | An immutable, versioned input to pure or worker-safe computation. It is invalid for commit if its relevant revision/token has changed. |
| **Shadow path** | A non-authoritative path that observes captured inputs and produces a comparison result without world, inventory, energy, link, packet, or persistence mutations. |
| **Compatibility gate** | A review checkpoint that cannot pass on design intent alone; it requires the listed fixture, differential, fault, or integration evidence. |

## 2. Current architectural baseline

### 2.1 Platform and build baseline

**Current state:** this is a Minecraft 1.7.10 / Forge `10.13.4.1614` project built through the GTNH convention plugin. `gradle.properties` enables Jabel syntax while targeting Java 8 bytecode, declares `appeng_at.cfg`, and declares the coremod `transformer.AppEngCore`. `build.gradle`, `settings.gradle`, `dependencies.gradle`, and `addon.gradle` are the build evidence.

The dependency surface is deliberately broad. `dependencies.gradle` contains compile-only integrations for, among others, BuildCraft, Forestry, Forge Multipart, GregTech, IC2, OpenComputers, Waila, Railcraft, EnderIO, Mekanism, PneumaticCraft, BetterStorage, ComputerCraft, JABBA, Thaumic Tinkerer, and AE2 addons. A successful compile is therefore not evidence that all combinations are behaviorally supported.

Verified local Gradle tasks include `test`, `check`, `build`, `functionalTestClasses`, `functionalTestJar`, `runFunctionalTestServer`, and `runFunctionalTestClient`. `.github/workflows/build-and-test.yml` delegates CI to a GTNH reusable workflow with Horizon-QA and external dependency inclusion enabled. The exact Horizon-QA invocation lives outside this repository and must not be documented as a local command until it is added here.

### 2.2 Actual source organization

The current source tree is horizontal. This table describes what exists; it is deliberately separate from the target-only layout in section 4.4.

| Current area | Representative paths | Current responsibility and coupling |
|---|---|---|
| Public API | `src/main/java/appeng/api/**` | 300+ public API source files: definitions, grids, storage, crafting, parts, events, registries, and deprecated bridges. Addons implement and call these types. |
| Bootstrap/config/registration | `appeng.core.AppEng`, `appeng.core.Registration`, `appeng.core.AEConfig` | Forge lifecycle, content registration, event wiring, grid-cache registration, configuration, GUI/network singletons, integration startup. |
| World/save services | `appeng.core.worlddata.WorldData` and sibling `*Data` classes | Static per-server/save owner for player IDs, spatial dimensions, grid blobs, meteorite data, and compass service. |
| Grid graph and caches | `appeng.me.Grid`, `appeng.me.GridNode`, `appeng.me.cache.*` | Connected-network topology, cache construction, ticking, energy, channels/pathing, security, storage, crafting, P2P, spatial pylons, diagnostics. |
| Storage and transfer | `appeng.me.storage.*`, `appeng.util.inv.*`, `appeng.tile.storage.*`, `appeng.parts.automation.*` | Cell NBT, storage routing, external inventory adaptation, import/export/storage buses, IO port behavior. |
| Crafting | `appeng.crafting.*`, `appeng.crafting.fast.*`, `appeng.crafting.v2.*`, `appeng.me.cluster.implementations.CraftingCPUCluster` | Planning, links/watchers, pattern selection, execution, persistence, diagnostics. |
| Spatial | `appeng.spatial.*`, `appeng.tile.spatial.*`, spatial items/API | Region capture/swap, tile/entity/tick movement, dimension allocation, movable handlers, cell metadata. |
| World generation | `appeng.hooks.*`, meteorite/worldgen packages and world adapters | Quartz and meteorite generation, seeded placement, meteorite records, compass updates. |
| Forge hosts | `appeng.block.*`, `appeng.tile.*`, `appeng.parts.*`, `appeng.items.*` | Registry identity, NBT hosts, events, world mutation, player interaction. |
| UI and protocol | `appeng.container.*`, `appeng.client.*`, `appeng.core.sync.*` | Menus, reflection-derived GUI pairing, packet dispatch, enum-based IDs and payloads. |
| Optional integrations | `appeng.integration.*`, `appeng.fmp.*`, `appeng.capabilities.*` | Reflective module loading, optional interfaces, dynamic multipart classes, external inventory/item I/O. |
| Coremod/reflection | `appeng.transformer.*`, `appeng.core.api.ApiPart` | API repair, bytecode rewriting, exact class/method descriptors, generated multipart/layer classes. |
| Tests | `src/test/**`, `src/functionalTest/**`, `src/main/java/appeng/gametests/**` | Small JUnit 4 unit suite, Forge/JUnit 5 functional server harness, and Horizon-QA game tests. |

Feature ownership currently cuts across these rows. For example, an import bus spans a part host, tick cache, `InventoryAdaptor`, storage grid, energy grid, security source, external mod inventory semantics, and NBT/configuration. Package adjacency is not authority.

### 2.3 Current composition and lifetimes

`AppEng` is the Forge `@Mod` singleton. Its pre-init/init/post-init callbacks construct configuration, register content and grid caches, initialize stack network IDs and integrations, and install event handlers. `AppEng.serverAboutToStart` creates `WorldData`; stopping/stopped callbacks flush and clear world data and several global managers.

`WorldData` is a deprecated static singleton but has a real **server/save** lifetime, not a per-dimension lifetime. It locates the current save root through `DimensionManager`, creates `AE2/settings.cfg`, `spawndata`, and `compass`, and owns `PlayerData`, `DimensionData`, `StorageData`, `SpawnData`, and `CompassData`/`CompassService`.

`TickHandler.INSTANCE` owns:

- a server queue;
- per-`World` call queues in a weak map;
- crafting-calculation queues keyed by `World`;
- ready-tile queues;
- the collection of live `Grid` instances; and
- additional static/session state.

On world tick start it drains that world's call queue with an elapsed-time guard. On world tick end it gives crafting jobs a configured calculation budget. On server tick end it drains ready tiles and calls `Grid.update()`. World unload destroys grid nodes located in that world and removes the world queue; server stop clears several global collections. `TickManagerCache` is a separate per-grid priority scheduler for `IGridTickable` machines.

Each connected network is a `Grid`. A grid constructs registered caches, owns nodes and machines, registers with `TickHandler`, and is destroyed when it loses its pivot. `GridNode`/`GridPropagator` reconstruct topology from loaded hosts and coordinate join/split behavior. Grid runtime UUIDs are not persistent identity. `GridStorage` IDs connect reconstructed grids to serialized cache state.

The current ownership flow is:

```text
Forge process
  -> AppEng / Registration / static registries
  -> server start: WorldData(save root)
       -> settings.cfg stores, SpawnData, CompassService worker
  -> loaded World objects
       -> TickHandler world queues and hosts
       -> Grid instances spanning nodes (potentially across worlds)
            -> per-grid caches -> hosts/operations
```

### 2.4 Current state and writer inventory

| State/artifact | Current owner and writer | Lifetime | Authority class | Important current behavior |
|---|---|---|---|---|
| Block/tile/part state | Minecraft chunks plus tile/part `readFromNBT`/`writeToNBT` | Save/chunk/host | Authoritative | `AEBaseTile` and `CableBusContainer` produce owned fields/compounds; arbitrary unknown top-level keys are not generally retained. |
| Item/cell contents | `ItemStack` NBT, notably `CellInventory` | Stack | Authoritative | Cell slots/counts are mutated in existing compounds; malformed entries can cause repair writes. |
| Player numeric IDs | `PlayerData` in `AE2/settings.cfg` | Save | Authoritative identity mapping | New IDs save eagerly. Counter recovery behavior must be characterized before changing it. |
| Spatial dimension IDs/sizes | `DimensionData` in `settings.cfg`, item `StorageDim` | Save/cell | Authoritative identity/topology | Registers providers and persists on mutation. |
| Grid cache blobs/counter | `StorageData`/`GridStorage` in `settings.cfg` | Save/grid | Authoritative for persisted cache state | Base64 compressed NBT; written at world stop. Decode failures currently fall back to empty NBT. |
| Crafting CPU jobs | `CraftingCPUCluster` NBT nested in cluster tile state | Save/cluster/job | Authoritative | Persists inventory, tasks, links, waiting/missing state, listeners and diagnostics-related fields. Some listener state uses Java object serialization. |
| Meteorite generation records | `SpawnData` files in `AE2/spawndata` | Save/region | Authoritative generation history | Cached NBT; direct file overwrite on save/stop; read failure is logged and an empty compound is substituted. |
| Compass sky-stone index | `CompassService`/`CompassReader` under `AE2/compass` | Save/world | Derived index; full rebuild behavior is not yet proven | Single scheduled executor; readers close after inactivity/world unload; service shuts down on save stop. |
| Live grid graph and most caches | `Grid`, `GridNode`, `appeng.me.cache.*` | Grid | Authoritative runtime state | Reconstructed from loaded hosts; join/split invokes cache callbacks. Cache iteration uses maps/sets in several places. |
| Import/export transfer | Automation part plus storage/energy/security/external inventory implementations | Operation/host/grid | Authoritative gameplay mutation | Simulation followed by multiple modulations and compensation; no atomic cross-inventory transaction. |
| Spatial region transition | `TileSpatialIOPort`, spatial cell, `StorageHelper`, `CachedPlane` | Operation/world/save | Authoritative world mutation | Force-loads and swaps blocks, tiles, ticks, and entities in memory; no durable operation journal or automatic rollback. |

**Constraint:** the table is the initial writer ledger. A slice changing one row MUST state the exact before/after authority and writer. “Both while migrating” is not an acceptable after-state.

### 2.5 Grid, storage, crafting, and security behavior

`Registration` registers `TickManagerCache`, `EnergyGridCache`, `PathGridCache`, `GridStorageCache`, `P2PCache`, `SpatialPylonCache`, `SecurityCache`, `CraftingGridCache`, and `ItemFlowGridCache`. Cache construction is registry/reflection-driven. `Grid.update()` invokes cache updates; join/split transfers selected state through `IGridStorage`. Persistence is cache-specific, not a general grid snapshot.

`GridStorageCache` builds network inventories/monitors from priority-ordered cell providers. `NetworkInventoryHandler` routes `SIMULATE` and `MODULATE` operations and consults security. It cannot make external handlers transactionally atomic. `InventoryAdaptor` is the present integration seam for GTNHLib ItemIO/capability providers, vanilla/sided inventories, fluids, multipart, and specific optional mods; it also has an identity-based per-server-tick cache.

Import and export buses use compensating protocols. For example, import simulates external extraction and network insertion, then removes externally, spends energy/inserts into the network, and returns remainder externally. Export simulates the destination, extracts from the grid, inserts externally, and reinjects leftovers. Reentrancy, inaccurate simulation, partial acceptance, unload, and exception behavior are compatibility and conservation risks.

Crafting has **two current planning implementations**: `CraftingJobFast` and `CraftingJobV2`. `CraftingGridCache.beginCraftingJob` chooses fast for lite mode and v2 otherwise. The historical v1 `CraftingJob`/tree implementation is absent from the current tree; repository history at `8c34108a1` records its removal. “Legacy crafting” now refers to API overloads/data views and shared execution/save surfaces, not a third live planner. Fast and v2 intentionally differ in supported semantics, so differential results require a declared equivalence domain.

Both current planners are scheduled through `TickHandler`; fast returns a completed calculation and v2 consumes the world-end budget incrementally. `CraftingGridCache` declares a static cached executor, but the inspected current planner schedule paths do not submit to it. A future worker planner is therefore a proposal, not a description of current behavior. Crafting execution is shared in `CraftingCPUCluster`, is tick-driven, mutates storage/energy/medium state, and persists active jobs. Planning and execution must be migrated separately.

`SecurityCache` is per grid. It selects a single security provider, delays availability for startup, maintains player permissions and a security key, and resolves player numeric IDs through static `WorldData`. Its static operator-player set is process state requiring explicit clearing/ownership evidence. Security is part of every mutation boundary, not a UI-only feature.

### 2.6 Spatial, generation, client, and integration behavior

Spatial transfer starts at `TileSpatialIOPort`, validates the cell/region/power, queues through the server tick handler, delegates to `ISpatialStorageCell`, then uses `StorageHelper.swapRegions` and `CachedPlane`. Blocks/metadata, tile NBT, scheduled ticks, entities, riding relationships, and addon-provided movable handlers participate. Failures are caught in several low-level paths, but there is no persisted intent/phase journal from which an interrupted transition can be completed or rolled back. A journal is a high-value proposal whose format and recovery algorithm still require design and failure tests.

Quartz generation consumes Forge-provided `Random` in its established order. Meteorite generation uses `Platform.seedFromGrid`, queued generation jobs, persisted meteorite settings, and separate seeded randomness for placement/loot/decay. `IMeteoriteWorld`, `StandardWorld`, and the chunk-only adapter are already a useful world boundary, but they combine reads and writes. Changing random call order, block numeric IDs stored in meteorite records, search order, or old-world `SpawnData` interpretation can change existing-world behavior.

Client and network identity is tightly coupled to source structure and enum order:

- `AppEngPacket.getPacketID()` uses `PacketTypes.ordinal()` and reception indexes the enum values.
- `GuiBridge` encodes its ordinal and derives GUI classes from container class/package names.
- several payload enums are written by ordinal;
- `AEStackTypeRegistry` assigns network IDs after built-ins, sorting addon type string IDs; the installed type set affects the table.

The integration registry is an ordered singleton of reflective `IntegrationNode`s created from `IntegrationType` names and `appeng.integration.modules.*`. The coremod and multipart layer system add further source-name constraints: `AEApi` reflectively loads `appeng.core.Api.INSTANCE`; `ApiPart` generates layer classes; `AppEngCore`, `ApiRepairer`, `ASMIntegration`, `ASMTweaker`, and `GuiButtonColorizer` depend on class names, descriptors, or bytecode shape. FMP uses persistent string identifiers and generated cable-bus tile classes. Package moves are therefore compatibility work, not cleanup.

### 2.7 Current global/static hazards

The following are representative, not an exhaustive blacklist:

- `WorldData`, `TickHandler`, `IntegrationRegistry`, public registries, and the Forge mod instance;
- crafting rebuild pause state and cached executor in `CraftingGridCache`;
- operator players in `SecurityCache`;
- network-monitor recursion depth/global state;
- dynamic tile/layer handler maps and inventory-adaptor caches;
- spatial entanglement dimension maps;
- packet chunk/reassembly buffers, locatable registries, notification managers, and client resources.

**Accepted decision:** do not replace all statics. Classify each by lifetime and mutability. Immutable constants and Forge-owned registries may remain static. Mutable save/grid/operation state must either move under an explicit runtime owner in its slice or have a documented lifecycle reset and a test proving it.

### 2.8 Current verification assets and gaps

Current assets include:

- a small JUnit 4 unit suite under `src/test/java`;
- a Forge/JUnit 5 server harness under `src/functionalTest/java`, launched by `AppengTestMod` and used by crafting-v2 and network-inventory tests;
- Horizon-QA tests under `src/main/java/appeng/gametests`, including storage cells, import/export, storage bus, IO port, crafting execution, interface behavior, grid/network, P2P, power, and AE2FC compatibility;
- Horizon-QA structures under `src/main/resources/assets/appliedenergistics2/horizonqastructures`;
- crafting execution tests that already check conservation across completion, cancellation, and CPU destruction.

Not found as repository-owned infrastructure: a released-save fixture corpus, identity manifest/snapshot, malformed/custom-store fault-injection suite, addon compatibility matrix, wire-compatibility harness, architecture/import rule, deterministic worldgen golden corpus, or numeric performance baselines. These are migration deliverables; reviews MUST NOT describe them as already present.

## 3. Compatibility contract

### 3.1 Separate contracts

**Accepted decision:** every change declares which of these contracts it touches. Passing one does not imply another.

| Contract | What it covers | Default during modernization | What remains open |
|---|---|---|---|
| Save/data | Released chunk/tile/part/item NBT, `settings.cfg`, grid blobs, crafting jobs, meteor records, spatial cells/dimensions | Read the selected support corpus without loss; write legacy-representable state until a format epoch is approved | Earliest supported release and downgrade window (OD-01) |
| Registry identity | Block/item/tile/entity/part/material/FMP names and numeric/meta identities | Preserve the captured identity snapshot exactly | Whether manifest later becomes registration source (OD-05) |
| Java API | Public `appeng.api`, implemented interfaces, deprecated bridges, extension registries | No accidental source/binary break; deliberate changes require policy and addon compile/runtime evidence | Supported API versions and binary guarantees (OD-02) |
| Reflection/bytecode | Coremod descriptors, `AEApi` lookup, generated layer/FMP classes, IMC class names, GUI derivation, serialized class names | Treat class/package/descriptor as protocol until inventory proves otherwise | Allowed relocation/bridge strategy (OD-03) |
| Wire/session | Packet IDs/payloads, GUI IDs, stack type network IDs | Same-build client/server is the only assumption evidenced here; preserve IDs within a supported session policy | Cross-version and differing-addon-set policy (OD-04) |
| Addon/modpack | Optional integrations, foreign inventories, multipart, addon NBT and implementations of public interfaces | Preserve characterized supported combinations and isolate absent/failed modules | Supported addon/mod/version matrix (OD-02) |
| Gameplay | Resource conservation, permission results, priority, tick phase, redstone behavior, seeded generation, failure semantics | Preserve characterized observable behavior unless an approved correction is called out | Which historical quirks are intentional (OD-06) |
| Performance | Tick time, crafting planning latency, memory, save/start/stop time, transfer throughput | No material regression against captured baselines; no invented numeric threshold | Workload corpus and budgets (OD-07) |

### 3.2 Save and data rules

1. A migration MUST inventory an artifact's owner, current writer, current decode failures, and actual unknown-field behavior before changing its codec or store.
2. Until OD-01 is resolved, fixtures SHOULD include every released lineage maintainers reasonably intend to support, but “all historical versions” is not a promise.
3. New code MUST distinguish missing, malformed, unsupported-version, and valid-empty data. Silent substitution with empty state is not a general recovery policy.
4. A read-repair or migration write is an authoritative mutation. It requires backup/fixture evidence and may not happen from a shadow path.
5. While rollback to an older binary is required, the new writer MUST emit a representation the older binary understands, or the feature must remain read-only. A new format epoch requires explicit forward/backward/downgrade policy and release tooling.
6. Data owned by Minecraft chunk/item persistence uses that lifecycle. Do not add an independent file writer for it.

### 3.3 Unknown and addon-owned NBT

The current implementation does not provide blanket unknown-key round trips. `AEBaseTile` and `CableBusContainer` generally write their owned view; `CellInventory` often mutates existing item compounds. Therefore:

- Each codec MUST declare owned keys/compounds, shared keys, and foreign/residual keys.
- Unknown fields in a shared or addon-owned compound MUST be retained byte-for-byte or structurally equivalently when feasible and covered by a fixture.
- Unknown fields inside an exclusively AE2-owned compound MAY be dropped only when the codec contract says so; stale known keys MUST NOT be retained merely because they were unknown to a previous version.
- Foreign tile/part/item data MUST never be normalized by a shadow reader.
- If safe residual preservation cannot be implemented for a specific artifact, that limitation is a gate decision, not a hidden fallback.

### 3.4 Identity inventory

The first identity snapshot MUST cover at least:

- block and item registry names, metadata/damage assignments, and class-derived feature names;
- tile entity and entity registration identifiers;
- `MaterialType`, `PartType`, `EntityIds`, and Forge Multipart strings;
- `PacketTypes`, `GuiBridge`, payload enum ordering, and stack type string/network-ID tables for each tested addon set;
- spatial provider/biome/dimension identifiers and item keys;
- API/reflection/coremod class names and method descriptors;
- Java-serialized crafting listener class descriptors and other class-name-bearing NBT/IMC data.

`FeatureNameExtractor`, `AEBlockFeatureHandler`, `ItemFeatureHandler`, `AETileBlockFeatureHandler`, `AEStackTypeRegistry`, `ApiPart`, and the transformer classes are required inventory anchors.

**Accepted decision:** the initial manifest is a generated validation snapshot compared with actual runtime registration. It does not become the registration authority in the same slice. That later inversion is proposal P-03 and requires Gate G4.

### 3.5 Behavioral equivalence and corrections

A modernization slice MUST list observable invariants and known quirks. Resource-conservation invariants take priority: item/fluid amount, energy charged, crafting link/job state, security result, and world block/entity/tick state may not be duplicated or lost across normal completion, cancellation, unload, restart, exception, or rollback.

Bug fixes discovered during extraction SHOULD be separated from structural cutover. If separation is unsafe, record the old behavior, the desired correction, affected saves/addons, and targeted regression tests in the pull request. “Cleaner” is not a compatibility argument.

## 4. Accepted target architecture

### 4.1 Architectural style

**Accepted decision:** keep the Forge shell and migrate vertical feature slices toward a ports-and-adapters boundary. The target is a disciplined monolith, not microservices, a universal event bus, a dependency-injection framework, or a rewrite of Minecraft abstractions.

```text
Forge/Minecraft/addons
        |
  hosts and adapters  <---- legacy adapters during migration
        |
  feature application service ---- codec/store owned by that feature
        |
  pure policy + value types ---- narrow consumed ports
        |
 explicit runtime owner (save/grid/host/operation scope)
```

Existing classes may play more than one box until their slice migrates. A boundary is justified when it changes authority, isolates an unstable dependency, enables a required fixture, or separates pure planning from mutation. It is not justified solely to reduce method size or create symmetry.

### 4.2 Dependency rules

| Rule | Allowed | Prohibited | Example and enforcement |
|---|---|---|---|
| D1: policy owns its ports | Feature policy imports its own port/value types; adapters implement them | Policy imports `TileEntity`, `World`, packet/container classes, concrete addon classes, or integration singletons | A crafting snapshot reader implements a crafting-owned read port. Enforce with import rules once target packages exist. |
| D2: hosts are inward callers | Tile/part/container/event/packet hosts call a feature application service | Policy calls back into a host to discover dependencies or uses static service lookup | Import bus host receives a transfer service/capability from its runtime scope. |
| D3: features communicate through narrow contracts | Stable value/query/command types with declared authority | Imports of another feature's tiles, caches, containers, or mutable collections | Crafting requests storage simulation through a storage read port; execution commits through an authorized storage capability. |
| D4: public API is a compatibility boundary | Adapters implement/translate public API; public value contracts may be shared deliberately | Moving internal implementation into `appeng.api` or changing API types to fit internals | Addon interface implementation remains at boundary. API compilation/runtime fixtures enforce. |
| D5: platform stays at the edge | Forge registration, lifecycle, networking, NBT primitives, world access in hosts/adapters/codecs | Pure algorithms depend on Forge event buses or global registries | Worldgen policy may consume a stable seeded view; adapter performs block writes. |
| D6: one authority and writer | One named mutation path and one named persistence path per phase | Dual writes, bidirectional synchronization, “new wins except...” rules | Shadow planner returns comparison only. Writer ledger is a required PR artifact. |
| D7: lifetimes are explicit | Constructor/factory ownership, close/flush/destroy in matching lifecycle | Mutable save/grid state held indefinitely in process statics | Server/save runtime is created at `serverAboutToStart` and destroyed at server stop. Lifecycle tests enforce. |
| D8: ordering is explicit when observable | Stable priority/tie-break keys and documented tick phase | Depending accidentally on `HashMap`/`HashSet` order where output differs | Storage routing preserves priority and adds a stable characterized tie-break only if current equivalence permits. |
| D9: optional integrations fail at their boundary | Absent/failed module yields disabled adapter and diagnostic | Optional class loading in core policy or partial activation after a failed stage | Integration fixtures and dedicated-server startup enforce. |
| D10: package moves are migrations | Bridge/forwarder plus identity/reflection tests | Mechanical relocation of API, tile, container, packet, transformer, FMP, or serialized classes | Each move names identity impact and rollback independently. |

Temporary exceptions MUST name the importing symbol, why the dependency cannot yet be inverted, the migration slice that removes it, and an automated check preventing new occurrences.

### 4.3 Runtime ownership and lifetimes

The type names below are target roles, not required final class names.

| Scope | Current creator/owner | Accepted target responsibility | Creation and teardown proof |
|---|---|---|---|
| Process/mod | Forge -> `AppEng`, static registration/API/integration structures | Immutable definitions, registration metadata, process-safe adapters; no save/grid mutation | Dedicated client/server startup; two server lifecycles in one JVM where harness permits |
| Server/save | `AppEng.serverAboutToStart` -> `WorldData` | `[TARGET] ServerRuntime` owns save-root services, stores, executors, and world-scope registry; `WorldData` may be a compatibility facade temporarily | Created after save root exists; flush/close on stopping; references/caches cleared on stopped |
| World/dimension | Forge `World`, `TickHandler` maps, compass readers | `[TARGET] WorldScope` is a lightweight handle for queues/revisions/cancellation, not an alternate universe model | Created lazily/load event; cancels queued work and releases readers on unload |
| Grid | `Grid` constructs caches and registers with `TickHandler` | Current `Grid` remains aggregate initially; `[TARGET] GridRuntime` role makes cache/service ownership and join/split transfer explicit | Created on graph formation; deterministic cache lifecycle; destroyed on empty grid; split/join fixtures |
| Host | Forge creates tile/part/item/container | Host keeps NBT and lifecycle adaptation; feature service owns policy | Chunk load/unload/invalidate/reload tests |
| Operation | Planner job, transfer attempt, spatial transition | Immutable request/snapshot plus explicit cancellation/revision/commit token; no static operation state | Completion, cancellation, unload, restart, stale-revision, and exception tests |

**Accepted decision:** introduce ownership from the outside inward. The first runtime slice wraps existing `WorldData` stores and `TickHandler` lifecycle without changing file formats or tick phases. A new runtime may not become a service locator exposed throughout the codebase.

### 4.4 Target-only source shape and current-to-target map

This is a navigation goal, not a demand for immediate relocation. All paths in this block are `[TARGET]`:

```text
appeng/
  bootstrap/                 Forge composition and runtime construction
  platform/                  Minecraft/Forge/addon adapters
  feature/
    grid/                    topology, cache lifecycle, scheduling boundary
    storage/                 cell/storage capabilities and transfer policy
    crafting/                plan, execution, links, codecs
    security/                identities, permissions, authorization
    spatial/                 transition planning, execution, recovery
    worldgen/                seeded generation policy and world adapters
    network/                 protocol adapters and stable identity tables
  persistence/              only shared mechanics, not feature schemas
  api/                      existing public compatibility surface
```

| Current ownership cluster | Target feature owner | Initial move |
|---|---|---|
| `AppEng`, `Registration`, `WorldData`, `TickHandler` | bootstrap/runtime plus feature-owned services | Add explicit composition/lifecycle facade; do not package-move first |
| `Grid`, `GridNode`, `me.cache.*` | grid plus feature cache adapters | Inventory cache state/join/split; extract one query or lifecycle seam at a time |
| `me.storage.*`, automation parts, `InventoryAdaptor` | storage | Wrap current routing/external adapters; first authority cutover at one bus operation |
| planners, `CraftingGridCache`, `CraftingCPUCluster` | crafting | Encapsulate current planner selection before replacement; execution remains legacy authority |
| `SecurityCache`, player IDs | security with save identity port | Remove direct static lookup only after server/save runtime exists |
| spatial tiles/items/helpers | spatial | Characterize and split read/plan/write; journal remains gated proposal |
| worldgen hooks/adapters/meteor data | worldgen | Golden tests first; retain existing seed/random sequence until explicit epoch |
| packets/GUI/container/client | network/client adapter layer | Freeze IDs and reflection names first; no blanket DTO rewrite |
| integration/FMP/capabilities | platform adapters per consuming feature | Preserve public/reflective entry points; isolate failures and concrete types |

### 4.5 Central design principles for code review

- Prefer cohesive feature state over horizontal “manager”, “util”, or “common” packages.
- Prefer immutable request/result values at a boundary; do not clone the entire live grid/world into a model.
- Define a port at the point of use and only when at least one real adapter/test substitution exists.
- Preserve `SIMULATE`/`MODULATE`, security, energy, priority, notification, and compensation semantics explicitly at mutation boundaries.
- Keep Forge events and public API events at the platform/API edge. Do not introduce a second general event system.
- Selective static state is acceptable when immutable or Forge-owned. Mutable static state requires lifetime/reset evidence.
- No “repository”, “service”, “factory”, or “runtime” type without a stated owner, lifetime, and failure behavior.

For a new class, reviewers apply this placement test in order:

1. A Forge callback, tile/part/item/container, packet handler, or concrete addon call stays in the existing host area or the owning feature's `[TARGET] platform` adapter.
2. Rules and invariants used by one feature belong to that feature even if their inputs originate in several technical packages.
3. A feature schema/codec stays with its feature. Shared persistence contains only mechanics already required by at least two features with the same durability semantics.
4. Process composition and runtime construction belong in `[TARGET] bootstrap`; runtime lookup does not.
5. A type enters `appeng.api` only through an intentional public-API decision with compatibility evidence.
6. A cross-feature utility is rejected unless it is pure, has a stable vocabulary, and has at least two real consumers; otherwise it belongs to the consuming feature.

## 5. Feature-boundary migration matrix

Each subsection is an operational review card. “Cutover” means the entry point named there; it never means the entire repository at once.

### 5.1 Grid graph and cache lifecycle

| Field | Specification |
|---|---|
| Current anchors | `Grid`, `GridNode`, `GridPropagator`, `Registration` cache registration, `TickHandler`, `appeng.me.cache.*` |
| Current owner/state | `Grid` owns nodes, machines, runtime UUID, `GridStorage`, and cache instances. Hosts reconstruct topology. Caches own feature state with different join/split/persistence behavior. |
| External dependencies | Forge world/chunk lifecycle, public grid/cache APIs, tile/part nodes, TickHandler phase, persisted grid ID in node NBT |
| Compatibility-sensitive behavior | Split/join membership, cache transfer, event order, channels/pathing, tick phase, security key propagation, grid-storage ID reuse, addon caches |
| First seam | Read-only `[TARGET] GridView`/revision for diagnostics or planning, built from current grid without changing topology authority |
| Shadow/compare | Compare membership, cache keys, path/channel results, and emitted events on captured topology fixtures; shadow never attaches nodes |
| Authority prerequisite | G5 plus explicit grid revision semantics, addon-cache policy, and split/join/unload/multi-world evidence. The first query cutover does not transfer topology authority |
| Cutover/rollback | Cut over one query consumer behind a startup-latched flag; rollback routes to current cache. Topology mutation remains current until a later gate |
| Exit evidence | Multi-world join/split, chunk unload/reload, server restart, addon cache, and deterministic event/tick characterization |
| Principal risks | Hidden cache coupling, `HashMap` order, cross-world grids, stale node references, public addon caches, grid-storage split semantics |

### 5.2 Storage cells and automation

| Field | Specification |
|---|---|
| Current anchors | `GridStorageCache`, `NetworkInventoryHandler`, `CellInventory`, `InventoryAdaptor`, import/export/storage bus and IO-port classes |
| Current owner/state | Per-grid storage cache routes providers; cells persist stack/count NBT; each automation host coordinates storage, external handler, energy, and security |
| External dependencies | Vanilla/sided inventories, GTNHLib ItemIO, fluids, optional mods, multipart, public storage interfaces, chunk/tick lifecycle |
| Compatibility-sensitive behavior | Priority/tie behavior, fuzzy/substitution rules, `SIMULATE` accuracy, partial transfer, energy charge, permission source, watcher notifications, cell NBT/meta |
| First seam | Transfer observation/result model around one import-bus item path, then one authoritative `[TARGET] ItemTransferCapability` with legacy adapters |
| Shadow/compare | Observe proposed steps and conservation result from captured pre-state. Do not call external simulate twice in runtime shadow unless adapter is proven side-effect-free |
| Authority prerequisite | G4: M0–M2 complete, one-operation writer ledger, adversarial external-handler fixtures, conservation/permission/energy evidence, and rollback drill |
| Cutover/rollback | One bus mode and item channel, startup-latched. New service becomes sole coordinator; current inventory/storage adapters remain concrete mutation implementations. Rollback restores old coordinator before save load |
| Exit evidence | Exact/partial/full destination, lying/reentrant/throwing handler, unload, permission denial, insufficient energy, compensation failure, addon inventory, conservation across restart |
| Principal risks | External handler contract violations, reentrancy, duplicate notifications, dual energy charge, cell read-repair writes, recursion guards |

### 5.3 Crafting planning and execution

| Field | Specification |
|---|---|
| Current anchors | `CraftingGridCache`, `CraftingJobFast`, `CraftingJobV2`, `TickHandler`, `CraftingCPUCluster`, crafting links/watchers/pattern APIs |
| Current owner/state | Cache owns patterns/providers/CPUs/links; fast or v2 job plans; CPU cluster executes and persists active work |
| External dependencies | Grid storage/energy/security, world, patterns/mediums, public API callbacks, tick budgets, Java serialization in persisted listener state |
| Compatibility-sensitive behavior | Planner selection, substitutions/ore dictionary, multi-output/remainders, priority, missing report, cancellation, link identity, persisted CPU jobs, conservation |
| First seam | Internal planning service that preserves current fast/v2 routing and exposes normalized diagnostics; no new algorithm in the seam slice |
| Shadow/compare | Compare the same planner through direct and service routes first. Cross-planner or replacement comparison is limited to an explicitly defined equivalence corpus |
| Authority prerequisite | G3 for routing through current planners; G5 plus revision/stale/cancellation evidence for any replacement planner. Execution authority moves only in its own later slice |
| Cutover/rollback | Cut over `beginCraftingJob` call sites to the service while both adapters invoke current implementations. Execution remains `CraftingCPUCluster`. Startup-latched fallback restores direct routing |
| Exit evidence | Existing functional/game tests plus substitution, priority, multi-output, recursive pattern, cancellation, CPU break, restart-active-job, stale snapshot, timeout/budget baselines |
| Principal risks | Calling “legacy” a nonexistent planner, snapshot staleness, static rebuild state/executor lifecycle, serialized listener class names, planning/execution authority confusion |

**Proposal P-01:** a worker-safe planner may later consume immutable storage/pattern/security revisions and return a declarative plan. The server thread must revalidate revisions and remain the sole commit authority. This requires G5; it is not a prerequisite for the first crafting seam.

### 5.4 Security and identity

| Field | Specification |
|---|---|
| Current anchors | `SecurityCache`, `PlayerData`, security API/source types, grid-node security key fields |
| Current owner/state | Per-grid permissions/provider/key plus save-wide player numeric mapping and process-static operator set |
| External dependencies | Server operator events, `GameProfile`, grid lifecycle, `WorldData`, public API action sources |
| Compatibility-sensitive behavior | Startup delay, zero/one/multiple provider behavior, owner/default permissions, CRAFT requiring EXTRACT, player ID stability, node key propagation |
| First seam | Save-owned player identity port injected into existing `SecurityCache`; current permission algorithm remains authority |
| Shadow/compare | Compare decisions for a matrix of provider/default/operator/action-source states; no duplicate events or key writes |
| Authority prerequisite | G2, stable player-ID fixtures, complete permission/provider/startup decision matrix, and proof every migrated mutation capability authorizes before commit |
| Cutover/rollback | Replace static `WorldData` lookup only after runtime slice; rollback returns adapter to existing facade with same file writer |
| Exit evidence | Restart ID stability, operator login/logout and second server lifecycle, grid split/join, provider churn, denied mutation tests |
| Principal risks | Fail-open behavior during startup, static operator leakage, ID counter recovery, security checks bypassed by new capabilities |

### 5.5 Spatial transfer

| Field | Specification |
|---|---|
| Current anchors | `TileSpatialIOPort`, spatial cell APIs/items, `DimensionData`, `StorageHelper`, `CachedPlane`, `NBTSpatialHandler`, movable registry |
| Current owner/state | Operation spans source/destination worlds and cell; current helper directly swaps blocks/tiles/ticks/entities and later charges/moves cell slots |
| External dependencies | Forge dimension/chunk loading, numeric block IDs, entities/riding, scheduled ticks, addon movable handlers, power/grid, cell NBT |
| Compatibility-sensitive behavior | Region bounds, source/destination mapping, tile NBT, remaining tick delay, entities, handler decisions, storage dimension IDs, interruption outcomes |
| First seam | Read-only transition preflight and manifest of intended coordinates/entities/ticks; current swap remains sole writer |
| Shadow/compare | Compare preflight with current validation and post-transition audit on fixtures. Never run a second spatial mutation |
| Authority prerequisite | G7, including accepted interruption policy, durable phase model, invertibility analysis, and crash/restart evidence at every phase |
| Cutover/rollback | No authority cutover until recovery design survives crash-boundary fault tests. Once gated, one operation state machine owns all writes; rollback is journal recovery, not invoking old and new swaps |
| Exit evidence | Crash/fault at every durable phase, restart recovery, chunk-load failure, movable addon, tile exception, entity/riding, scheduled ticks, full state conservation |
| Principal risks | Irreversible partial world mutation, save corruption, force-loading, addon behavior, no current journal, enormous fixture state space |

**Proposal P-02:** use a persisted transition journal with intent, validated manifest/hash, phase, and completion marker. Do not choose file layout, fsync semantics, or rollback algorithm until the target filesystems/Java runtime and Minecraft save ordering are tested. Spatial remains a late migration.

### 5.6 World generation

| Field | Specification |
|---|---|
| Current anchors | `QuartzWorldGen`, `MeteoriteWorldGen`, `MeteoritePlacer`, `IMeteoriteWorld`, `StandardWorld`, chunk-only adapter, `SpawnData`, compass update calls |
| Current owner/state | Forge chunk generation plus queued meteorite work; `SpawnData` is authoritative generated/history data; compass files are derived index |
| External dependencies | Forge `Random`, world/chunk writes, config/loot, numeric block IDs, server tick queue, save files |
| Compatibility-sensitive behavior | Seed derivation, random call order, candidate/search order, placement geometry, loot/decay, already-generated chunks, old meteor records |
| First seam | Capture/replay read decisions and planned block effects for fixed seeds/configs while current generator remains writer |
| Shadow/compare | Generate against isolated fixture worlds or recording adapters, never twice in a live chunk |
| Authority prerequisite | G6, including golden seeds/configs/chunks, explicit old-world unexplored-chunk policy, duplicate-prevention restart tests, and performance baseline |
| Cutover/rollback | Separate quartz and meteorite toggles; latch before world load; preserve `SpawnData` writer and random sequence in first cutover |
| Exit evidence | Golden seed/config/chunk corpus, old-world unexplored chunks, restart and duplicate-prevention, compass rebuild, performance baseline |
| Principal risks | Silent terrain divergence, order-sensitive RNG, existing-world seams, loot config changes, confusing derived compass data with authority |

### 5.7 Client, GUI, and network protocol

| Field | Specification |
|---|---|
| Current anchors | `AppEngPacket`, `PacketTypes`, `GuiBridge`, `NetworkHandler`, containers, client GUI derivation, `AEStackTypeRegistry` |
| Current owner/state | Static handlers and enum tables dispatch packets/GUI; containers own synchronized menu state |
| External dependencies | Netty/Forge networking, sided class loading, addon stack types, class-name reflection |
| Compatibility-sensitive behavior | Enum ordinal IDs, payload field order/width, chunking, validation, GUI/container pairing, addon-set stack ID table |
| First seam | Generated protocol/GUI identity snapshot and packet boundary tests; no ID reassignment |
| Shadow/compare | Encode/decode golden payloads and side-validation tests; shadow handling may parse but must not execute commands |
| Authority prerequisite | G1 and a resolved OD-04 policy for the affected packet/GUI/stack-type surface, plus identity, authorization, malformed-input, and sided-classloading evidence |
| Cutover/rollback | Per packet family after identity freeze; old and new handlers cannot both perform the action |
| Exit evidence | Dedicated server classloading, malformed/oversized packet, unauthorized action, same-build addon sets, reconnect/reload |
| Principal risks | Ordinal drift, class relocation, client-only load on server, differing registry sets, dual command execution |

### 5.8 Integrations, public API, and multipart

| Field | Specification |
|---|---|
| Current anchors | `appeng.api`, `IntegrationRegistry`, `IntegrationNode`, `IntegrationType`, `ApiPart`, FMP registries, `ASMIntegration`, `ApiRepairer`, `ASMTweaker` |
| Current owner/state | Process registries/modules; addons implement public interfaces and provide foreign storage/parts/handlers; coremod transforms optional shapes |
| External dependencies | Named mod IDs/versions, reflection/class loaders, IMC, bytecode descriptors, generated classes, Java 8 |
| Compatibility-sensitive behavior | API linkage, absent/failed module isolation, module order/stages, FMP strings/tile IDs, transformed method signatures, reflection names |
| First seam | Compatibility inventory plus compile/runtime fixtures for selected addon matrix; feature-owned adapters wrap concrete integrations |
| Shadow/compare | Read-only capability discovery and equivalent operation results in isolated pack fixtures; never register identifiers twice |
| Authority prerequisite | G0/G1 with OD-02/OD-03 resolved for the affected integration, a versioned addon fixture, and absent/failed-module behavior characterized |
| Cutover/rollback | One integration consumer at a time, preserving public/reflection entry point as bridge. Rollback reselects legacy adapter at startup |
| Exit evidence | Dedicated client/server startup, absent mod, supported versions, deliberate module failure, addon API compile/link/run, FMP load/save |
| Principal risks | Unspecified support matrix, compile-only false confidence, eager classloading, binary break, coremod descriptor drift, generated class identity |

## 6. Persistence and identity design

### 6.1 Authoritative versus derived state

**Accepted decision:** recovery follows authority classification.

- Authoritative state is never silently rebuilt from a guess. Decode failure produces a structured diagnostic and follows an artifact-specific fail/quarantine/recovery policy.
- Derived state, such as compass indexes, may be discarded and rebuilt if its source and rebuild completeness are proven.
- Cached runtime state may be dropped only if reconstruction preserves observable behavior. A cache that stores energy or active crafting state is not merely a cache.
- Configuration and recipe/loot inputs influence behavior but are not save-state replacements.

### 6.2 Codec contract

Every new or extracted codec MUST define:

1. artifact name, authority owner, writer, and lifecycle;
2. format/version detection without relying on a fabricated version field;
3. required and optional fields, numeric bounds, enum/ID mapping, and defaults;
4. missing versus malformed versus unsupported handling;
5. owned/shared/foreign field policy;
6. legacy read and legacy-compatible write behavior;
7. whether decoding can trigger repair, and who authorizes the write;
8. deterministic encoding where byte/content stability matters;
9. fixtures for valid, missing, malformed, unknown-field, and previous-release cases;
10. an operator-visible diagnostic that does not leak or spam raw payloads.

### 6.3 Store and durability contract

There is no universal “atomic save” mechanism in the current repository. The target rule is proportional:

| Artifact family | Required migration approach |
|---|---|
| Minecraft chunk/tile/part and item NBT | Use Minecraft's save lifecycle; test host invalidation/unload and read/write compatibility. Do not create parallel files. |
| `AE2/settings.cfg` and grid blobs | Keep `StorageData`/Forge `Configuration` as sole writer initially. Add fixture/fault evidence before selecting backup, temporary-file, checksum, or replacement mechanics. |
| Meteorite `spawndata` | Characterize partial/truncated/write-failure behavior first. A new store must provide tested backup/quarantine/recovery and one writer; direct-overwrite replacement is a gated slice. |
| Compass files | Treat as derived only after proving a complete rebuild trigger. Shut down worker/readers, quarantine/delete invalid index, and rebuild from authoritative world data with observable progress; until that proof exists, preserve the files. |
| Spatial transition state | No current journal. Any new journal is authoritative operation data and requires phase-by-phase crash recovery tests before it can guard a cutover. |

“Write temp, fsync, rename” is not a portable guarantee by itself. If selected for a custom file, tests must cover the actual Java 8/filesystem behavior, directory entry handling where available, backup selection, interrupted rename, and startup recovery.

### 6.4 One-writer protocol

For every authority-changing pull request, include this ledger:

| Phase | Reader(s) | Authority | Persistent writer | Shadow mutation allowed? | Rollback route |
|---|---|---|---|---|---|
| Before | Current paths | Current path | Current writer | No | Current path |
| Shadow | Current + shadow reader | Current path | Current writer | No | Disable shadow |
| Cutover | Current compatibility reader + new path | New path for named entry point | Exactly one named writer | No | Startup-latched route to current before opening save/accepting work |
| Soak | As needed for comparison | New path | New writer | No | Version-compatible fallback or release rollback plan |
| Removal | Supported readers only | New path | New writer | No | Release-level rollback policy |

If the rollback binary cannot read state produced after cutover, the feature has crossed a format epoch and must meet OD-01/G4 before release.

### 6.5 Identity-manifest rollout

1. **Discover:** instrument/test actual registration and scan explicit/class-derived/reflection/serialized identities.
2. **Snapshot:** commit a stable, reviewable generated fixture with provenance and addon-set metadata.
3. **Verify:** CI fails on unapproved drift. This is the first accepted role.
4. **Explain changes:** approved changes include migration, alias/bridge, save/protocol/API effect, and rollback policy.
5. **Proposal only:** after G4, selected registries may consume a manifest as source of truth. Do not invert all registries at once.

## 7. Scheduling, concurrency, and determinism

### 7.1 Current scheduler map

| Phase/thread | Current work | Evidence anchor |
|---|---|---|
| Forge lifecycle/server thread | composition, registration, server/save start/stop | `AppEng`, `Registration`, `WorldData` |
| World tick start | per-world queued calls with elapsed-time guard | `TickHandler` |
| World tick end | incremental crafting job simulation with configured budget | `TickHandler`, `CraftingJobV2` |
| Server tick end | ready tiles then live `Grid.update()` calls | `TickHandler`, `Grid` |
| Per-grid update | cache updates; `TickManagerCache` priority queue calls machines | `Grid.update`, `TickManagerCache.onUpdateTick` |
| Compass worker | file-backed index update/query and callbacks | `CompassService` single scheduled executor |
| Declared crafting executor | cached thread pool accessor exists; active inspected planner paths do not use it | `CraftingGridCache.CRAFTING_POOL` |

### 7.2 Accepted threading rules

1. World, tile, entity, grid membership, storage commit, energy commit, security mutation, crafting execution, packet action, and authoritative NBT/store mutation remain on the server thread unless a specific platform API proves otherwise.
2. Workers may consume only immutable snapshots or worker-owned data. No `World`, tile, live grid cache, mutable `ItemStack`, handler, or addon object crosses into worker computation.
3. Every snapshot has the minimum relevant revision/token. Commit returns **stale**, not partial success, when any required revision changed.
4. Cancellation is structured by server/save, world, grid, host, and operation scopes. Unload/stop cancels queued work, prevents callbacks into dead hosts, and joins/shuts down owned executors within a measured bound.
5. Worker result callbacks re-enter through the owner scheduler. They do not invoke hosts directly from a worker.
6. A time budget is measured using a monotonic clock, latched from configuration at an explicit boundary, and reports consumed/deferred/cancelled work. Fairness and starvation tests precede algorithm changes.
7. Current tick phase and relative ordering are compatibility-sensitive. Moving work from world-end to server-end or to a worker is a behavior change requiring differential and performance evidence.

### 7.3 Determinism policy

Stable ordering MUST be explicit where it changes:

- storage priority/tie routing;
- pattern/provider/CPU selection;
- grid/cache event emission relied on by addons;
- serialized or diagnostic output used by fixtures;
- scheduler tie-breaking and deferred-work fairness; or
- seeded worldgen decisions.

Do not sort merely for aesthetics. Added sorting can change established winner selection and cost. First characterize current output across representative runs, choose a stable key that represents intended behavior, and approve any resulting gameplay correction under OD-06.

## 8. Testing and enforcement strategy

### 8.1 Evidence ladder

| Layer | Purpose | Existing asset | Required additions |
|---|---|---|---|
| Pure/unit | codecs, value rules, algorithms, revision/cancellation state machines | JUnit 4 suite | Legacy fixtures, malformed/unknown fields, identity snapshot parser, deterministic policy tests |
| Forge functional | real registries, grids, inventories, crafting planning, save lifecycle | `src/functionalTest`, `runFunctionalTestServer` | runtime start/stop, split/join/unload, snapshot staleness, store fault hooks |
| Horizon-QA game tests | blocks/parts/world/player/automation/crafting execution | existing `appeng.gametests` and structures | migration-specific conservation, old-save load, worldgen/spatial recovery fixtures |
| Addon matrix | public API linkage and optional behavior | compile-only dependencies and a few compatibility tests | policy-selected addon/version packs, absent/failure cases, FMP and API compile/link/run |
| Golden/fixture | detect save/identity/protocol/worldgen drift | not found | released save corpus, NBT/custom-file corpus, identities, packets, seeds/chunks/configs |
| Fault/recovery | prove one writer and interruption semantics | not found as general infrastructure | short write/truncation/exception/unload/restart points appropriate to each store/operation |
| Performance | protect tick/latency/memory/start-stop | ad hoc timing configuration only | repeatable workloads, warmup/sampling/reporting, approved budgets |
| Architecture | stop dependency regression | not found | package/import rules, forbidden static access rules, documented exceptions |

### 8.2 Required evidence by change risk

| Change | Minimum evidence before cutover |
|---|---|
| Pure extraction, no authority/identity change | Unit/functional equivalence and dependency rule |
| New read seam or shadow path | Direct-versus-seam differential, proof shadow has no mutation capability, overhead measurement |
| Mutation authority change | Conservation/invariant tests, permission/energy/notification tests, failure/unload/cancellation, explicit writer ledger, rollback drill |
| Codec/store change | Released fixtures, malformed/missing/unknown data, read-old/write policy, fault/restart recovery, downgrade decision |
| Registry/class/package change | Identity snapshot, reflection/coremod/API/addon evidence, alias/bridge and rollback |
| Scheduler/thread change | Thread-affinity assertions, stale snapshot, cancellation/unload/stop, fairness/starvation, tick/latency/memory baselines |
| Worldgen change | Golden seeds/configs/chunks, old-world unexplored behavior, duplicate prevention, performance |
| Spatial authority change | All of the above plus crash injection at every durable/mutation phase and restart recovery |

### 8.3 Architecture enforcement

Gate G2 MUST add a repository-owned automated check that can express at least:

- target policy packages cannot import `net.minecraft`, `net.minecraftforge`, container/client/packet hosts, concrete integrations, or forbidden mutable globals;
- platform adapters may depend on policy ports, never the reverse;
- only the declared composition package constructs target runtime/service implementations;
- legacy exceptions are an explicit allowlist with owner and removal slice;
- target codec/store packages do not become generic dumping grounds; feature codecs remain feature-owned.

The implementation can be a focused JUnit source scan, build task, or an appropriate dependency rule tool compatible with this Java/Gradle baseline. Selecting a large framework is not required.

### 8.4 Validation commands

Repository-verified commands are:

```bash
./gradlew test
./gradlew runFunctionalTestServer
./gradlew build
```

`./gradlew build` is the broad local build gate. Horizon-QA is enabled in the external reusable CI workflow; until a local task is repository-owned, cite the CI job/result rather than inventing a command. Addon-matrix commands must be documented by the slice that introduces them.

### 8.5 Performance baselines

Before a scheduler, planner, routing, save, generation, or spatial cutover, record the current and candidate under the same workload and JVM after warmup. At minimum report:

- server tick distribution and worst sustained interval for the affected phase;
- operation latency/throughput and deferred work;
- allocation/heap growth and retained state where measurable;
- save/start/stop or chunk-generation cost when relevant; and
- fixture size, addon set, grid/topology size, item/pattern count, and configuration.

OD-07 decides numeric budgets. Until then, unexplained material regression blocks cutover; reviewers must not substitute a fabricated percentage.

## 9. Executable migration roadmap

### 9.1 Gates

| Gate | Required outcome | Blocks |
|---|---|---|
| G0 — adopt contract | OD-01 through OD-04 have explicit interim or final policies; document status accepted by maintainers | Release claims and identity/package work |
| G1 — baseline | Compatibility ledger, released fixture corpus scope, initial identity snapshot, current performance workloads | Any authority or format change |
| G2 — ownership seam | Server/save runtime facade, lifecycle tests, architecture rule, no format/tick change | Feature injection and removal of save-scoped statics |
| G3 — first feature seam | Current crafting planner routing behind a tested boundary; normalized diagnostics/equivalence domain | Planner experiments |
| G4 — first mutation cutover | One storage transfer entry point with single authority/writer, conservation/failure evidence, rollback drill | Broader storage/grid mutation extraction and manifest inversion |
| G5 — grid/crafting | Grid revision/snapshot semantics, split/join evidence, worker/cancellation decision | Worker planner and crafting execution changes |
| G6 — generation | Golden generation corpus and old-world policy | Worldgen authority change |
| G7 — spatial | Journal/recovery design, fault harness, compatibility decision | Spatial authority change |

### 9.2 Universal slice state machine

Every slice follows:

1. **Characterize:** identify symbols, state, authority, writer, identities, quirks, and baseline.
2. **Constrain:** add fixtures/invariants and an architecture rule before extraction.
3. **Introduce seam:** route current behavior through the smallest boundary; preserve current writer and lifecycle.
4. **Shadow:** only if safe; capture once, compute without mutation, compare normalized results, sample/limit overhead.
5. **Cut over:** choose one startup-latched entry point. Declare new sole authority; keep legacy reader/adapter only as required.
6. **Soak:** run local/CI/addon/performance/restart evidence and observe structured diagnostics.
7. **Remove:** only after the rollback window and policy allow it; remove toggle, dual model, and exception together.

The first three slices below are deliberately issue-ready and avoid high-risk format or world mutations.

### 9.3 Slice M0 — compatibility ledger and executable identity baseline

**Objective:** turn hidden compatibility surfaces into reviewable fixtures without changing runtime authority.

**Scope and anchors:** registration in `Registration` and feature handlers; `MaterialType`, `PartType`, `EntityIds`; packet/GUI/stack type enums; tile/entity/FMP registration; transformer/API/reflection descriptors; representative tile/part/cell/crafting/grid/spawn data.

**Prerequisites:** none. Maintainers must nominate an initial old-save/addon corpus; unresolved breadth is recorded under OD-01/OD-02 rather than guessed.

**Deliverables:**

- compatibility ledger keyed by artifact/identity, current owner/writer, reader, evidence fixture, and policy status;
- generated runtime identity snapshot with tested addon-set metadata;
- unit/functional verifier that fails on unexplained identity drift;
- initial released-data fixture set, including valid, unknown-field, malformed, and previous-format cases where obtainable;
- protocol enum and class-name/reflection inventory;
- benchmark workload definitions and current results, with no threshold yet;
- explicit exclusions/issues for identities or formats that cannot yet be generated or loaded.

**Authority/writer:** before = current runtime and stores; after = unchanged. The generator observes registrations or isolated test instances. It does not register, repair, normalize, or save production state.

**Shadow/cutover:** there is no gameplay cutover. CI adoption is the cutover: unexplained snapshot drift fails review. Provide a clearly reviewed regeneration command and human-readable diff.

**Rollback:** revert/disable the verifier only if it is incorrect; runtime behavior and saves are unaffected. A snapshot failure must never auto-rewrite the accepted fixture.

**Required evidence:** deterministic repeated generation, dedicated server classloading, at least the default and policy-selected addon sets, round trips for fixture artifacts, and proof that generation has no registration side effects.

**Exit criteria:** G1 passes; every identity category in section 3.4 is captured or linked to a blocking issue; OD-01/OD-02 have at least interim policy; current commands remain green.

**Known risks:** runtime-only identities missed by source scan, different addon sets producing different stack type tables, class loading changing registration, and false confidence from incomplete release fixtures.

### 9.4 Slice M1 — explicit server/save ownership with legacy writers

**Objective:** replace implicit save-scoped lookup with an explicit lifecycle owner while preserving `WorldData` behavior, file formats, tick phases, and all current writers.

**Scope and anchors:** `AppEng.serverAboutToStart/serverStopping/serverStopped`, `WorldData`, `PlayerData`, `DimensionData`, `StorageData`, `SpawnData`, `CompassData`/`CompassService`, and only the `TickHandler` hooks needed for lifecycle ownership. `[TARGET] ServerRuntime` and `[TARGET] WorldScope` are roles; names may change.

**Prerequisites:** M0; fixture coverage for `settings.cfg`, grid blobs, spawndata, spatial IDs, and compass rebuild classification; architecture rule can recognize the new boundary.

**Deliverables:**

- one server/save runtime factory called by `AppEng` after the save root is known;
- explicit ownership/close order for existing store objects and compass executor;
- a compatibility facade at `WorldData.instance()` for unmigrated callers, delegating to the active runtime and failing clearly outside lifecycle;
- injection of the runtime or narrow owned service into one low-risk consumer, not global replacement;
- tests for create/start, world load/unload, stop/flush/close, second server lifecycle in one JVM, and no callback after close;
- an allowlist of remaining save-scoped static callers with removal slices;
- G2 import/static-access enforcement.

**Authority/writer:** before = `WorldData` child implementations; after = the same child implementations, now owned by the runtime. `StorageData`, `PlayerData`, `DimensionData`, and `SpawnData` remain the only writers of their current artifacts. No codec, filename, category/key, eager-save, or recovery behavior changes in this slice.

**Shadow/cutover:** instantiate exactly one runtime. Do not instantiate parallel stores against a save. Cut over ownership in `serverAboutToStart` behind a startup-latched implementation selector only if the selector does not open the files twice. Compare read-only inventories/diagnostics from the facade and direct owned objects in tests.

**Rollback:** before opening a save, select the legacy composition that constructs `WorldData` exactly as today. Once a server session begins, do not switch runtime ownership live. Because formats/writers are unchanged, release rollback remains governed by the existing data policy.

**Required evidence:** existing tests plus two lifecycle cycles, forced world unload with pending compass work, server stop with dirty spawndata/grid data, identity/player/spatial ID stability, no executor/thread leak, and unchanged fixture bytes or semantic output where Forge Configuration ordering is not stable.

**Exit criteria:** G2 passes; one runtime owns save services; every owned close/flush is idempotent or guarded; current facade callers are enumerated; there is no second file writer or new service locator.

**Known risks:** Forge callback ordering, tests unable to restart in-process cleanly, eager saves during construction/repair, compass callbacks racing close, and accidentally treating multi-world grids as world-owned.

### 9.5 Slice M2 — crafting planning boundary using current planners

**Objective:** establish a feature-owned planning boundary and comparison vocabulary without changing planner algorithms or crafting execution authority.

**Scope and anchors:** `CraftingGridCache.beginCraftingJob`, `CraftingJobFast`, `CraftingJobV2`, `TickHandler` crafting queues, public callback/job interfaces, and diagnostic result normalization. `CraftingCPUCluster` is test scope but not implementation scope.

**Prerequisites:** M0; M1 runtime scheduling/lifecycle handle; documented fast-versus-v2 feature differences; baseline workloads for small, recursive, substitution, multi-output, and missing-item requests.

**Deliverables:**

- internal planning request/result/diagnostic values that do not expose live mutable collections;
- `[TARGET] CraftingPlanningService`-role boundary with two adapters that delegate to the current fast and v2 implementations and preserve the existing lite-mode selection;
- explicit operation owner, cancellation, world/grid invalidation, and callback delivery rules matching current behavior in this slice;
- normalizer that compares outcome kind, requested/output amounts, missing inputs, selected patterns/priority where stable, and resource estimate without claiming universal cross-planner equivalence;
- direct-current-versus-service functional differentials for both planners;
- optional sampled runtime shadow only for proven read-only/captured inputs and bounded requests; disabled by default if it would repeat mutable addon callbacks;
- instrumentation for calculation time, deferral/ticks, cancellation, and result size.

**Authority/writer:** planning has no persistent writer. Before = `CraftingGridCache` selection plus current job; after = service selection plus the same current job adapters. Execution, storage/energy mutation, links, CPUs, notifications, and CPU NBT remain exclusively current authority.

**Shadow/cutover:** first compare direct and service routes in the functional harness. Cut over only `beginCraftingJob` routing with a startup-latched flag. A shadow result is logged/tested and discarded; it cannot submit a CPU job or mutate storage. Cross-fast/v2 comparison is limited to a named equivalence corpus.

**Rollback:** route `beginCraftingJob` directly as before on the next startup. Active CPU jobs are unaffected because execution and save format did not move. Cancel planning operations through their current job mechanism on world/grid/server teardown.

**Required evidence:** current crafting-v2 functional tests and Horizon-QA execution conservation tests; planner-specific success/missing/cancel/error; substitutions, priorities, multi-output/remainders, recursive graphs, callback exactly once, unload/stop, and no extra authoritative reads with side effects; performance baseline with shadow disabled/enabled.

**Exit criteria:** G3 passes; selection and results match direct current paths in each planner's domain; no live Forge/world object is presented as worker-safe; no claim of a third v1 planner; execution/save authority is visibly unchanged.

**Known risks:** current planners read mutable grid/addon state during calculation, callbacks have implicit thread/tick assumptions, normalized output may hide meaningful ordering, and the unused/static executor can be mistaken for an approved worker lifecycle.

### 9.6 Later roadmap

The order below is recommended; a reorder requires evidence that prerequisites and authority boundaries still hold.

| Slice | Objective and seam | Before -> after authority/writer | Shadow/cutover/rollback | Gate and exit evidence |
|---|---|---|---|---|
| M3: one import-bus item transfer | Add transfer result/compensation policy around one mode/channel using current external/storage/energy adapters | Bus method -> feature transfer service for that entry point; cell/grid/custom-file writers unchanged | Observe captured steps; cut over startup-latched; fallback restores legacy coordinator only | G4: conservation, lying/reentrant/throwing handlers, permissions, energy, unload, addons, rollback drill |
| M4: security identity injection | Replace `SecurityCache` direct `WorldData` lookup and process-static lifecycle leak | Current permission algorithm stays authority; runtime-owned identity adapter is sole player-ID access; same settings writer | Decision differential; per-server start rollback | IDs stable, operator lifecycle, provider split/join/startup matrix |
| M5: grid revisions/read models | Add explicit grid revision and read views for migrated consumers | `Grid` remains topology authority; new view owns no mutation/persistence | Compare membership/cache queries; consumer-by-consumer cutover | G5 prerequisites: split/join/unload/multi-world/addon cache and overhead |
| M6: storage expansion | Extend capability to export, storage bus, IO port, fluids/channels only after M3 | One operation family at a time; existing cell/store writer retained until its own codec slice | Per family/mode toggle and compensation tests | Full conservation/priority/watcher/cell fixture/addon matrix |
| M7: planner experiment | Optional snapshot/revision worker planner | Current planner remains authority until G5 comparison; server thread remains commit authority | Non-mutating sampled shadow; cut over a bounded request class; fallback to current planners | G5: equivalence domain, stale rejection, cancellation, executor shutdown, performance |
| M8: crafting execution | Extract CPU execution state machine and codec separately from planner | `CraftingCPUCluster` -> one new execution authority and one codec writer after legacy load | Replay/trace comparison; no dual CPU tick; rollback only while writes remain legacy-readable | Active-job restart, cancellation/break, links/listeners, energy/item conservation, addon mediums |
| M9: custom stores | Improve one of settings/grid or spawndata, never both in one slice | Named old store -> named new sole writer; Minecraft NBT unaffected | Shadow read/encode in memory; cut over at server start; tested backup/recovery rollback | Fault injection, fixture corpus, downgrade policy, operator diagnostics |
| M10: worldgen | Separate planned decisions from writes for quartz then meteorites | Current generator -> feature generator per generator; SpawnData writer initially unchanged | Isolated recording-world shadow; pre-world-load toggle | G6 golden seeds/configs, old-world unexplored policy, duplicate/restart/perf |
| M11: spatial preflight/recovery | Add immutable transition manifest; then journaled state machine if approved | Current swap remains writer through preflight; later single journaled operation owns all world mutation | Audit only until G7; no live dual execution; recovery is rollback/complete mechanism | G7 crash points, restart recovery, tile/entity/tick/addon conservation |
| M12: identity/package cleanup | Remove bridges/exceptions only after support window | No identity changes without explicit manifest/API/protocol migration | Alias/bridge per identity; release rollback policy | G0/G4 plus addon/coremod/reflection tests and release notes |

### 9.7 Removal criteria

Legacy code, flags, adapters, and compatibility readers may be removed only when:

- the supported release/downgrade window permits removal;
- all supported saves/addons use the new path or have a tested bridge;
- soak and performance evidence covers the declared matrix;
- no public/reflection/serialized identity still points to the legacy class;
- the writer ledger has had one authority for the full soak window; and
- the removal PR deletes its exception/flag and updates this decision register.

## 10. Risk register

| ID | Risk | Likelihood/impact | Mitigation and evidence | Blocking gate |
|---|---|---|---|---|
| R1 | Save corruption or silent empty fallback hides authoritative data loss | Medium / critical | Released fixtures, structured decode outcomes, backup/quarantine policy, fault/restart tests, one writer | G1/G4/G7 |
| R2 | Registry/enum/class identity drifts during cleanup | High / critical | Generated identity snapshot, reflection/coremod/addon fixtures, package moves isolated | G1/G4 |
| R3 | Dual authority duplicates or loses items/energy/world state | Medium / critical | Writer ledger, mutation-incapable shadow, conservation invariants, cutover one entry point | G4/G7 |
| R4 | External inventories violate simulation/exception assumptions | High / high | Adversarial handlers, compensation outcomes, reentrancy guard, addon matrix | G4 |
| R5 | Grid split/join/unload invalidates snapshot or transfers wrong cache state | Medium / high | Explicit revisions, stale rejection, multi-world topology fixtures | G5 |
| R6 | Crafting planner/execution boundary breaks active jobs or public callbacks | Medium / high | Separate slices/codecs, callback-once tests, active-job restart, listener class inventory | G3/G5 |
| R7 | Worker callbacks touch dead worlds/hosts or leak threads | Medium / high | Structured cancellation, owner scheduler, stop/join tests, thread-affinity assertions | G2/G5 |
| R8 | Seed/order changes create terrain seams or duplicate meteorites | Medium / high | Golden corpus, old-world policy, keep `SpawnData` authority initially | G6 |
| R9 | Spatial interruption leaves two partially mutated regions | High / critical | No cutover before journal/recovery design and exhaustive fault injection | G7 |
| R10 | Optional integration or coremod fails only in real packs | High / high | Policy-selected compile/link/run matrix, absent/failure startup, FMP and dedicated side tests | G0/G1/G4 |
| R11 | Abstraction increases allocation/tick cost | Medium / high | Boundaries justified per slice, representative performance baselines, sampled shadow | Every cutover |
| R12 | Static state leaks across integrated-server/restart tests | Medium / medium | Lifetime inventory, two server cycles, clear/close assertions, allowlist | G2 |
| R13 | Compatibility scope expands without an owned policy | High / high | Resolve OD-01/OD-02; label untested combinations; do not make universal claims | G0 |
| R14 | Permission decisions drift or a new mutation path bypasses authorization | Medium / critical | Decision matrix, capability-level authorization, denied-operation conservation tests, security provider split/join/startup coverage | G4/G5 |
| R15 | Interfaces, ports, runtimes, or shared abstractions grow without ownership value | Medium / medium | Require a real consumer/test seam and named owner/lifetime; reject package moves or wrappers as milestones; remove unused abstractions | Every slice |

## 11. Decision register

### 11.1 Accepted decisions

| ID | Decision | Consequence |
|---|---|---|
| AD-01 | Modernize by vertical feature slices inside the Forge shell | No platform/framework rewrite prerequisite |
| AD-02 | One authority and one writer per artifact/operation phase | Shadow paths are structurally non-mutating; dual-write synchronization is forbidden |
| AD-03 | Explicit process, server/save, world, grid, host, and operation lifetimes | Save/grid mutation cannot hide in unbounded process statics |
| AD-04 | Compatibility contracts are separate | Save/API/wire/addon/gameplay evidence cannot substitute for each other |
| AD-05 | Identity manifest begins as verifier, not registration source | No simultaneous discovery and authority inversion |
| AD-06 | Codec/store behavior is artifact-specific | No universal unknown-NBT or atomic-file promise |
| AD-07 | Current planners are fast and v2; execution is a separate legacy surface | Migration/test vocabulary matches the current repository |
| AD-08 | Server thread remains mutation authority; workers require snapshots/revalidation | Async is an optimization after lifecycle and correctness gates |
| AD-09 | Target package names are provisional; package moves are gated migrations | Architectural ownership can improve without identity churn |
| AD-10 | Determinism is explicit only where observable/test-relevant | Avoid accidental order dependence and gratuitous behavior changes |

### 11.2 Proposals requiring validation

| ID | Proposal | Evidence required before acceptance |
|---|---|---|
| P-01 | Snapshot/revision-based worker crafting planner | G5, equivalence corpus, stale/cancel/thread/performance evidence |
| P-02 | Durable spatial transition journal | G7 design review and fault/restart recovery on target filesystems |
| P-03 | Selected identity manifest becomes registration source | G4, proven snapshot completeness, alias/API/addon impact and rollback |
| P-04 | Shared custom-store durability primitive | Two real store consumers with matching requirements; Java 8/filesystem fault evidence |
| P-05 | More deterministic grid/cache iteration | Characterized current winner/order behavior and approved correction policy |

### 11.3 Open decisions

| ID | Decision needed | Why repository evidence is insufficient | Evidence needed | Blocks |
|---|---|---|---|---|
| OD-01 | Earliest supported save release, forward-read and downgrade window | No authoritative policy or released-save corpus found | Selected release lineages, representative saves, and an explicit rollback/downgrade promise | G0/G1/G4 |
| OD-02 | Public API/addon support matrix and source/binary guarantees | Many compile-only integrations and API repair bridges, but no version matrix | Versioned addon packs plus compile, link, startup, and behavior results | G0/G1 |
| OD-03 | Which class/package/descriptors may move and bridge duration | Reflection, coremod, FMP generation, GUI derivation, Java serialization all carry names | Complete identity inventory, bridge prototype, and support-window decision | G0/G4 |
| OD-04 | Network compatibility policy across versions and addon sets | Ordinal IDs and dynamic stack type table suggest same-set assumptions, not policy | Handshake/session fixtures across the candidate version and addon-set combinations | G0/G1 |
| OD-05 | Whether any manifest should become registration authority | A validation snapshot can be useful without controlling registration | Completeness comparison, alias behavior, failed-registration behavior, and rollback proof | G4 |
| OD-06 | List of preserved quirks versus approved gameplay corrections | Code and tests show behavior, not product intent | Issue-linked characterization and explicit expected outcomes for disputed cases | Relevant cutover |
| OD-07 | Numeric performance budgets and representative workloads | No approved baseline or threshold found | Reproducible workloads, current distributions, capacity goals, and candidate results | Relevant cutover |
| OD-08 | Custom-store recovery behavior: fail start, quarantine, restore backup, or rebuild | Current `settings.cfg`/spawndata fallbacks differ and can hide loss | Fault exercises, recovery prototypes, and operator-facing failure examples | M9/G7 |
| OD-09 | Spatial interruption policy: roll forward, roll back, or require operator repair | No current journal and world mutation may be non-invertible | Durable phase model, invertibility analysis, failure prototypes, and restart exercises | G7 |
| OD-10 | Cross-world grid ownership on dimension unload | Current grid may span worlds; simple per-world runtime ownership is insufficient | Multi-dimension topology fixtures, lifecycle trace, and stale-reference analysis | G2/G5 |

## 12. Definition of done and pull-request contract

A modernization slice is done only when all applicable items are true:

- Scope names the exact entry points, state, identities, current authority/writer, and target authority/writer.
- Current behavior is characterized in repository-owned tests before or alongside extraction.
- Compatibility categories touched by the slice are named; unresolved policy links to an open decision.
- New boundaries obey D1–D10 or add a narrow, owned, expiring exception.
- Shadow code has no mutation capability and has bounded/observed overhead.
- Cutover selects one authority and writer, is latched at a safe lifecycle boundary, and has a tested rollback route.
- Missing/malformed/unknown/legacy data semantics are tested for any codec change.
- Resource, energy, permission, notification, lifecycle, cancellation, unload, restart, and failure invariants are tested as applicable.
- Registry/API/reflection/protocol/addon evidence is present for identity-sensitive changes.
- Current and candidate performance results use the same declared workload; regressions are explained and approved.
- Dedicated server/client side loading is tested when client/integration code is touched.
- Operator-visible failures identify artifact/feature/action without silently discarding authoritative state.
- Documentation, decision/risk registers, compatibility ledger, snapshot, flags, and exception allowlist are updated in the same change where relevant.
- `./gradlew test`, `./gradlew runFunctionalTestServer` when server behavior is touched, `./gradlew build`, relevant Horizon-QA CI, and the selected addon matrix pass.
- Removal occurs only after the declared soak/support window and removes the compatibility flag/exception with the old authority.

Suggested pull-request summary template:

```text
Slice / gate:
Current anchors:
Compatibility contracts touched:
Before authority / writer:
After authority / writer:
Shadow inputs and proof of no mutation:
Cutover latch and rollback route:
Fixtures / integration / addon evidence:
Performance workload and result:
Known risks / open decisions:
Legacy code or exception removed (or removal issue):
```

## 13. Repository evidence index

This index is intentionally concrete so reviewers can re-verify claims as the code changes.

### Bootstrap, lifecycle, and build

- `build.gradle`, `settings.gradle`, `gradle.properties`, `dependencies.gradle`, `addon.gradle`
- `.github/workflows/build-and-test.yml`
- `src/main/java/appeng/core/AppEng.java` — Forge lifecycle and server callbacks
- `src/main/java/appeng/core/Registration.java` — content, events, cache and integration registration
- `src/main/java/appeng/hooks/TickHandler.java` — server/world queues, crafting budget, grid updates and cleanup

### Save data and services

- `src/main/java/appeng/core/worlddata/WorldData.java`
- `src/main/java/appeng/core/worlddata/PlayerData.java`
- `src/main/java/appeng/core/worlddata/DimensionData.java`
- `src/main/java/appeng/core/worlddata/StorageData.java`
- `src/main/java/appeng/core/worlddata/SpawnData.java`
- `src/main/java/appeng/core/worlddata/CompassData.java`
- `src/main/java/appeng/services/CompassService.java`
- `src/main/java/appeng/me/GridStorage.java`
- `src/main/java/appeng/tile/AEBaseTile.java`
- `src/main/java/appeng/parts/CableBusContainer.java`
- `src/main/java/appeng/me/storage/CellInventory.java`

### Grid, storage, crafting, and security

- `src/main/java/appeng/me/Grid.java`, `GridNode.java`, and `GridPropagator.java`
- `src/main/java/appeng/me/cache/TickManagerCache.java`
- `src/main/java/appeng/me/cache/GridStorageCache.java`
- `src/main/java/appeng/me/cache/SecurityCache.java`
- `src/main/java/appeng/me/cache/CraftingGridCache.java`
- `src/main/java/appeng/me/storage/NetworkInventoryHandler.java`
- `src/main/java/appeng/util/inv/InventoryAdaptor.java`
- automation import/export/storage bus and IO-port classes under `src/main/java/appeng/parts/automation`
- `src/main/java/appeng/crafting/fast/CraftingJobFast.java`
- `src/main/java/appeng/crafting/v2/CraftingJobV2.java`
- `src/main/java/appeng/me/cluster/implementations/CraftingCPUCluster.java`

### Identity, API, integrations, and protocol

- `src/main/java/appeng/core/features/FeatureNameExtractor.java`
- feature handlers under `src/main/java/appeng/core/features`
- `src/main/java/appeng/items/materials/MaterialType.java`
- `src/main/java/appeng/items/parts/PartType.java`
- `src/main/java/appeng/entity/EntityIds.java`
- `src/main/java/appeng/core/sync/AppEngPacket.java` and nested `AppEngPacketHandlerBase.PacketTypes`
- `src/main/java/appeng/core/sync/GuiBridge.java`
- `src/main/java/appeng/api/storage/data/AEStackTypeRegistry.java`
- `src/main/java/appeng/integration/IntegrationRegistry.java`, `IntegrationNode.java`, and `IntegrationType.java`
- `src/main/java/appeng/core/api/ApiPart.java`
- `src/main/java/appeng/transformer/**`
- `src/main/java/appeng/api/AEApi.java` and the rest of `src/main/java/appeng/api/**`

### Spatial and generation

- `src/main/java/appeng/tile/spatial/TileSpatialIOPort.java`
- `src/main/java/appeng/spatial/StorageHelper.java`
- `src/main/java/appeng/spatial/CachedPlane.java`
- `src/main/java/appeng/spatial/NBTSpatialHandler.java`
- spatial cell and movable-handler API/implementations
- quartz/meteorite generation classes including `QuartzWorldGen`, `MeteoriteWorldGen`, and `MeteoritePlacer`
- `IMeteoriteWorld`, `StandardWorld`, and chunk-only world adapters

### Tests

- `src/test/java/**`
- `src/functionalTest/java/appeng/test/AppengTestMod.java`
- `src/functionalTest/java/appeng/test/CraftingV2Tests.java`
- `src/functionalTest/java/appeng/test/me/storage/NetworkInventoryHandlerFunctionalTest.java`
- `src/main/java/appeng/gametests/**`
- `src/main/resources/assets/appliedenergistics2/horizonqastructures/**`

Repository history is evidence only when cited with a commit and independently relevant fixtures. In particular, `8c34108a1` explains the removed v1 planner; it does not add a third current implementation or define a compatibility promise.
