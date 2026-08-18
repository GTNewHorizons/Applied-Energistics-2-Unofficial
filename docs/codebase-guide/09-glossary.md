# Glossary

## Why this exists

Minecraft, Forge, GTNH, and AE2 each reuse ordinary words—world, side, network, container, cache, cell—with specialized meanings. This glossary defines them from first principles and names a canonical repository example. Definitions describe this codebase; where an analogy is used, its limit is stated.

## Platform and lifecycle

### Minecraft 1.7.10

The game version targeted by this fork. It predates modern Minecraft's block-state/model/network APIs, so AE2 uses numeric metadata, `TileEntity`, pre-model renderers, vanilla `Container`, Forge/FML channels, and LaunchWrapper bytecode transformers. The version is fixed in [`gradle.properties`](../../gradle.properties#L23) and [`AppEng`](../../src/main/java/appeng/core/AppEng.java#L63).

### GTNH / GT New Horizons

A large Minecraft 1.7.10 modpack and development ecosystem. This repository is its AE2 fork and uses the GTNH Gradle convention plugin plus GTNH-specific dependency/runtime/test infrastructure. “GTNH” is context, not one Java subsystem.

### Forge

The Minecraft modding platform/API. It supplies registries, events, world generators, capabilities/interfaces, networking support, configuration, and lifecycle integration. `GameRegistry`, `MinecraftForge.EVENT_BUS`, and `IWorldGenerator` are examples used by [`Registration`](../../src/main/java/appeng/core/Registration.java).

### FML

Forge Mod Loader, the loader/lifecycle layer used by this version of Forge. It discovers the `@Mod`, fires pre-init/init/post-init/server events, creates sided proxies and event channels, and loads coremods. [`AppEng`](../../src/main/java/appeng/core/AppEng.java) is the ordinary FML entry; [`AppEngCore`](../../src/main/java/appeng/transformer/AppEngCore.java) is the earlier loading-plugin entry.

### Mod lifecycle

The ordered FML phases in which a mod configures and registers itself. In AE2 the central phases are `AppEng.preInit`, `init`, `postInit`, `serverAboutToStart`, `serverStarting`, `serverStopping`, and `serverStopped`. Phase order is an architectural dependency: definitions exist before handler registration; integrations initialize after core registries; world data exists only for an active save.

### Physical side

The Java process/environment on which code is loaded: client or dedicated server. It is not the same as logical authority. A client process contains an integrated server, so “running in a client process” does not make client GUI state authoritative.

### Logical side / authority

The side responsible for a particular state transition. The server owns world, inventory, security, power, and crafting mutations. A GUI produces intent and displays a replica. See the terminal trace in [game objects and UI](05-game-objects-and-ui.md#fully-traced-interaction-terminal-left-click-withdraw-or-deposit).

### Sided proxy

FML-selected implementation used to keep client-only classes and behavior off a dedicated server. [`CommonHelper.proxy`](../../src/main/java/appeng/core/CommonHelper.java#L28) is a `ClientHelper` on the client and `ServerHelper` on the server. The boundary is imperfect because `ClientHelper` extends `ServerHelper`; callers must still respect side-safe classloading.

### Event bus

A registry that invokes subscribed handlers for events without direct callers. AE2 registers on Forge and FML buses. A method reached only by an event subscription is live even when a call search finds no Java invocation.

### Tick

One game update step. [`TickHandler`](../../src/main/java/appeng/hooks/TickHandler.java) uses server/world tick phases to ready tiles, update grids, advance bounded crafting simulations, and drain queues. Grid ticking also has a separate `ITickManager` service for network hosts.

## Bootstrap and compatibility

### Coremod

A mod loaded early enough to transform classes before normal mod initialization. AE2's [`AppEngCore`](../../src/main/java/appeng/transformer/AppEngCore.java) installs optional-integration, API-repair, GUI-color, and access/invocation transformers. Coremods are version-sensitive because they depend on bytecode and mapped names.

### ASM / bytecode transformer

ASM is the bytecode library; a transformer receives class bytes and returns modified bytes as LaunchWrapper loads a class. [`ASMIntegration`](../../src/main/java/appeng/transformer/asm/ASMIntegration.java) removes unavailable optional interfaces/methods, while [`GuiButtonColorizer`](../../src/main/java/appeng/transformer/asm/GuiButtonColorizer.java) redirects a vanilla rendering call. Source shape can therefore differ from runtime shape.

### Access transformer (AT)

A Forge mechanism that changes Java access/finality at load time. AE2's [`appeng_at.cfg`](../../src/main/resources/META-INF/appeng_at.cfg) exposes selected GUI, rendering, and NBT members. `ASMTweaker` provides additional method/invocation changes that a static AT cannot express.

### Obfuscated, SRG, and MCP names

Three name forms encountered by a 1.7.10 transformer: production Minecraft's obfuscated names, stable-ish SRG names, and developer-readable MCP names. AE2 transformer tables recognize multiple forms so they work in development and packaged games.

### Jabel

A compilation technique that accepts newer Java source syntax while emitting bytecode compatible with older Java targets. This repository enables it in [`gradle.properties`](../../gradle.properties#L42). It does not modernize Minecraft/Forge runtime APIs.

### Optional integration

An adapter activated only when configuration, physical side, and another mod/API allow it. `IntegrationType`, `IntegrationRegistry`, and `IntegrationNode` own discovery/lifecycle. Optional does not mean unused; external mods can call the adapter or a transformed interface.

### IMC

Inter-Mod Communications, FML messages that let another mod configure AE2 without linking directly to its implementation. [`IMCHandler`](../../src/main/java/appeng/core/IMCHandler.java) recognizes spatial, grindable, ammunition, and P2P-attunement messages.

## World objects and UI

### Block

A registered world-position type. An [`AEBaseTileBlock`](../../src/main/java/appeng/block/AEBaseTileBlock.java) delegates changing per-position state to a tile entity. A block is a type, not the live machine instance.

### Item / `ItemStack`

An item is a registered type; an `ItemStack` is a mutable vanilla value carrying that item, count, damage/metadata, and optional NBT. Registry names and damage values can be persisted compatibility contracts. AE2's generic `IAEStack` should not be confused with vanilla `ItemStack`.

### Tile entity

A Java object attached to a block position for changing state, inventories, NBT, and updates. [`AEBaseTile`](../../src/main/java/appeng/tile/AEBaseTile.java) reflectively dispatches methods annotated with `@TileEvent`. Save NBT and render-description streams are separate paths.

### Multipart part

An `IPart` mounted at the center or one face of a cable-bus host. [`TileCableBus`](../../src/main/java/appeng/tile/networking/TileCableBus.java) owns a [`CableBusContainer`](../../src/main/java/appeng/parts/CableBusContainer.java), which can host a cable and face devices such as terminals or buses. Here “container” is a part host, not a GUI container.

### Facade

A cosmetic cover rendered on a cable-bus face while preserving multipart/network behavior. Facades participate in collision/render/neighbor logic but are not ordinary network nodes.

### Container (GUI sense)

A vanilla server/client object that coordinates slots and an open screen. AE2 `Container*` classes exist on both sides, but the server copy validates and mutates authoritative state. [`AEBaseContainer`](../../src/main/java/appeng/container/AEBaseContainer.java) owns the action source, host anchor, slots, permission checks, and sync mechanisms.

### GUI

The client-only screen that renders state and translates input into intent. [`GuiBridge`](../../src/main/java/appeng/core/sync/GuiBridge.java) maps a server container to a convention-named `Gui*` class. A GUI must not directly mutate server/network storage.

### TESR / ISBRH

Legacy rendering mechanisms. A Tile Entity Special Renderer draws dynamic tile content; `ISimpleBlockRenderingHandler` draws custom block inventory/world geometry. AE2 wraps these through `TESRWrapper`, `WorldRender`, and multipart `BusRenderer`.

### Packet / channel

A serialized message over the FML event channel. AE2's runtime channel is `"AE2"`; [`NetworkHandler`](../../src/main/java/appeng/core/sync/network/NetworkHandler.java) dispatches `AppEngPacket` implementations. Do not confuse this transport channel with an ME cable **channel**.

### Replica / synchronization

A client-side copy suitable for display, updated from server messages. Slot sync, `@GuiSync`, `SyncManager`, purpose-built packets, and tile description packets are different replica paths. Replica state is not authority.

## ME graph model

### ME

“Matter Energy,” AE2's name for its network system. In code it encompasses graph topology and services for storage, power, channels, crafting, security, P2P, ticking, and more. It is not synonymous with item storage alone.

### Grid host / `IGridHost`

A tile or part that owns or exposes one or more grid nodes and supplies a grid-block description. The host is the game object; the node is its graph identity.

### Grid block / `IGridBlock`

The node-facing behavior supplied by a host: world location, connectable sides, flags, channel capacity/use, idle power, color, and callbacks. Despite its name, it need not be a Minecraft `Block`; parts implement it too.

### Grid node / `IGridNode`

A vertex in the ME graph, implemented by [`GridNode`](../../src/main/java/appeng/me/GridNode.java). It owns links, host/grid-block references, grid membership, flags, channel state, persisted identity, and lifecycle. A machine can expose multiple nodes.

### Grid connection / `IGridConnection`

An edge between two nodes, implemented by [`GridConnection`](../../src/main/java/appeng/me/GridConnection.java). Creating/destroying it can merge/split connected components and trigger path/channel rebuilds.

### Grid / `IGrid`

One connected component of nodes, implemented by [`Grid`](../../src/main/java/appeng/me/Grid.java). It owns a set of per-component service caches and persistent `GridStorage`. A grid is not a permanent network identity: topology changes can merge or split it.

### Grid cache / `IGridCache`

A service instance owned by one grid. Registration maps API cache interfaces to implementations. Examples include `PathGridCache`, `EnergyGridCache`, `GridStorageCache`, `CraftingGridCache`, `TickManagerCache`, `SecurityCache`, and `P2PCache`. “Cache” is historical/misleading: many are authoritative aggregators or active schedulers, not disposable memoization.

### Grid event

An in-process notification posted to grid machines/caches, such as node add/remove, power status, channel state, or storage changes. It is unrelated to the Forge event bus and unrelated to network packets.

### Controller

A multiblock-capable network device that participates in path/channel allocation and can impose controller-path rules. Controller state is computed by `PathGridCache`, not merely by checking for one controller block.

### ME channel

A finite logical resource assigned along paths from channel sources/controllers to channel-using devices. Dense cable carries more than normal cable; node flags describe usage/capacity. Pathing recalculates assignments after topology/constraint changes. It has nothing to do with the `"AE2"` packet channel.

### Pathing

The `PathGridCache` process that determines controller state, routes, channel capacity/use, and node active status. It uses topology and flags; it is not general shortest-path routing for items.

### Active node

A node that is connected/ready and satisfies relevant energy/channel/path conditions. “Present in a grid” and “active” are different states, so caches often distinguish node add/remove from active-state events.

### Grid proxy

A host-side helper that creates/manages a grid node and offers convenient access to grid services. [`AENetworkProxy`](../../src/main/java/appeng/me/helpers/AENetworkProxy.java) is not a grid and does not replace cache authority.

### Grid storage / `GridStorage`

Persistent state associated with a grid across save/load, identified in save-wide `StorageData`. Caches serialize selected network-wide state into its NBT. It is not the network's item inventory; `GridStorageCache` is the storage aggregation service, while `GridStorage` is persistence infrastructure.

### Security terminal / security cache

The mechanism mapping players to permissions such as inject/extract/build. [`SecurityCache`](../../src/main/java/appeng/me/cache/SecurityCache.java) calculates network security state. Storage actions carry a `BaseActionSource` such as `PlayerSource` so enforcement can occur below the GUI.

## Storage

### `IAEStack` / stack type

AE2's generic stack abstraction: a type/key plus quantity and metadata such as craftable/requestable counts. Concrete stack types include items and fluids. [`AEStackTypeRegistry`](../../src/main/java/appeng/api/storage/data/AEStackTypeRegistry.java) assigns network IDs and codecs. It is not necessarily a vanilla `ItemStack`.

### `IMEInventory`

The basic typed insertion/extraction/listing contract. `Actionable.SIMULATE` asks what would happen; `MODULATE` performs it. Insertion normally returns the remainder that did not fit; extraction returns what was obtained.

### Storage monitor

An inventory view that can notify listeners of changes, represented by `IMEMonitor`/`IMEMonitorHandlerReceiver`. [`NetworkMonitor`](../../src/main/java/appeng/me/cache/NetworkMonitor.java) combines network inventory behavior and change propagation.

### Storage cell

An item-backed storage medium with type/byte limits, partitioning, and upgrade behavior. A drive hosts cells, while cell handlers expose their contents to the network storage cache. The cell is a contributor, not the whole network inventory.

### Cell inventory / cell handler

The object that interprets a cell item's NBT as typed storage and exposes insert/extract/list behavior. `CellInventoryHandler` layers configuration/upgrades over the underlying inventory. Registry handlers let addons supply other cell types.

### Storage bus / external storage

A part that adapts an adjacent inventory or optional capability into an ME storage handler. It contributes to the same priority-ordered network aggregate as cells. Recursion guards matter when external inventories route back into the same network.

### Priority

Ordering used by the federated storage handler when choosing contributors. It affects where insertion goes and where extraction searches; it is not a transaction guarantee across all handlers.

### Action source

Context describing who/what requested a storage action—commonly `PlayerSource`, `MachineSource`, or a base source. Security and audit-sensitive behavior depends on preserving it through layers.

## Crafting

### Crafting pattern / provider

An encoded recipe plus its inputs/outputs, exposed to the network by a provider such as an interface. `CraftingGridCache` indexes patterns/providers so planners can find production paths.

### Crafting job / planner

A computed plan for producing a requested stack. This tree contains distinct planner implementations in `appeng.crafting.v2` and `appeng.crafting.fast`, plus compatibility/legacy-facing job forms. A plan is not execution and does not reserve a CPU by itself.

### Crafting CPU

The player's name for the multiblock resource that stores crafting bytes/co-processors and executes a selected job. In code, cluster discovery and execution are separated: `CraftingGridCache` tracks available CPU clusters; [`CraftingCPUCluster`](../../src/main/java/appeng/me/cluster/implementations/CraftingCPUCluster.java) holds execution state.

### Cluster

A validated multiblock assembly represented as one logical machine. A crafting CPU cluster aggregates multiple blocks and inventories. Cluster membership can be rebuilt when blocks load/unload/change.

### Crafting link

A persistent relationship connecting a submitted job, requesting machine/player context, and executing CPU. Links support status, cancellation, and save/load reconciliation.

### Crafting bytes / co-processor

CPU resources derived from multiblock components. Bytes constrain plan/job size; co-processors permit more concurrent crafting activity. They are capacities, not stored ingredients.

## Persistence and resources

### NBT

Minecraft's typed tree serialization format. Items, tiles, parts, grid caches, and crafting state use NBT at different ownership levels. Keys and readers are persistence contracts; changing them needs migration or a compatible fallback.

### Description packet

A vanilla tile update packet carrying AE2's compact `NETWORK_WRITE` stream to nearby clients for rendering. It is not durable NBT and not the AE2 FML packet channel.

### Registry name / metadata

Stable identifiers stored in worlds/items. In 1.7.10, one registered item can multiplex variants through numeric damage/metadata (`PartType`, materials). Reordering/renumbering can corrupt old stacks even if source compiles.

### Recipe root / custom recipe

The bundled import tree parsed by AE2's recipe language. `RecipeLoader` selects normal or `GTNHRecipes` resources and can copy them into generated/user config trees. User files override generated siblings; generated copies are not checked-in sources.

### HorizonQA

GTNH's structure-driven in-game test framework. AE2's test holders live in [`appeng.gametests`](../../src/main/java/appeng/gametests) and templates under the resource `horizonqastructures` directory. It is distinct from JUnit unit and functional-test layers.

## Navigation cautions

- `NetworkHandler` means packet transport; `NetworkInventoryHandler` means federated ME storage; neither is the graph itself.
- `GridStorage` means persistence; `GridStorageCache` means the network storage service.
- `CableBusContainer` hosts parts; `AEBaseContainer` hosts a player GUI session.
- “Cache” often means a grid-owned service with authority and lifecycle, not safely discardable computed data.
- “Client” can mean a physical process or a logical presentation side. State authority must be determined separately.
- Deprecated, legacy, generated, reflective, annotated, or transformer-only code may be live compatibility behavior.

## Related guides

- [Overview](README.md)
- [ME network](03-me-network.md)
- [Storage and crafting](04-storage-and-crafting.md)
- [Game objects and UI](05-game-objects-and-ui.md)
- [Evidence index](10-evidence-index.md)
