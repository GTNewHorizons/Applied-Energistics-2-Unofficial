# Evidence index

## Why this exists

The narrative guides explain behavior; this page is a jump table from a concept to its canonical contract, implementation/entry method, and corroborating test/config/resource. It favors a few authoritative starting points over exhaustive class lists. Paths and line anchors describe the tree inspected at commit `70e83130b` plus its local working state on 2026-08-09; search the named symbol when later edits move a line.

“Gap” means no focused proof was found in the current repository, not that the behavior is broken. Test-layer limitations are explained in the [development guide](07-development-guide.md#test-layers-and-what-they-prove).

## Build, packaging, and source sets

| Concept | Configuration/contract | Implementation or entry | Tests/resources/evidence |
|---|---|---|---|
| GTNH convention build | [`settings.gradle`](../../settings.gradle#L19), [`build.gradle`](../../build.gradle) | `com.gtnewhorizons.gtnhsettingsconvention` `2.0.26`; `com.gtnewhorizons.gtnhconvention` | `./gradlew tasks --all` current task graph; external convention implementation is not in-tree. |
| Game/Forge/mappings | [`gradle.properties`](../../gradle.properties#L23) | Minecraft `1.7.10`, Forge `10.13.4.1614`, MCP stable 12 | [`AppEng` annotation/dependencies](../../src/main/java/appeng/core/AppEng.java#L63) cross-check runtime restriction. |
| Wrapper/toolchain/Jabel | [`gradle-wrapper.properties`](../../gradle/wrapper/gradle-wrapper.properties#L3), [`gradle-daemon-jvm.properties`](../../gradle/gradle-daemon-jvm.properties#L2), [`gradle.properties`](../../gradle.properties#L42) | Gradle 9.4.0, daemon toolchain 25, modern syntax to Java 8-compatible bytecode | Compile/build tasks are convention-owned. |
| Dependencies/integration APIs | [`dependencies.gradle`](../../dependencies.gradle) | implementation, `compileOnlyApi`, functional JUnit, dev runtime, HorizonQA configurations | Runtime activation is separately gated by `IntegrationNode`. |
| Main/API artifact | [`apiPackage=api`](../../gradle.properties#L90), [`appeng.api` package marker](../../src/main/java/appeng/api/package-info.java) | Convention tasks `apiClasses`, `apiJar`; generated `appeng.core.BuildTags` | API has its own [`LICENSE`](../../src/main/java/appeng/api/LICENSE); token input in [`mcmod.info`](../../src/main/resources/mcmod.info). |
| Functional source set | [`addon.gradle`](../../addon.gradle#L4) | `functionalTest`, `functionalTestJar`, `runFunctionalTestServer/Client` | [`AppengTestMod`](../../src/functionalTest/java/appeng/test/AppengTestMod.java#L30), `junit-out` XML at runtime. |
| Formatting/static checks | [`gradle.properties`](../../gradle.properties#L207), [`.editorconfig`](../../.editorconfig) | `spotlessCheck`/`spotlessApply`, Checkstyle tasks | [`CONTRIBUTING.md`](../../CONTRIBUTING.md#L42) asks for diff checks, but later style examples are stale. |
| Coremod/AT packaging | [`coreModClass`](../../gradle.properties#L119), [`appeng_at.cfg`](../../src/main/resources/META-INF/appeng_at.cfg) | Convention-generated manifest → `AppEngCore` | Validate a packaged/reobfuscated client and dedicated server; gap: no focused transformer-output test. |

## Bootstrap, lifecycle, configuration, and registration

| Concept | Public/config surface | Implementation and entry methods | Corroboration/gaps |
|---|---|---|---|
| Coremod container | FML `IFMLLoadingPlugin`, `DummyModContainer` | [`AppEngCore`](../../src/main/java/appeng/transformer/AppEngCore.java#L29), `getASMTransformerClass`, `getAccessTransformerClass` | `AppEng.preInit` verifies the core container; generated manifest configured by Gradle. |
| Optional interface stripping | [`@Integration`](../../src/main/java/appeng/transformer/annotations/Integration.java) annotations | [`ASMIntegration.transform/removeOptionals`](../../src/main/java/appeng/transformer/asm/ASMIntegration.java#L67) | Active annotated sites include `IStorageBus`, cable-bus, power, P2P, storage bus, tools. Gap: absent/present transformation fixture. |
| Bundled API repair | `appeng.api` package compatibility | [`ApiRepairer.transform`](../../src/main/java/appeng/transformer/asm/ApiRepairer.java#L40) | Source comment identifies addons embedding old API. Gap: representative old-addon jar fixture. |
| GUI bytecode hooks | Vanilla GUI methods; AT entries | [`ASMTweaker`](../../src/main/java/appeng/transformer/asm/ASMTweaker.java), [`GuiButtonColorizer`](../../src/main/java/appeng/transformer/asm/GuiButtonColorizer.java) | [`AEBaseGui`](../../src/main/java/appeng/client/gui/AEBaseGui.java), [`ScreenColor`](../../src/main/java/appeng/client/gui/ScreenColor.java) are consumers. |
| Normal mod lifecycle | FML `@Mod` and lifecycle events | [`AppEng`](../../src/main/java/appeng/core/AppEng.java#L63): `preInit`, `init`, `postInit`, server events, IMC | Dependency string and config/resource construction in the same class. |
| Physical-side proxy | [`CommonHelper.proxy`](../../src/main/java/appeng/core/CommonHelper.java#L28) | [`ClientHelper.init/postInit`](../../src/main/java/appeng/client/ClientHelper.java#L109), [`ServerHelper`](../../src/main/java/appeng/server/ServerHelper.java) | Dedicated-server launch is the practical side-safety test. |
| Global configuration/features | `AEFeature`, Forge `Configuration`, config manager | [`AEConfig`](../../src/main/java/appeng/core/AEConfig.java#L52), constructor/load/save/config-change handler | Files created by `AppEng.preInit`; non-client settings are restart-required. |
| API definition construction | `IBlocks`, `IItems`, `IMaterials`, `IParts`, definitions API | [`ApiDefinitions`](../../src/main/java/appeng/core/ApiDefinitions.java#L33), [`DefinitionConstructor`](../../src/main/java/appeng/core/api/definitions/DefinitionConstructor.java) | Feature-handler collection is constructor-driven; registry/meta values need world/item compatibility tests. |
| Forge feature registration | Definition/feature handlers | [`Registration.preInitialize`](../../src/main/java/appeng/core/Registration.java#L136), [`AETileBlockFeatureHandler`](../../src/main/java/appeng/core/features/AETileBlockFeatureHandler.java) | Cross-check registered resource/definition and constructed block/item/tile. |
| Runtime registries/caches | [`IRegistryContainer`](../../src/main/java/appeng/api/features/IRegistryContainer.java) | [`RegistryContainer`](../../src/main/java/appeng/core/features/registries/RegistryContainer.java), [`Registration.initialize`](../../src/main/java/appeng/core/Registration.java#L508) | Grid cache mappings at `Registration.java:543`; cells/external storage/recipes/events nearby. |
| Post-registration wiring | Feature/integration state | [`Registration.postInit`](../../src/main/java/appeng/core/Registration.java#L593), `AppEng.postInit` | Cable-bus runtime tile, upgrades, loot/trades/worldgen, P2P, GUI/channel. |
| IMC extension | FML IMC keys | [`IMCHandler.handleMessage`](../../src/main/java/appeng/core/IMCHandler.java#L47) | Gap: focused tests for invalid/each supported message. |

## Public API boundary

| Concept | API | Internal implementation | Compatibility evidence |
|---|---|---|---|
| API entry | [`AEApi.instance`](../../src/main/java/appeng/api/AEApi.java#L25), [`IAppEngApi`](../../src/main/java/appeng/api/IAppEngApi.java) | [`Api.INSTANCE`](../../src/main/java/appeng/core/Api.java#L33) | Reflection deliberately separates API artifact; `ApiRepairer` preserves old embedded copies. |
| Definitions | `IBlocks`, `IItems`, `IMaterials`, `IParts`, definitions | `ApiDefinitions` and feature handlers | `Registration.assign*` mirrors definitions into deprecated holders; deprecation is not dead-code proof. |
| Registries | [`IRegistryContainer`](../../src/main/java/appeng/api/features/IRegistryContainer.java) and registry interfaces | [`RegistryContainer`](../../src/main/java/appeng/core/features/registries/RegistryContainer.java) and implementations | External addon surface plus IMC. |
| Grid factories | `IAppEngApi.createGridNode/createGridConnection` | [`Api.createGridNode/createGridConnection`](../../src/main/java/appeng/core/Api.java#L99) | Node factory rejects client-side creation. |
| Part helper | `IPartHelper` | [`ApiPart`](../../src/main/java/appeng/core/api/ApiPart.java) | Runtime ASM-generated cable-bus tile layers; class shape absent from source. |

## Blocks, tiles, parts, rendering, UI, and packets

| Concept | Contract/type | Implementation and entry | Test/resource evidence |
|---|---|---|---|
| Base block | Forge `Block`, feature definitions | [`AEBaseBlock`](../../src/main/java/appeng/block/AEBaseBlock.java) | Registration/definition and texture/lang resources. |
| Tile block | Forge `ITileEntityProvider` | [`AEBaseTileBlock.createNewTileEntity`](../../src/main/java/appeng/block/AEBaseTileBlock.java#L116), activation/drop paths | HorizonQA machine structures exercise representative blocks. |
| Tile state/events | `@TileEvent`, `TileEventType` | [`AEBaseTile.getEventToHandlers`](../../src/main/java/appeng/tile/AEBaseTile.java#L275), NBT/tick/description callbacks | Gap: focused reflective-dispatch and stream-compatibility tests. |
| Network tile/proxy | `IGridHost`, `IGridProxyable` | [`AENetworkTile`](../../src/main/java/appeng/tile/grid/AENetworkTile.java), [`AENetworkProxy.onReady/getNode`](../../src/main/java/appeng/me/helpers/AENetworkProxy.java#L108) | [`NetworkCoreTests`](../../src/main/java/appeng/gametests/network/NetworkCoreTests.java). |
| Cable-bus host | `IPartHost`, `IPart` | [`TileCableBus`](../../src/main/java/appeng/tile/networking/TileCableBus.java#L51), [`CableBusContainer`](../../src/main/java/appeng/parts/CableBusContainer.java) | Network/P2P/bus game tests and cable-bus resources. |
| Part metadata/factory | `IPart`, `IPartItem` | [`ItemMultiPart`](../../src/main/java/appeng/items/parts/ItemMultiPart.java), [`PartType`](../../src/main/java/appeng/items/parts/PartType.java#L67) | Damage values/features/integration gates are persisted compatibility surfaces. |
| Runtime tile layers | Optional capability interfaces | [`ApiPart.getCombinedInstance`](../../src/main/java/appeng/core/api/ApiPart.java#L70), `BlockCableBus.setupTile` | Gap: generated-class capability matrix with optional mods absent/present. |
| Block rendering | Forge `ISimpleBlockRenderingHandler` | [`WorldRender`](../../src/main/java/appeng/client/render/WorldRender.java), `BaseBlockRender` implementations | Manual client/visual verification. |
| Tile/part rendering | TESR and bus renderer contracts | `TESRWrapper`, [`BusRenderer`](../../src/main/java/appeng/client/render/BusRenderer.java), `ClientHelper.bindTileEntitySpecialRenderer` | Manual client; gap: automated visual/side-load test. |
| GUI registry/open | FML GUI handler; `IGuiItem`, part/tile host | [`GuiBridge`](../../src/main/java/appeng/core/sync/GuiBridge.java#L146), [`Platform.openGUI`](../../src/main/java/appeng/util/Platform.java#L365) | Naming reflection maps `Container*` to `Gui*`; ordinals cross wire. |
| Container authority | vanilla `Container`, action source | [`AEBaseContainer`](../../src/main/java/appeng/container/AEBaseContainer.java), [`ContainerMEMonitorable`](../../src/main/java/appeng/container/implementations/ContainerMEMonitorable.java) | Gap: adversarial/stale GUI packet tests. |
| GUI replica | client `GuiContainer` | [`GuiMEMonitorable`](../../src/main/java/appeng/client/gui/implementations/GuiMEMonitorable.java), `ItemRepo` | Sender/receiver trace in [UI guide](05-game-objects-and-ui.md#fully-traced-interaction-terminal-left-click-withdraw-or-deposit). |
| Packet registry/transport | `AppEngPacket`, enum packet registry | [`NetworkHandler`](../../src/main/java/appeng/core/sync/network/NetworkHandler.java#L27), [`AppEngServerPacketHandler`](../../src/main/java/appeng/core/sync/network/AppEngServerPacketHandler.java#L25) | Channel literal `AE2`; packet/action/GUI ordinals are compatibility surface. |
| Terminal action | `MonitorableAction` | [`PacketMonitorableAction.serverPacketData`](../../src/main/java/appeng/core/sync/packets/PacketMonitorableAction.java#L43), `ContainerMEMonitorable.doMonitorableAction` | Server re-resolves target, player hand, power and permissions. |
| ME-list update | `IMEMonitorHandlerReceiver` | [`ContainerMEMonitorable.postChange`](../../src/main/java/appeng/container/implementations/ContainerMEMonitorable.java#L486), [`PacketMEInventoryUpdate`](../../src/main/java/appeng/core/sync/packets/PacketMEInventoryUpdate.java) | Client packet calls `GuiMEMonitorable.postUpdate`. |

## ME graph and grid services

| Concept | Canonical API | Implementation/entry | Tests/evidence |
|---|---|---|---|
| Host/block description | [`IGridHost`](../../src/main/java/appeng/api/networking/IGridHost.java), [`IGridBlock`](../../src/main/java/appeng/api/networking/IGridBlock.java) | `AENetworkProxy`, network tiles/parts | `NetworkCoreTests.networkBootsAndActivatesDevices`. |
| Node | [`IGridNode`](../../src/main/java/appeng/api/networking/IGridNode.java) | [`GridNode.updateState`](../../src/main/java/appeng/me/GridNode.java#L197), NBT/load/destroy | Network core/P2P tests; gap: save-before-ready and unload reconstruction. |
| Edge | [`IGridConnection`](../../src/main/java/appeng/api/networking/IGridConnection.java) | [`GridConnection` constructor](../../src/main/java/appeng/me/GridConnection.java#L204), [`destroy`](../../src/main/java/appeng/me/GridConnection.java#L88) | Gap: duplicate/self/security rejection and partial-callback failure. |
| Component | [`IGrid`](../../src/main/java/appeng/api/networking/IGrid.java) | [`Grid`](../../src/main/java/appeng/me/Grid.java), `GridPropagator`, `GridSplitDetector` | `NetworkCoreTests.splitAndMergePreservesStorageVisibility`. |
| Cache lifecycle | [`IGridCache`](../../src/main/java/appeng/api/networking/IGridCache.java) | `Grid` constructor/add/remove/update/saveState | Gap: ordinary split versus persisted reconstruction characterization. |
| Grid event bus | `MENetworkEvent`, event subclasses/annotation | [`NetworkEventBus`](../../src/main/java/appeng/me/NetworkEventBus.java), publishers/subscribers | Gap: cancellation/static-event/exact-class/error tests. |
| Controller/path/channels | [`IPathingGrid`](../../src/main/java/appeng/api/networking/pathing/IPathingGrid.java), `GridFlags` | [`PathGridCache`](../../src/main/java/appeng/me/cache/PathGridCache.java), [`PathingCalculation`](../../src/main/java/appeng/me/pathfinding/PathingCalculation.java), `ChannelFinalizer` | Network core channel-limit test; gap: controller conflict/dense/compressed/multiblock cases. |
| Power | [`IEnergyGrid`](../../src/main/java/appeng/api/networking/energy/IEnergyGrid.java) | [`EnergyGridCache`](../../src/main/java/appeng/me/cache/EnergyGridCache.java), quartz-fiber bridge | [`NetworkPowerTests`](../../src/main/java/appeng/gametests/network/power/NetworkPowerTests.java); gap: full idle+channel accounting/watchers/quartz recursion. |
| Device ticking | [`ITickManager`](../../src/main/java/appeng/api/networking/ticking/ITickManager.java), `IGridTickable`, `TickingRequest` | [`TickManagerCache`](../../src/main/java/appeng/me/cache/TickManagerCache.java), `TickTracker` | Gap: modulation/request-category transition tests. |
| Security | [`ISecurityGrid`](../../src/main/java/appeng/api/networking/security/ISecurityGrid.java), `ISecurityProvider` | [`SecurityCache`](../../src/main/java/appeng/me/cache/SecurityCache.java), [`TileSecurity`](../../src/main/java/appeng/tile/misc/TileSecurity.java), `Platform.securityCheck` | Gap: startup window, multiple providers, join denial, permission transitions. |
| P2P | P2P registry/cache/tunnel APIs | [`P2PCache`](../../src/main/java/appeng/me/cache/P2PCache.java), [`PartP2PTunnelME`](../../src/main/java/appeng/parts/p2p/PartP2PTunnelME.java) | [`P2PTests`](../../src/main/java/appeng/gametests/network/p2p/P2PTests.java), `p2p_tunnels` structure. |
| Persistent component | [`IGridStorage`](../../src/main/java/appeng/api/networking/IGridStorage.java) | [`GridStorage`](../../src/main/java/appeng/me/GridStorage.java), [`StorageData`](../../src/main/java/appeng/core/worlddata/StorageData.java) | Gap: corrupt/historical data, crash durability, split payload semantics. |

## Storage

| Concept | Canonical API | Implementation/entry | Tests/evidence |
|---|---|---|---|
| Typed stack registry | [`AEStackTypeRegistry`](../../src/main/java/appeng/api/storage/data/AEStackTypeRegistry.java) | stack registrations/codecs; initialized by `AppEng.init` | Packet/list/cell paths exercise item and optional fluid types; gap: ID compatibility fixture. |
| Generic stack/list | `IAEStack`, `IItemList`, storage-channel/type APIs | `AEItemStack`/`AEFluidStack`, `ItemList`, converters in `appeng.util.item`/`fluid` | Functional planner/storage tests. |
| Inventory contract | [`IMEInventory`](../../src/main/java/appeng/api/storage/IMEInventory.java), `Actionable` | typed handlers and adapters | Simulate/modulate/remainder semantics in implementations; gap: broad contract suite. |
| Grid storage service | [`IStorageGrid`](../../src/main/java/appeng/api/networking/storage/IStorageGrid.java) | [`GridStorageCache`](../../src/main/java/appeng/me/cache/GridStorageCache.java), [`NetworkMonitor`](../../src/main/java/appeng/me/cache/NetworkMonitor.java) | NetworkCore split/merge visibility and drive/bus game tests. |
| Federated routing | `IMEInventoryHandler`, priorities/action source | [`NetworkInventoryHandler`](../../src/main/java/appeng/me/storage/NetworkInventoryHandler.java) | [`NetworkInventoryHandlerFunctionalTest`](../../src/functionalTest/java/appeng/test/me/storage/NetworkInventoryHandlerFunctionalTest.java) is narrow; game tests cover representative buses. |
| Cell registry/item | [`IStorageCell`](../../src/main/java/appeng/api/implementations/items/IStorageCell.java), cell registry API | `ItemBasicStorageCell`, `ItemAdvancedStorageCell`, creative/void/extreme variants | [`DriveAndCellTests`](../../src/main/java/appeng/gametests/storage/drive/DriveAndCellTests.java) and structures. |
| Cell NBT inventory | [`ICellInventory`](../../src/main/java/appeng/api/storage/ICellInventory.java), `ICellInventoryHandler` | [`CellInventory`](../../src/main/java/appeng/me/storage/CellInventory.java), item/fluid/void/creative handlers | Drive/cell and I/O-port game tests; NBT keys are persistence surface. |
| Cell provider/drive | `ICellProvider`, `IMEInventoryHandler` | `TileDrive`, drive inventory, `GridStorageCache.cellUpdate` | Drive/cell and I/O-port game tests. |
| External storage | external-storage registry, `IStorageBus` | `PartStorageBus`, integration adapters, `NetworkInventoryHandler` aggregation | [`ImportExportBusTests`](../../src/main/java/appeng/gametests/automation/importexport/ImportExportBusTests.java), [`StorageBusTests`](../../src/main/java/appeng/gametests/automation/storagebus/StorageBusTests.java). |
| Change propagation | `IMEMonitor`, `IMEMonitorHandlerReceiver`, `MENetworkStorageEvent` | `NetworkMonitor.postChange`/`postChangesToListeners`, cache monitor locking/diffs, container `postChange` | GUI/list update path; gap: watcher removal and listener failure/reentrancy. |

## Crafting

| Concept | Canonical API | Implementation/entry | Tests/evidence |
|---|---|---|---|
| Pattern/provider index | `ICraftingPatternDetails`, `ICraftingProvider`, [`ICraftingGrid`](../../src/main/java/appeng/api/networking/crafting/ICraftingGrid.java) | [`CraftingGridCache`](../../src/main/java/appeng/me/cache/CraftingGridCache.java), provider add/remove/rebuild | [`InterfaceTests`](../../src/main/java/appeng/gametests/interfaces/InterfaceTests.java); crafting functional tests. |
| Job API | [`ICraftingJob`](../../src/main/java/appeng/api/networking/crafting/ICraftingJob.java) | [`CraftingJobV2`](../../src/main/java/appeng/crafting/v2/CraftingJobV2.java), [`CraftingJobFast`](../../src/main/java/appeng/crafting/fast/CraftingJobFast.java) | [`CraftingV2Tests`](../../src/functionalTest/java/appeng/test/CraftingV2Tests.java). |
| Planner selection | calculation mode/config and `ICraftingGrid.beginCraftingJob` | `CraftingGridCache.beginCraftingJob`, [`CraftingCalculations`](../../src/main/java/appeng/crafting/v2/CraftingCalculations.java), fast planner entry | Functional cases cover v2 heavily; fast/selection equivalence needs explicit coverage. |
| CPU discovery/selection | crafting CPU API/status | `CraftingGridCache` CPU sets, cluster formation, [`CraftingCPUCluster`](../../src/main/java/appeng/me/cluster/implementations/CraftingCPUCluster.java) | [`CraftingExecutionTests`](../../src/main/java/appeng/gametests/crafting/CraftingExecutionTests.java) and structure. |
| Submission/link | [`ICraftingLink`](../../src/main/java/appeng/api/networking/crafting/ICraftingLink.java) | [`CraftingLink`](../../src/main/java/appeng/crafting/CraftingLink.java), [`CraftingLinkNexus`](../../src/main/java/appeng/crafting/CraftingLinkNexus.java), cache `submitJob` | Functional planner + game execution; gap: failure/cancel/reload matrices. |
| Execution | crafting requester/provider/medium APIs | `CraftingCPUCluster.submitJob`, `executeCrafting`, `injectItems`, completion/cancel/NBT paths | Crafting execution game tests; CPU class is large and persistence-sensitive. |
| Client crafting flow | terminal/crafting GUI actions | `PacketMonitorableAction.AUTO_CRAFT`, craft amount/confirm/status containers and packets | Gap: full client click → plan → submit → failure/cancel sync automated test. |

## Integrations, recipes, world, and services

| Concept | Activation/config surface | Implementation/entry | Tests/resources/gaps |
|---|---|---|---|
| Integration catalog | [`IntegrationType`](../../src/main/java/appeng/integration/IntegrationType.java), `ModIntegration` config | [`IntegrationRegistry`](../../src/main/java/appeng/integration/IntegrationRegistry.java), [`IntegrationNode.call`](../../src/main/java/appeng/integration/IntegrationNode.java#L47) | Compile-only dependencies; gap: full absent/present/version matrix. |
| Representative NEI | `IntegrationType.NEI`, client side | [`NEI` module](../../src/main/java/appeng/integration/modules/NEI.java), constructor probes and `init` registrations | Manual client with matching NEI; reflection branches under-characterized. |
| Recipe root/overrides | `recipes` / `GTNHRecipes` resources, custom config | [`RecipeLoader.run`](../../src/main/java/appeng/core/RecipeLoader.java#L37), [`RecipeHandler`](../../src/main/java/appeng/recipes/RecipeHandler.java), `JarLoader`, `ConfigLoader` | [`index.recipe`](../../src/main/resources/assets/appliedenergistics2/recipes/index.recipe), alternate [`GTNH index`](../../src/main/resources/assets/appliedenergistics2/GTNHRecipes/index.recipe). |
| Quartz generation | worldgen registry and `AEFeature` config | [`QuartzWorldGen.generate`](../../src/main/java/appeng/worldgen/QuartzWorldGen.java) | Ore definitions/config; gap: dimension/distribution regression fixture. |
| Meteor generation | worldgen dimension rules | [`MeteoriteWorldGen.generate`](../../src/main/java/appeng/worldgen/MeteoriteWorldGen.java), queued spawn callables | `MeteoriteLootTable.json` runtime file, `SpawnData`, compass region files; unit test covers only meteor data-name encoding. |
| World-data lifecycle | [`IWorldData`](../../src/main/java/appeng/core/worlddata/IWorldData.java) | [`WorldData`](../../src/main/java/appeng/core/worlddata/WorldData.java), `AppEng.serverAboutToStart/stopping/stopped` | Gap: repeated integrated-server lifecycle test. |
| Player security IDs | [`IWorldPlayerData`](../../src/main/java/appeng/core/worlddata/IWorldPlayerData.java) | [`PlayerData`](../../src/main/java/appeng/core/worlddata/PlayerData.java) | Save config; gap: UUID migration/corruption fixture. |
| Spatial dimensions | [`IWorldDimensionData`](../../src/main/java/appeng/core/worlddata/IWorldDimensionData.java) | [`DimensionData`](../../src/main/java/appeng/core/worlddata/DimensionData.java), login synchronization | Spatial game/manual tests; gap: restart/collision matrix. |
| Grid-storage registry | [`IWorldGridStorageData`](../../src/main/java/appeng/core/worlddata/IWorldGridStorageData.java) | [`StorageData`](../../src/main/java/appeng/core/worlddata/StorageData.java), `GridStorage.getValue` | `<save>/AE2/settings.cfg`; persistence gaps above. |
| Meteor/spawn index | [`IWorldSpawnData`](../../src/main/java/appeng/core/worlddata/IWorldSpawnData.java) | [`SpawnData`](../../src/main/java/appeng/core/worlddata/SpawnData.java) | `<save>/AE2/spawndata`; world-save flush. |
| Compass service | [`IWorldCompassData`](../../src/main/java/appeng/core/worlddata/IWorldCompassData.java) | [`CompassData.onWorldStop`](../../src/main/java/appeng/core/worlddata/CompassData.java), [`CompassService.kill`](../../src/main/java/appeng/services/CompassService.java#L124) | `<save>/AE2/compass`; executor stopped during world shutdown; gap: shutdown/race/corruption tests. |
| Commands | Minecraft command API | [`AECommand`](../../src/main/java/appeng/server/AECommand.java), registered at `AppEng.serverStarting` | Permission levels in subcommands; manual/automated command tests not found. |
| Global tick/work queues | FML/Forge tick/world events | [`TickHandler`](../../src/main/java/appeng/hooks/TickHandler.java), `addInit`, `addCallable`, tick/unload/save/shutdown | Network/power/crafting/worldgen paths exercise portions; gap: time-bound/error/lifecycle suite. |

## Tests and CI lookup

| Layer | Entry | Coverage landmarks | Limitation |
|---|---|---|---|
| JUnit 4 | [`src/test/java`](../../src/test/java), `./gradlew test` | version, formatting, UUID/iteration, meteor name encoding | Does not boot Forge/Minecraft. |
| In-game JUnit 5 | [`AppengTestMod`](../../src/functionalTest/java/appeng/test/AppengTestMod.java), `runFunctionalTestServer` | `CraftingV2Tests`, narrow network inventory test, mock ME objects | Mock topology is not a real graph; not normal `test`. |
| HorizonQA | [`appeng.gametests`](../../src/main/java/appeng/gametests) | 58 tests: I/O port, P2P, drive/cell, buses, interfaces, network/power, crafting, AE2FC | Tests live in main source; local runner details are convention/framework-dependent. |
| Structures | [`horizonqastructures`](../../src/main/resources/assets/appliedenergistics2/horizonqastructures) | nine JSON/NBT template pairs | Resource name/template agreement is part of test reachability. |
| Build/test CI | [`build-and-test.yml`](../../.github/workflows/build-and-test.yml#L12) | Enables HorizonQA and external-dependency inclusion | Reusable implementation is external and referenced at `master`. |
| Daily full-pack integration | [`latest-daily-integration-test.yml`](../../.github/workflows/latest-daily-integration-test.yml) | Runs after build workflow | Exact external commands/version matrix not in-tree. |

## Required flow cross-checks

| Required trace | Guide | Sender/entry and receiving/terminal evidence |
|---|---|---|
| Game startup/registration | [Startup](02-startup-and-runtime.md#complete-startup-sequence) | Manifest/coremod → `AppEng` phases → `Registration` handlers/registries → proxy/integrations/GUI/channel. |
| Block/part to grid | [ME network](03-me-network.md#placement-or-load-to-grid-membership) | placement/NBT/validate → tick-ready → proxy/node → neighbor connections → `Grid.add`/cache callbacks. |
| Storage insert/extract | [Storage](04-storage-and-crafting.md#end-to-end-storage-insertion-and-extraction) | requester/action source → `NetworkInventoryHandler` permission/priority handlers → cell/external handler → monitor delta. |
| Crafting request | [Crafting](04-storage-and-crafting.md#end-to-end-crafting-request) | client/API request → planner/job → CPU selection/submission → execution/link/completion or failure. |
| GUI action | [UI](05-game-objects-and-ui.md#fully-traced-interaction-terminal-left-click-withdraw-or-deposit) | `GuiMEMonitorable`/target chunks/action packet → open server container/live monitor/security/power → update packets/client repo. |
| Topology/channel/power change | [ME network](03-me-network.md#concrete-topologychannel-propagation-trace) | edge destroy/split → cache node callbacks → path finalization/power/activity events → service rebuild. |
| World-data save/load | [World/services](06-integrations-world-and-services.md#concrete-persistence-trace-grid-storage) | server start/`StorageData` load → `GridStorage`/cache reconstruction → stop/save/compression. |
| Optional integration | [Integrations](06-integrations-world-and-services.md#representative-trace-nei) | transformer-seeded registry → side/mod/config detection → reflection → NEI registration. |

## Related guides

- [Overview](README.md)
- [Project map](01-project-map.md)
- [Development guide](07-development-guide.md)
- [Refactor map](08-refactor-map.md)
- [Glossary](09-glossary.md)
