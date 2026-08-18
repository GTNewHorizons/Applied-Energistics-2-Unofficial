# Startup and runtime lifecycle

## Why this exists

AE2 does not begin at its `@Mod` class. FML first loads a coremod that changes class shape and also seeds optional-integration state; only afterward does ordinary configuration and registration run. Server saves introduce another shorter lifetime inside the process lifetime. Understanding those nested lifetimes prevents three common failures: loading a client/optional class on the wrong side, registering an object before its definition/config exists, and retaining save-scoped state after shutdown.

This guide follows the verified startup path and names the ownership handoffs. The [project map](01-project-map.md) explains source/build boundaries; [integrations and world data](06-integrations-world-and-services.md) expands the indirect activation paths.

## The three nested lifetimes

1. **Classloader/process lifetime:** LaunchWrapper installs transformers and FML constructs mod/proxy/event objects. Static registries and event subscriptions generally live here.
2. **Mod lifecycle:** pre-init → init → post-init constructs configuration, definitions, registries, recipes, integrations, GUI/networking, and render hooks once for that process.
3. **Server/save lifetime:** about-to-start → starting → stopping → stopped opens one world's AE2 data, runs ticks, registers commands, then saves and clears world-dependent state. An integrated client can repeat this lifetime without restarting the process.

Do not treat “static” as evidence that an object should outlive a save. [`WorldData.instance()`](../../src/main/java/appeng/core/worlddata/WorldData.java#L94) is deprecated specifically because it exposes save-scoped state through a global accessor.

## Complete startup sequence

```mermaid
sequenceDiagram
    participant LW as LaunchWrapper / FML
    participant Core as AppEngCore
    participant ASM as AE2 transformers
    participant Mod as AppEng
    participant Config as AEConfig + config objects
    participant Reg as Registration
    participant Defs as ApiDefinitions / handlers
    participant Int as IntegrationRegistry
    participant Proxy as CommonHelper.proxy
    participant Net as GuiBridge / NetworkHandler

    LW->>Core: load coremod from generated manifest
    Core-->>LW: ASMIntegration, ApiRepairer, GuiButtonColorizer
    Core-->>LW: ASMTweaker access transformer
    LW->>ASM: transform classes as they load
    Note over ASM,Int: constructing ASMIntegration adds every IntegrationType

    LW->>Mod: create @Mod singleton
    Mod->>Mod: create Registration; register crash enhancement
    LW->>Mod: preInit(event)
    Mod->>Config: create AE2 config directory and config objects
    Mod->>Proxy: client-only init when physical side is client
    Mod->>Reg: preInitialize(event)
    Reg->>Defs: realize definitions and feature handlers
    Reg->>Reg: register blocks, items, tiles, recipes' handlers

    LW->>Mod: init(event)
    Mod->>Reg: initialize(event)
    Reg->>Reg: recipes, events, cable layers, cache/cell registries
    Mod->>Int: PRE_INIT then INIT optional modules

    LW->>Mod: postInit(event)
    Mod->>Reg: postInit(event)
    Reg->>Reg: FMP/cable tile, upgrades, loot, worldgen, registries
    Mod->>Int: POST_INIT optional modules
    Mod->>Proxy: postInit()
    Mod->>Net: register GUI handler; create AE2 channel
    Mod->>Config: save()
```

The sequence is conceptual at the transformer stage: LaunchWrapper calls each transformer for many class loads. The normal lifecycle order and method calls come from [`AppEng`](../../src/main/java/appeng/core/AppEng.java#L129).

## Coremod bootstrap

### How FML finds it

[`gradle.properties`](../../gradle.properties#L119) configures `transformer.AppEngCore` as the legacy coremod class. The GTNH convention generates the packaged manifest entries, including the full `appeng.transformer.AppEngCore` name, access-transformer name, and “contains FML mod” marker. [`AppEngCore`](../../src/main/java/appeng/transformer/AppEngCore.java#L29) is both an `IFMLLoadingPlugin` and a `DummyModContainer`, locked to Minecraft 1.7.10. Its embedded mod ID is `appliedenergistics2-core`, parented to the ordinary mod.

The normal [`AppEng`](../../src/main/java/appeng/core/AppEng.java#L63) dependency string orders it after the core container, requires at least the configured Forge build (`@[version,)`) and GTNHLib `>=0.11.14`, and restricts the acceptable Minecraft version. Because the core dependency is only an `after` relation, `preInit` explicitly fails when the core container is missing ([`AppEng.java:129`](../../src/main/java/appeng/core/AppEng.java#L129)).

### Installed transformations and why they exist

`AppEngCore.getASMTransformerClass` installs:

| Transformer | Load-time behavior | Compatibility reason |
|---|---|---|
| [`ASMIntegration`](../../src/main/java/appeng/transformer/asm/ASMIntegration.java#L35) | Removes `@Integration.Interface`, `@InterfaceList`, or `@Method` surfaces when their optional integration is disabled. Its constructor also adds every `IntegrationType` to `IntegrationRegistry`. | Core types can expose external capabilities when present without requiring those API classes when absent. This is active across many storage, power, P2P, cable-bus, and tool sites. |
| [`ApiRepairer`](../../src/main/java/appeng/transformer/asm/ApiRepairer.java#L24) | For `appeng.api*` classes, loads AE2's bundled API copy and replays the other LaunchWrapper transformers. | Old addons sometimes embedded stale AE2 API classes; replacement avoids linkage errors and preserves a historical binary ecosystem. |
| [`GuiButtonColorizer`](../../src/main/java/appeng/transformer/asm/GuiButtonColorizer.java#L21) | Finds vanilla `GuiButton.drawButton` under MCP/SRG/obfuscated names and redirects its first GL color call to `ScreenColor.applyButtonColorHook`. | AE2 can tint its own buttons while vanilla screens retain vanilla color. |

`getAccessTransformerClass` returns [`ASMTweaker`](../../src/main/java/appeng/transformer/asm/ASMTweaker.java#L34). It publicizes/de-finalizes `GuiContainer.drawSlot` and `AEBaseTile.readFromNBT`/`writeToNBT`, and changes the vanilla draw-screen call from `INVOKESPECIAL` to `INVOKEVIRTUAL` so AE GUI slot-render overrides dispatch. The checked-in source methods can therefore still appear `final` while the runtime methods are not.

The static [`appeng_at.cfg`](../../src/main/resources/META-INF/appeng_at.cfg) separately exposes GUI text/slot state, render methods, an NBT list, and Tessellator fields. The draw-slot overlap is deliberate: access changes alone cannot repair invocation dispatch.

### Bootstrap invariants

- Transformers must recognize development and packaged naming forms. A change can work under `runClient` and fail in a reobfuscated jar.
- An optional annotated class can trigger lazy integration pre-initialization during transformation. Configuration must exist before affected loads on the normal path.
- Runtime class shape is not source shape. Validate with the optional dependency both present and absent.
- `ApiRepairer` is brittle-looking by design around a real addon compatibility contract; do not remove it because this source tree compiles without it.

## Ordinary mod entry and instance construction

[`AppEng`](../../src/main/java/appeng/core/AppEng.java#L63) is the normal `@Mod`. [`AppEng.instance()`](../../src/main/java/appeng/core/AppEng.java#L118) is the `@Mod.InstanceFactory`. Its constructor registers `ModCrashEnhancement` and constructs a single [`Registration`](../../src/main/java/appeng/core/Registration.java) object.

The core API implementation is also process-global: [`AEApi.instance()`](../../src/main/java/appeng/api/AEApi.java#L25) reflectively retrieves [`Api.INSTANCE`](../../src/main/java/appeng/core/Api.java#L33). `Api` eagerly owns helpers, registry container, compatibility definition holders, and `ApiDefinitions`. This is why normal startup constructs configuration before first broad API use.

## Pre-initialization: establish configuration and definitions

[`AppEng.preInit`](../../src/main/java/appeng/core/AppEng.java#L129) performs, in order:

1. Verify the coremod container.
2. Register the expanded-Baubles wireless-terminal slot when applicable.
3. Create `<Forge config>/AppliedEnergistics2`.
4. Construct `AppliedEnergistics2.cfg`, `MeteoriteLootTable.json`, `Facades.cfg`, and `CustomRecipes.cfg` owners.
5. Initialize creative tabs.
6. Call client helper initialization only on the physical client.
7. Call [`Registration.preInitialize`](../../src/main/java/appeng/core/Registration.java#L136).

[`AEConfig`](../../src/main/java/appeng/core/AEConfig.java#L52) is a Forge `Configuration`, a `ConfigManager`, and the owner of enabled `AEFeature` flags. It subscribes to config changes, always enables the invisible core feature, and marks non-client GUI settings as restart-required. Some IDs and selected display/power settings are written back on save.

### Definitions are factories plus registration plans

[`ApiDefinitions`](../../src/main/java/appeng/core/ApiDefinitions.java#L33) constructs block, item, material, and part definition groups through [`DefinitionConstructor`](../../src/main/java/appeng/core/api/definitions/DefinitionConstructor.java). Constructing a definition selects its `FeatureHandler`, checks feature availability, constructs the implementation when enabled, and records the handler. It does not by itself finish all Forge registration.

`Registration.preInitialize` then:

- performs early spatial recovery/ID work;
- registers recipe handlers/sorting and the ore-dictionary listener;
- resolves the API definition groups;
- mirrors them into deprecated `Items`, `Materials`, `Parts`, and `Blocks` holders for addon compatibility; and
- invokes the collected feature handlers.

For a normal tile block, [`AETileBlockFeatureHandler`](../../src/main/java/appeng/core/features/AETileBlockFeatureHandler.java) registers the block without a vanilla item-block, registers AE2's custom item separately, registers the tile entity and client renderer, and maps the tile class to its item drop. Cable-bus registration is delayed because its final tile class is generated from capability layers later.

This indirect model means constructor reachability, feature flags, handler lists, registry names, and metadata are all part of registration. A bare `GameRegistry.register*` call is usually the wrong extension point.

## Initialization: runtime registries and integrations

[`AppEng.init`](../../src/main/java/appeng/core/AppEng.java#L181) first initializes `AEStackTypeRegistry` network IDs. It may start a low-priority CSV-export thread, initializes client Inventory Tweaks sorting, invokes [`Registration.initialize`](../../src/main/java/appeng/core/Registration.java#L508), advances integrations through `PRE_INIT` and `INIT`, then registers `CraftingNotificationManager`.

`Registration.initialize` is the key middle phase:

- run [`RecipeLoader`](../../src/main/java/appeng/core/RecipeLoader.java) and inject parsed recipes;
- register dynamic cable-bus capability layers for sided inventory, fluids, and storage monitoring;
- register `TickHandler` on the relevant event buses, plus sound and part-placement handlers;
- map nine grid-cache APIs to implementations: ticking, energy, pathing, storage, P2P, spatial, security, crafting, and item-flow;
- register external-storage and cell handlers;
- register matter-cannon ammunition, achievements, and conditional generated recipes.

The cache mapping is not only a lookup table. Every new [`Grid`](../../src/main/java/appeng/me/Grid.java#L55) reflectively constructs one instance of each registered implementation using an `IGrid` constructor. Registration order must therefore precede live grids.

Integration detection/loading is described in [the integration framework](06-integrations-world-and-services.md#optional-mod-integration-framework). Crucially, compile-only API presence at build time is distinct from a node reaching `READY` at runtime.

## Post-initialization: close the registries and expose UI/networking

[`AppEng.postInit`](../../src/main/java/appeng/core/AppEng.java#L205) calls `Registration.postInit`, advances integrations through `POST_INIT`, registers integration crash diagnostics, invokes proxy post-init, saves config, registers [`GuiBridge`](../../src/main/java/appeng/core/sync/GuiBridge.java), and creates global [`NetworkHandler.instance`](../../src/main/java/appeng/core/sync/network/NetworkHandler.java#L27) on the literal FML channel `"AE2"`.

`Registration.postInit` performs operations that require the complete mod registry:

- force/finish spatial biome/provider IDs and configure P2P types;
- initialize localization and ForgeMultipart support;
- select and register the dynamically combined cable-bus tile class;
- populate the large upgrade compatibility matrix;
- add wireless handlers, loot, trades, world generators, movable-tile defaults, dimension exclusions/whitelists, and blocking-mode ignores;
- bake ore-dictionary-dependent recipes.

There are two `FMLPostInitializationEvent` handlers in `AppEng`: the main `postInit` and `PostLoad`, which runs Postea converters for compatibility mods. Their same-phase relative ordering is implicit in the current source; code should not assume more ordering than observed.

The sided proxy completes client render registration in [`ClientHelper.postInit`](../../src/main/java/appeng/client/ClientHelper.java#L362). [`ServerHelper`](../../src/main/java/appeng/server/ServerHelper.java) supplies no-op or unsupported client functions. Dedicated-server launches remain the reliable way to catch a leaked client class.

## Messages and extension input during startup

[`AppEng.handleIMCEvent`](../../src/main/java/appeng/core/AppEng.java#L233) delegates FML inter-mod messages to [`IMCHandler`](../../src/main/java/appeng/core/IMCHandler.java#L47). Current keys can blacklist/whitelist spatial blocks, add grindables or matter-cannon ammunition, and add a P2P attunement for a tunnel type. Invalid messages are logged and skipped rather than becoming direct registry calls from another mod.

Recipes and resources are also startup inputs. `RecipeLoader` selects the default or `GTNHRecipes` import tree and can overlay user files; `mcmod.info` has Gradle-substituted tokens; feature/config state gates definitions. See [recipes and resources](06-integrations-world-and-services.md#recipe-and-resource-pipeline).

## From loaded tile to running grid

Ordinary world objects do not construct complete grid membership in their Java constructor. A network tile's proxy follows a deferred lifecycle:

1. Forge creates the tile and calls `readFromNBT`; [`AENetworkProxy.readFromNBT`](../../src/main/java/appeng/me/helpers/AENetworkProxy.java#L131) retains node identity/security/grid-storage data until a node exists.
2. `validate` calls `AENetworkProxy.validate`, enqueueing the tile through [`TickHandler.addInit`](../../src/main/java/appeng/hooks/TickHandler.java#L88).
3. At server-tick end, `TickHandler` calls `onReady` before updating grids.
4. The proxy creates a server-only [`GridNode`](../../src/main/java/appeng/me/GridNode.java), restores its NBT/owner, and calls `updateState`.
5. `GridNode` reconciles adjacent nodes and connections; connection creation merges grids, or a standalone `Grid` is created.
6. `Grid.add` updates its exact-class machine index and calls every cache's `addNode` while topology is still in transition.

Cable-bus hosts perform the analogous operation for their center cable and face parts. The full topology trace is in [ME network](03-me-network.md#placement-or-load-to-grid-membership).

## Runtime tick ownership

[`TickHandler`](../../src/main/java/appeng/hooks/TickHandler.java) is subscribed during registration. At world tick `END` it advances bounded crafting simulations. At server tick `END` it:

1. readies queued tiles;
2. calls `Grid.update()` for every live grid;
3. updates server-side color state; and
4. drains the server queue.

At world tick `START` it drains that world's callable queue under a time bound. On world unload it destroys nodes in that world and drops the world's queue; world save flushes meteor spawn data. Cache update order inside `Grid.update` is not specified because cache instances are held in a `HashMap`; do not introduce same-tick ordering dependencies between path, energy, storage, and ticking services.

`ITickManager`/`TickManagerCache` is a distinct per-grid adaptive scheduler for `IGridTickable` machines. `TickHandler` drives the grid; the grid cache decides which machine tick is due.

## Server lifecycle and shutdown

```mermaid
sequenceDiagram
    participant FML
    participant Mod as AppEng
    participant WD as WorldData
    participant TH as TickHandler
    participant Grid as live grids / GridStorage
    participant Cmd as AECommand

    FML->>Mod: serverAboutToStart(server)
    Mod->>WD: onServerAboutToStart(server)
    WD->>WD: create world/AE2 paths and services
    WD->>WD: start PlayerData, DimensionData, StorageData
    FML->>Mod: serverStarting(event)
    Mod->>Cmd: register /ae2
    loop server ticks
        FML->>TH: server/world tick events
        TH->>Grid: update live grids
    end
    FML->>Mod: serverStopping(event)
    Mod->>WD: onServerStopping()
    WD->>Grid: serialize live persistent grid state
    WD->>WD: save players/dimensions/storage/spawn; stop compass
    FML->>Mod: serverStopped(event)
    Mod->>WD: clear singleton
    Mod->>TH: shutdown queues/repositories/jobs
    Mod->>Mod: clear notifications, locatables, adaptor cache
```

[`AppEng.serverAboutToStart`](../../src/main/java/appeng/core/AppEng.java#L241) creates the [`WorldData`](../../src/main/java/appeng/core/worlddata/WorldData.java) tree under `<save>/AE2`: settings, spawn data, compass files, player IDs, dimension mappings, and storage data. `FMLServerStartingEvent` itself registers [`AECommand`](../../src/main/java/appeng/server/AECommand.java).

At `serverStopping`, every registered `WorldData` stoppable runs: player IDs/config save and reset; spatial dimensions save/unregister; `StorageData` asks loaded live `GridStorage` values to serialize and saves `settings.cfg`; compass shuts its executor; spawn data flushes. At `serverStopped`, `AppEng` clears `WorldData`, calls `TickHandler.shutdown`, clears crafting notifications, announces removal of all locatable entries, and clears the `InventoryAdaptor` cache.

Some process-global state is not explicitly reset: for example the global network handler is not nulled/unregistered, and not every static map/event registration has a visible stop callback. That is an **observed lifecycle seam**, not proof of a user-visible leak. Repeated integrated-server start/stop tests are required before changing ownership.

## Configuration and persistence ownership

Keep these levels separate:

| Owner | Examples | Save/sync path |
|---|---|---|
| Process/mod config | feature flags, integration mode, power units, recipe mode | Forge config files under `AppliedEnergistics2`; initialized in pre-init, saved after post-init/config change. |
| World object | tile/part inventories, orientation, local settings, proxy node IDs | Tile/part NBT in chunks; description streams for client rendering. |
| Grid component | overflow energy, crafting/item-flow cache identity/state | Cache `populateGridStorage` → `GridStorage` NBT. |
| Whole save | player security IDs, spatial dimensions, grid-storage registry, meteor/compass indexes | `WorldData` children under `<save>/AE2`. |
| Client replica | GUI lists/settings, tile render state | FML packets, container sync, or tile descriptions; never the durable authority. |

The grid persistence trace is in [integrations, world, and services](06-integrations-world-and-services.md#concrete-persistence-trace-grid-storage).

## Invariants and extension checklist

1. Decide which lifetime owns new state before choosing a singleton, cache, tile, or service.
2. Register normal game content through definitions/feature handlers and preserve registry names/metadata.
3. Register a grid cache before any grid can be constructed; give its implementation an `IGrid` constructor and tolerate unstable topology in add/remove callbacks.
4. Keep client classes behind the sided boundary and verify a dedicated-server launch.
5. Treat reflection conventions, annotations, resource imports, manifest/AT data, and bytecode transformers as real callers.
6. Balance every event-bus/executor/queue start with the correct world- or process-lifetime stop.
7. Preserve API, NBT, enum ordinal, registry/meta, and optional-classloading compatibility unless a migration is explicit.
8. Test development and reobfuscated class names for transformer changes; test dependency present/absent for integration changes.

## Risks and misleading names

- `AppEngCore` is a coremod container, not the normal mod entry.
- `Registration` spans all three mod phases; reading only `preInitialize` misses most registrations.
- Constructing definitions has side effects that populate handler registries, but Forge registration happens later.
- `CommonHelper.proxy` separates physical-side behavior, not logical authority.
- `AEConfig.PACKET_CHANNEL` is `"AE"` but the live `NetworkHandler` uses literal `"AE2"`; the former has no current runtime caller.
- `usesMixins=false` with `forceEnableMixins=true` is present in build properties. **Uncertain:** this tree does not document which dependency requires the forced mixin support.
- Coremod transformer failures may be swallowed or rethrown through loader-specific paths; validate the actual packaged launch rather than relying on compilation.
- World/save persistence of grid caches is visibly flushed during server stop; **Uncertain:** before asserting crash-durability guarantees, rule out convention/framework flushes not visible in these calls and test abrupt termination.

## Related guides

- [Project map](01-project-map.md)
- [ME network](03-me-network.md)
- [Game objects and UI](05-game-objects-and-ui.md)
- [Integrations, world, and services](06-integrations-world-and-services.md)
- [Development guide](07-development-guide.md)
- [Evidence index](10-evidence-index.md)
