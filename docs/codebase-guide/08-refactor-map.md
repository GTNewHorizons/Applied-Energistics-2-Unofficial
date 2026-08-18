# Refactor map

## Why this exists

This is a cautious map of places where architectural work may pay off. It is not an implementation plan and no proposal here has been applied. Each candidate separates source-observed behavior from interpretation and proposal, names compatibility risks, and makes missing characterization tests a prerequisite. A large class, global, reflection, or old API is not by itself a defect; the question is whether a smaller change can make important behavior safer without breaking worlds, addons, packets, or optional mods.

Use the subsystem guides and [evidence index](10-evidence-index.md) before scoping any candidate.

## Ranking method

“Value” estimates how much correctness/changeability improves. “Risk” includes runtime, addon, wire, persistence, and lifecycle risk. “Prerequisite” estimates missing test work, not coding effort. Rankings are relative, based on the current tree.

| Rank | Candidate | Value | Change risk | Prerequisite coverage | Recommended posture |
|---:|---|---|---|---|---|
| 1 | Topology/cache lifecycle and event safety | Very high | Very high | High | Characterize and make narrow correctness fixes before structural extraction. |
| 2 | Crafting planning/execution contracts and CPU boundaries | Very high | Very high | High | Fix isolated ordering/failure defects; extract behind tests, preserve NBT. |
| 3 | Storage routing/monitor recursion and generic-type boundary | High | High | High | Harden guards/watchers first; do not promise full genericity prematurely. |
| 4 | Terminal/container synchronization and ordinal protocol | High | High | Medium–high | Extract server actions/codecs without changing bytes or authority. |
| 5 | Save/process lifecycle ownership and shutdown | Medium–high | High | High | Add repeated-start/stop tests, then narrow world-scoped ownership. |
| 6 | Definition/registration lifecycle monolith | Medium–high | High | Medium–high | Introduce validated descriptors around one feature family at a time. |
| 7 | Coremod and optional-integration compatibility seam | Medium | Very high | High | Build transformation fixtures; prefer hardening over wholesale replacement. |
| 8 | Test/build boundary and external convention visibility | Medium | Medium | Low–medium | Improve local assertions/tasks/docs; avoid duplicating the convention plugin. |

The highest rank is not the first code change. It is the area with the strongest payoff once its prerequisite tests exist.

## 1. Topology/cache lifecycle and event safety

**Observed facts**

- [`Grid`](../../src/main/java/appeng/me/Grid.java), [`GridNode`](../../src/main/java/appeng/me/GridNode.java), and [`GridConnection`](../../src/main/java/appeng/me/GridConnection.java) mutate component membership while calling cache add/remove callbacks; `IGridCache` warns state is unstable in them.
- Runtime edge splits create a new grid/cache set and migrate nodes; they do not call `IGridCache.onSplit`. The only `onSplit` call is `Grid.add`'s persisted-`GridStorage` reconstruction path. The pivot component retains old caches, while the new component starts defaults plus add callbacks.
- Connection merge happens before the new adjacency is inserted and has no general rollback when a cache/event callback throws.
- Cache update order is a `HashMap` order.
- `GridNode` and `GridConnection` reuse mutable/cancelable static `MENetworkChannelsChanged` instances; canceled/visited state is not reset.
- `Grid.postEvent`, `GridNode.beginVisit`, and `P2PCache.updateTunnel` pause crafting rebuilds without `try/finally`.
- Watcher maps in `GridStorageCache`, `CraftingGridCache`, and `EnergyGridCache` add by node but remove by machine; energy also checks the wrong watcher-host interface.

**Interpretation.** Topology, cache ownership, events, and persistence form one implicit transaction without a transaction boundary. Several narrow source mismatches can cause stale state; normal behavior relies on callback ordering and rebuilding conventions that are difficult to infer.

**Proposal.** First repair provable local lifecycle defects (fresh event per post, balanced pause guards, consistent watcher keys/interfaces) behind tests. Then introduce an internal topology-change context/phase model that distinguishes runtime split, persisted reconstruction, merge, add/remove, and “settled/rebuild” notification. Do not force all caches through `onSplit` until every cache's current state policy is characterized.

| Required field | Assessment |
|---|---|
| Confidence | High for observed call sites/key mismatches; medium for the best long-term phase abstraction. |
| Expected blast radius | All grids, path/power/storage/crafting/security/P2P caches, world unload/reload, addons implementing caches. |
| Compatibility/persistence risks | Different winner/split cache state, energy duplication/loss, crafting-link identity, addon callback assumptions, save reconstruction. |
| Tests before change | Both split orientations with persisted cache payloads; merge-winner selection; repeated split/merge; event cancellation; callback exception; watcher add/remove/no-notification; controller/channel/power/storage/crafting after settling; chunk/world reload. |
| Safe incremental sequence | (1) Add characterization. (2) Allocate fresh channel events. (3) add `try/finally` guards. (4) fix watcher keys. (5) instrument topology phase/order. (6) expose a settled rebuild callback internally. (7) migrate one cache and compare state. |
| Why it may not be worthwhile | A generalized transaction layer could add allocations/latency and break addon callbacks; narrow fixes plus documentation may cover the real defects. |

## 2. Crafting planning/execution contracts and CPU boundaries

**Observed facts**

- [`CraftingCPUCluster`](../../src/main/java/appeng/me/cluster/implementations/CraftingCPUCluster.java) is about 2,500 lines and combines multiblock state, scheduling, inventory ledger, medium dispatch, returned-output accounting, links, notifications/diagnostics, missing mode, and NBT.
- Two planners are active: default [`CraftingJobV2`](../../src/main/java/appeng/crafting/v2/CraftingJobV2.java) and lite-mode [`CraftingJobFast`](../../src/main/java/appeng/crafting/fast/CraftingJobFast.java). Fast deliberately omits substitution, priorities, complete multi-output behavior, and backtracking.
- Equal-priority alternatives in `CraftingGridCache` use a comparator that compares only priority, allowing a `TreeSet` to discard distinct patterns; CPU task ordering uses priority then hash code.
- `craftableItemsLegacy` is not cleared with generic maps during rebuild.
- V2 exceptions/cancellation, `isSimulation`, `Future.get`, confirmation auto-start, and CPU submission do not share one explicit terminal-state contract; timeout conversion is reversed.
- Running CPU NBT is versionless, restores an unchecked enum ordinal, includes Java-object-serialized listener data, and may skip missing pattern items. No CPU reload test was found.
- Produced output can complete the job even when a requester rejects it and the remainder goes to ordinary storage. That is current semantics.

**Interpretation.** Planning and execution are conceptually separate but their success/failure/persistence contracts are implicit. The CPU class has natural internal boundaries, yet a rewrite would threaten dupe/loss and historical saves.

**Proposal.** Define/test an internal calculation result state (`ready`, `simulated`, `failed`, `canceled`) shared by both planners and confirmation/submission. Fix comparator/legacy-map defects narrowly. Then extract pure collaborators from the CPU—output ledger, medium dispatcher/scheduler, link lifecycle, and an NBT codec facade—while the cluster remains the compatibility orchestrator. Version new writes only after a dual-reader historical-fixture plan exists.

| Required field | Assessment |
|---|---|
| Confidence | High for observed defects/convergence; medium for extraction boundaries until tests expose hidden field coupling. |
| Expected blast radius | Terminal confirmation, public crafting API, both planners, CPU clusters/tiles, interfaces/assemblers, storage interceptors, links, old saves. |
| Compatibility/persistence risks | Item duplication/loss, different pattern choice, planner output differences, auto-start behavior, NBT/link incompatibility, addons implementing patterns/media/requesters. |
| Tests before change | Equal-priority alternatives and hash collision; V2 failure/cancel/timeout; Fast baseline; storage race rollback; missing/wait/cancel; requester partial rejection; CPU break; CPU+requester NBT round trip and historical fixtures; cluster unload/reform. |
| Safe incremental sequence | (1) Add tests and explicit calculation-state adapter without changing API. (2) fix comparators/map clearing. (3) add codec facade over identical NBT. (4) extract output ledger with golden state tests. (5) extract medium dispatch. (6) version only future fields with old reader retained. |
| Why it may not be worthwhile | Runtime execution is stateful and performance-sensitive; interfaces can create more coordination than clarity. Small correctness fixes may deliver most value. |

## 3. Storage routing, monitoring, and generic-type boundary

**Observed facts**

- [`NetworkInventoryHandler`](../../src/main/java/appeng/me/storage/NetworkInventoryHandler.java) combines priority/two-pass placement, security, nested-network accounting, exact/list lookup, and autocrafting interception.
- Its thread-local recursion state is popped only on normal returns in some paths. [`NetworkMonitor`](../../src/main/java/appeng/me/cache/NetworkMonitor.java) similarly balances semaphores/global diff stack/listener paths without `finally` in places; exceptions can poison later operations on the server thread.
- Monitor/provider rebuild and signed-diff propagation are subtle and terminal-facing; focused recursion/event tests were not found.
- Registered `IAEStackType` is generic, but normal/void cells, crafting queries/polling/spill, interface delivery, and pattern complexity still contain item/fluid assumptions. Deprecated channel/type defaults can recurse when neither side is overridden.
- Custom type network IDs use one signed byte and duplicate string IDs replace prior registrations.

**Interpretation.** Two different objectives are entangled: making current item/fluid routing exception-safe, and completing a broader generic-stack migration. The first is a correctness improvement; the second is an ecosystem/API program.

**Proposal.** Harden recursion/listener guards with scoped `AutoCloseable`-style internal tokens or `try/finally`, fix watcher cleanup as Candidate 1, and build a table-driven handler-order contract suite. Add explicit registration validation for duplicate/ID capacity where compatible. Treat full custom-type support as a separately approved capability matrix; replace legacy channel defaults only with addon migration evidence.

| Required field | Assessment |
|---|---|
| Confidence | High for guard/generic gaps; medium on external addons compensating with custom handlers. |
| Expected blast radius | Every storage operation, terminals, buses/cells, crafting output interception, addon stack types and inventories. |
| Compatibility/persistence risks | Priority/remainder changes, duplicate notifications, recursion cycles, packet/NBT stack IDs, external API default behavior. |
| Tests before change | Handler ordering at equal/mixed priorities; simulate→modulate races; child exception then next operation; recursive storage buses; permission/action sources; listener reentrancy/failure; item/fluid/custom-type codec/list/cell/terminal/craft matrix. |
| Safe incremental sequence | (1) Characterize order/remainders. (2) add balanced guard helpers without order changes. (3) add exception tests. (4) validate new registrations while grandfathering current IDs. (5) inventory every item/fluid switch. (6) enable one custom-type path end to end only if required. |
| Why it may not be worthwhile | GTNH may intentionally support only items/fluids in core, with addons owning custom handlers. A sweeping generic rewrite could add cost without player value. |

## 4. Terminal/container synchronization and ordinal protocol

**Observed facts**

- [`ContainerMEMonitorable`](../../src/main/java/appeng/container/implementations/ContainerMEMonitorable.java) and [`GuiMEMonitorable`](../../src/main/java/appeng/client/gui/implementations/GuiMEMonitorable.java) are large convergence points for multiple stack types, filtering/search/settings, virtual slots, crafting transitions, security/power, and list deltas.
- A client target stack is compressed/chunked separately, then an action packet refers to it; the server anchors to the current open container, re-resolves live availability/hand, and enforces power/security lower in storage.
- GUI sync spans vanilla slots, `@GuiSync`, `SyncManager`, purpose packets, and tile descriptions.
- Packet types, `GuiBridge`, `InventoryAction`, `MonitorableAction`, and settings encode enum ordinals. No focused hostile/out-of-order protocol tests were found.

**Interpretation.** The server boundary is mostly sound but dispersed. Large UI/container classes make it easy to add an action that skips revalidation or creates another sync path. Ordinals make apparently local enum cleanup wire-sensitive.

**Proposal.** Extract server-side monitorable actions into small services operating on a validated container context and current target/hand; keep packet bytes and enum order unchanged. Consolidate new state sync onto existing typed codecs where practical. Separately document/freeze protocol ordinals in golden byte tests. Consider explicit IDs only through a version handshake/dual decoder, not a flag-day rewrite.

| Required field | Assessment |
|---|---|
| Confidence | High for current flow/ordinal usage; medium that service extraction reduces complexity without awkward state plumbing. |
| Expected blast radius | All terminals, crafting sub-GUIs, packets, client repositories, security/power/storage actions, addons opening GUIs. |
| Compatibility/persistence risks | Client/server mismatch, reordered ordinals, partial-target chunk ordering, stale/hostile inputs, different click semantics. |
| Tests before change | Golden packet bytes/enum tables; malformed/oversized/out-of-order chunks; wrong open container; stale target; permission/power loss; item/fluid types; full/delta/list removal; client confirmation/cancel. |
| Safe incremental sequence | (1) Add sender/receiver and golden tests. (2) wrap one action in an internal validated context. (3) extract actions one by one with byte-identical packets. (4) remove duplicate sync only after replica equivalence tests. (5) evaluate explicit IDs only if mixed-version support is a real goal. |
| Why it may not be worthwhile | Locked modpacks often require exact client/server versions, reducing explicit-ID value; extraction can merely move a large state machine across files. |

## 5. Save/process lifecycle ownership and shutdown

**Observed facts**

- [`WorldData`](../../src/main/java/appeng/core/worlddata/WorldData.java) is save-scoped but exposed through a deprecated static singleton. Integrated servers can start/stop more than once per process.
- `AppEng.serverStopped` clears several services, but global `NetworkHandler.instance`, some maps/event registrations/queues, and other static state have no visible full reset.
- `TickHandler` mixes tile readiness, grid ticks, crafting calculation budgets, world/server call queues, unload/save handling, and shutdown.
- `StorageData` visibly serializes grid storage at server stop; the ordinary world-save hook flushes spawn data, not grid storage.
- `AENetworkProxy` can hold deferred loaded node NBT until END-tick readiness, while its writer writes only a live node.

**Interpretation.** Lifetimes are understood by convention rather than encoded ownership. This complicates repeated integrated-server tests and makes crash/save guarantees hard to state.

**Proposal.** First add a two-world/two-restart lifecycle harness that records subscribers, grids, queues, network handlers, executors, and persistent values. Preserve deferred proxy NBT on write as a narrow tested fix if reproduced. Then pass a save-scoped services object into new code while retaining the static accessor adapter; split `TickHandler` only along proven lifetime/scheduling boundaries. Define/verify an explicit periodic grid-storage flush policy before changing it.

| Required field | Assessment |
|---|---|
| Confidence | High for observed ownership/cleanup paths; low–medium on externally supplied Forge/convention cleanup. |
| Expected blast radius | Integrated/dedicated lifecycle, all grids, spatial/player/security data, compass/crafting workers, save files. |
| Compatibility/persistence risks | Cross-save contamination, double subscribers, lost cache energy/job/link data, changed crash durability, shutdown races. |
| Tests before change | Two start/stop cycles in one JVM; two save directories; save while proxy deferred; periodic and stop flush; abrupt termination fixture; compass/crafting worker termination; player/dimension/grid data reload. |
| Safe incremental sequence | (1) Observe/assert lifetimes. (2) fix reproduced deferred-write/reset defects. (3) add idempotent close methods. (4) introduce save context behind static adapter. (5) migrate one service. (6) split scheduler responsibilities only after timing tests. |
| Why it may not be worthwhile | Normal dedicated servers have one save/process lifetime; broader dependency injection could touch most constructors for little runtime benefit. |

## 6. Definition and registration lifecycle monolith

**Observed facts**

- [`Registration`](../../src/main/java/appeng/core/Registration.java) spans three lifecycle phases, deprecated API mirroring, cache/cell/event registration, recipes, dynamic cable layers, upgrades, loot/trades/worldgen/dimension rules, and integration-sensitive tables.
- Definitions are constructed indirectly through [`ApiDefinitions`](../../src/main/java/appeng/core/ApiDefinitions.java) and `DefinitionConstructor`, collecting feature handlers that register later.
- Cable-bus tile registration is intentionally delayed until runtime capability layers/FMP state are known.
- Copy/paste hazards are visible (for example duplicated compatibility assignment); registry names, metadata, recipes, definition access, and addon API are compatibility surfaces.

**Interpretation.** Lifecycle order is correct but encoded by a large method/class plus constructor side effects. Adding a feature requires knowing implicit phases and historical mirrors.

**Proposal.** Introduce immutable, validated internal feature descriptors around one uniform family (for example simple items) that declare definition, feature flag, registry/meta, handler, resources and lifecycle phase. Generate diagnostics/tests from descriptors while keeping existing handlers and public definitions. Do not force dynamic cable tiles, upgrade compatibility, spatial IDs, or integration-specific entries into a lowest-common-denominator schema.

| Required field | Assessment |
|---|---|
| Confidence | High for coupling; medium that descriptors fit enough families to justify themselves. |
| Expected blast radius | All content registration, addon definitions, recipes/resources, item/world compatibility, client render registration. |
| Compatibility/persistence risks | Registry/meta/name changes, different feature gating/order, missing tile renderer/capability, deprecated holder differences. |
| Tests before change | Snapshot registry names/meta/definitions/features; construct every enabled/disabled family; dedicated-server classloading; resource existence; old world/item stacks; optional FMP/capability matrix. |
| Safe incremental sequence | (1) Create read-only registry snapshot test. (2) wrap one simple family without changing calls. (3) compare outputs. (4) add validation/diagnostics. (5) migrate similar families. (6) leave bespoke late phases explicit. |
| Why it may not be worthwhile | AE2 content has many exceptions; a descriptor DSL can hide lifecycle logic in another abstraction and make debugging harder. |

## 7. Coremod and optional-integration compatibility seam

**Observed facts**

- `ASMIntegration` both seeds integration registry state and strips unavailable members/interfaces across numerous active core types.
- `ApiRepairer` replaces stale addon-bundled API bytes and replays transformers using fragile URL/package assumptions.
- `ASMTweaker`, the static AT, and `GuiButtonColorizer` recognize old name variants and alter vanilla/AE2 bytecode; AT/ASM overlap where invocation semantics require it.
- `IntegrationNode` detects/configures/reflectively constructs optional modules and catches broad failures. Compile-only integrations cannot all be run in the default matrix.

**Interpretation.** The seam is brittle, but brittleness partly reflects Forge 1.7.10/addon history. Replacing it wholesale could be less safe than making inputs/outputs observable.

**Proposal.** Build class-byte golden/structural tests for each transformer in MCP/SRG-like forms and optional annotations enabled/disabled. Add integration-node state/missing-class tests and explicit startup diagnostics. Isolate `ASMIntegration`'s registry seeding behind an idempotent bootstrap method only after proving FML construction order. Harden `ApiRepairer` reads/replay/error reporting while preserving output. Do not migrate to mixins or delete repair logic without packaged-addon evidence.

| Required field | Assessment |
|---|---|
| Confidence | High that tests/diagnostics help; low that a replacement mechanism can preserve the ecosystem cheaply. |
| Expected blast radius | Earliest classloading, vanilla GUI, optional mods, dedicated server, every addon consuming `appeng.api`. |
| Compatibility/persistence risks | Loader crash before logging, absent optional API linkage, old addon `NoSuchMethodError`, obfuscated-only failures. |
| Tests before change | Transformer input/output class shape; MCP/SRG/obfuscated packaged launch; optional dependency absent/present; old embedded-API addon fixture; client and dedicated server; integration failure state/crash report. |
| Safe incremental sequence | (1) Capture outputs. (2) add deterministic diagnostics. (3) harden stream/error handling byte-identically. (4) separate registry bootstrap idempotently. (5) consider larger replacement only with full modpack/addon matrix. |
| Why it may not be worthwhile | The runtime is permanently Forge 1.7.10/LaunchWrapper; a modern abstraction may add migration risk without reducing supported-version cost. |

## 8. Test/build boundary and external convention visibility

**Observed facts**

- Standard build/reobf/API/publishing behavior and CI execution live in pinned or moving external conventions/workflows, while local `build.gradle` is five lines.
- HorizonQA test classes/resources live in the main source set while its framework is dev-only; unit, functional, and game-test tasks have distinct launch semantics.
- Root README/CONTRIBUTING setup/style/artifact guidance conflicts in places with the current task graph, properties, EditorConfig, and publishing group.
- High-risk transformer, protocol, persistence, topology, Fast-planner, and repeated-lifecycle paths lack focused local tests.

**Interpretation.** Contributors can select the wrong command or infer guarantees from external configuration they cannot see. The solution is better local contracts, not cloning the convention plugin.

**Proposal.** Add small local verification tasks/tests only when they express repository-specific invariants: registry/ordinal snapshots, resource/test-class consistency, transformer fixtures, and a task that describes/runs the intended local test layers through convention-supported hooks. Keep current GTNH guidance linked and update stale root docs in a separately authorized task.

| Required field | Assessment |
|---|---|
| Confidence | High for the documentation/test-discovery mismatch. |
| Expected blast radius | Contributor workflow, CI, packaging; low runtime impact unless source sets move. |
| Compatibility/persistence risks | Duplicate/divergent build logic, published jar contents, CI-only failures, moving external workflow semantics. |
| Tests before change | Compare local and CI task inputs/artifacts; inspect main/api/functional jars; verify HorizonQA discovery; clean/offline/first-resolution builds where infrastructure permits. |
| Safe incremental sequence | (1) Add assertions/reporting without changing packaging. (2) pin/surface external versions where GTNH policy permits. (3) add repository-specific invariant tasks. (4) move test source/resources only with jar/runtime/CI proof. |
| Why it may not be worthwhile | GTNH intentionally centralizes build logic; local duplication will drift. Documentation and upstream convention improvements may be enough. |

## Narrow correctness candidates before architectural work

The following source mismatches are smaller than the ranked refactors and should each receive a focused regression test before a minimal fix:

- watcher removal key/interface mismatches in storage, crafting, and energy caches;
- reused mutable channel events and missing `finally` around crafting rebuild pauses;
- equal-priority crafting pattern collapse and stale `craftableItemsLegacy` rebuild;
- requester-link removal comparing a host to the link object rather than the requester's host;
- V2 timeout conversion and calculation terminal-state submission path;
- full idle-plus-channel energy success accounting;
- recursion/diff guard cleanup after a throwing child/listener;
- proxy deferred NBT write before readiness.

These are evidence-backed candidates, not fixes performed by this task. Some may embody an undocumented compatibility choice; reproduce the consequence first.

## What not to refactor yet

- Do not remove deprecated API merely because in-tree callers are absent; addons and `ApiRepairer` establish an external binary history.
- Do not reorder enum values, registry/meta IDs, patterns, or GUI mappings for neatness.
- Do not replace both crafting planners with one implementation until Fast/lite performance and V2 semantic equivalence are measured.
- Do not generalize every item/fluid path to arbitrary stack types without an actual supported type and end-to-end capability matrix.
- Do not rewrite coremod/registration/world lifecycle in one patch; failures occur before useful diagnostics and can corrupt compatibility.
- Do not split large files for style alone. Extract only around a behavioral seam with tests and an owner/lifetime improvement.

## Related guides

- [ME network](03-me-network.md)
- [Storage and crafting](04-storage-and-crafting.md)
- [Game objects and UI](05-game-objects-and-ui.md)
- [Startup and runtime](02-startup-and-runtime.md)
- [Development guide](07-development-guide.md)
- [Evidence index](10-evidence-index.md)
