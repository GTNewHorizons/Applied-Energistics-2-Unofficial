# Cross-linked item P2P tunnels crash when a comparator reads them

- Report ID: BUG-002
- Status: Accepted
- Confidence: High
- Confirmation: Statically proven but not runtime-confirmed
- Player impact: A player can create two supported Item P2P networks whose outputs point into each other's inputs. Reading either input host with a vanilla comparator then recurses through the two virtual inventories until the logical server throws `StackOverflowError`, crashing a dedicated server or the integrated server.
- Affected mode: Both
- Affected version: `rv3-beta-1032-GTNH-1-g3654a842b`
- Minecraft version: 1.7.10
- Mod loader: Minecraft Forge 10.13.4.1614
- Branch: `master`
- Commit: `3654a842bf9b40e6e08ead7bb3a46e3d7b169eaf`
- Relevant subsystem: Item P2P tunnel inventory composition, cable-bus inventory layers, and comparator access

## Preconditions

- A powered AE carrier network with enough channels for six active Item P2P Tunnels.
- Six Item P2P Tunnels, one Memory Card, two chests, and one vanilla comparator.
- Six cable-bus hosts arranged so that:
  - Pair A has one input, one output facing Pair B's input, and a second output facing Chest A.
  - Pair B has one input, one output facing Pair A's input, and a second output facing Chest B.
- The carrier cables must connect all six tunnel hosts through faces other than the two face-to-face tunnel connections.

All components and topology are available through ordinary survival gameplay. Item P2P Tunnels can be obtained through the supported P2P attunement flow, and the Memory Card GUI interaction supports one input with multiple outputs on the same frequency. No commands, edited NBT, malformed packets, or third-party inventory behavior are required.

## Exact reproduction steps

1. Build a powered carrier network with at least six available channels.
2. Install three Item P2P Tunnels for Pair A: `A-input`, `A-cross-output`, and `A-chest-output`.
3. Install three more Item P2P Tunnels for Pair B: `B-input`, `B-cross-output`, and `B-chest-output`.
4. Place the tunnel hosts so `A-cross-output` points directly into the face occupied by `B-input`, and `B-cross-output` points directly into the face occupied by `A-input`. A four-host rectangle permits these two opposing face-to-face connections while carrier cable reaches the hosts from their other faces.
5. Place Chest A against `A-chest-output` and Chest B against `B-chest-output`.
6. Sneak-right-click `A-input` with the Memory Card to save it as an input. Right-click both `A-cross-output` and `A-chest-output` with the card to bind them as outputs of Pair A.
7. Sneak-right-click `B-input` with the Memory Card to overwrite the card with Pair B's new input frequency. Right-click both `B-cross-output` and `B-chest-output` to bind them as outputs of Pair B.
8. Let the carrier network finish its normal channel/power update. Both real chest outputs ensure that each P2P input exposes a nonempty virtual inventory in addition to the cross-linked range.
9. On any free side of the cable-bus block hosting `A-input`, place a vanilla comparator so that the comparator reads that cable-bus block.

The same trigger works by reading the block that hosts `B-input`.

## Expected result

AE2 should reject or safely terminate a circular Item P2P inventory path. A comparator should either report zero for the circular portion or calculate a signal from only the reachable concrete inventories. A player-created automation topology must not crash the server.

## Actual predicted result

The comparator asks the dynamically layered cable-bus tile for every inventory slot. When it reaches the cross-linked part of `A-input`, slot access follows `A-input`'s chained destination to the cable-bus layer containing `B-input`. That layer delegates the slot back to `B-input`, whose chained destination reaches the cable-bus layer containing `A-input`, and the same calls repeat without a persistent visited set or depth limit.

The Java call stack grows until `StackOverflowError`. This is an `Error`, not a normal failed insertion or empty-inventory result, so the logical server crashes instead of returning a comparator value.

## Player-visible symptom

Placing or updating the comparator causes an immediate server crash when Minecraft evaluates its rear input. On a dedicated server, connected players are disconnected and the server must be restarted after the circular topology is removed or edited. In singleplayer, the integrated server crashes and the world closes/crash-reports.

## Reachability proof

`PartP2PTunnel.onPartShiftActivate` is the normal Memory Card input-binding action. It converts an unbound/output tunnel to an input, assigns a nonzero frequency, and stores that frequency and tunnel item on the card. `PartP2PTunnelNormal.onPartActivate` loads the same card into another normal tunnel; `convertToOutput` changes it to an output on that frequency. The only validation is compatible tunnel type and a nonzero frequency. It does not inspect the external inventory graph for a cycle.

`P2PCache` intentionally represents outputs with a multimap, so binding both a cross output and a chest output to one input is supported. `PartP2PItems.getDestination` collects every linked output inventory into `WrapperChainedInventory`.

An Item P2P Tunnel implements `ISidedInventory`. AE2 registers `LayerISidedInventory` for cable-bus hosts, and the runtime-combined cable-bus tile therefore exposes every installed Item P2P inventory through Minecraft's `ISidedInventory` interface. The layer records per-slot delegates (`InvSot`) that call the part's own `getStackInSlot`.

For a cross output, `PartP2PItems.getOutputInv` sees the facing cable-bus tile as an `ISidedInventory` and wraps it in `WrapperMCISidedInventory`. The sided cable-bus layer exposes the Item P2P input on the facing side, so the returned inventory really is the other pair's input rather than an unrelated internal object.

The two additional chest outputs make each virtual input inventory nonempty and ensure the cable-bus layer creates real slot delegates. Output order does not affect the trigger: vanilla `Container.calcRedstoneFromInventory`, called by AE2's comparator override, visits every slot, so it eventually enters the cross-linked range even if a chest range comes first.

The apparent recursion protection does not prevent this path. `PartP2PItems.getOutputInv` adds the output tunnel to `which` only while discovering the adjacent tile and removes it before returning the inventory wrapper. The circular call does not occur during discovery. It occurs later, when the comparator dereferences the already-created wrapper's slots, at which point `which` is empty again.

## Root cause

Item P2P cycle detection has the wrong lifetime. The `which` collection protects only the short `getOutputInv` discovery call, but P2P destinations are lazy, composable `IInventory` wrappers. A wrapper can point to another P2P input without accessing it until a later `getStackInSlot`, `setInventorySlotContents`, or size-related operation.

Consequently, AE2 accepts a cyclic virtual-inventory graph and discards its visited marker before traversing that graph. Cable-bus inventory layering then turns the cycle into ordinary recursive inventory delegation with no terminating condition.

## Execution path

1. Player sneak-right-clicks each input with a Memory Card; `PartP2PTunnel.onPartShiftActivate` calls `convertToInput` and `saveInputToMemoryCard`.
2. Player right-clicks each output; `PartP2PTunnelNormal.onPartActivate` calls `applyMemoryCard`, then `PartP2PTunnel.convertToOutput`.
3. `P2PCache.updateFreq` registers multiple outputs under each input frequency.
4. `PartP2PItems.getDestination` obtains the cross output and chest output inventories and constructs a `WrapperChainedInventory`.
5. For a cross output, `PartP2PItems.getOutputInv` wraps the facing runtime cable-bus `ISidedInventory` in `WrapperMCISidedInventory` and removes its temporary `which` marker before returning.
6. `LayerISidedInventory.notifyNeighbors` exposes the facing `PartP2PItems` input using `InvLayerData` / `InvSot` slot delegates.
7. Player places a comparator reading the input's cable-bus block.
8. `AEBaseTileBlock.getComparatorInputOverride` calls vanilla `Container.calcRedstoneFromInventory` on the combined cable-bus tile.
9. Comparator iteration calls cable-bus layer `getStackInSlot` -> Pair A input `getStackInSlot` -> chained cross inventory -> Pair B cable-bus layer -> Pair B input `getStackInSlot` -> chained cross inventory -> Pair A cable-bus layer.
10. Step 9 repeats until `StackOverflowError` terminates the server thread.

## Code evidence

- `src/main/java/appeng/parts/p2p/PartP2PTunnel.java`, `onPartShiftActivate`, `convertToInput`, and `convertToOutput`, lines 260-309: normal Memory Card actions create a nonzero-frequency input and compatible outputs; output conversion has no external-inventory cycle check.
- `src/main/java/appeng/parts/p2p/PartP2PTunnelNormal.java`, `onPartActivate`, lines 27-44: an ordinary nonsneaking Memory Card click calls `applyMemoryCard` on a normal P2P tunnel.
- `src/main/java/appeng/parts/p2p/PartP2PTunnel.java`, `applyMemoryCard`, lines 435-446: card data is passed directly to `convertToOutput` after type/frequency decoding.
- `src/main/java/appeng/me/cache/P2PCache.java`, fields, `updateFreq`, and `getOutputs`, lines 35-38 and 145-181: one input is stored per frequency while outputs are a multimap, and all compatible outputs are returned to the input.
- `src/main/java/appeng/parts/p2p/PartP2PItems.java`, `getDestination`, lines 74-102: every linked output inventory is composed into one lazy `WrapperChainedInventory`.
- `src/main/java/appeng/parts/p2p/PartP2PItems.java`, `getOutputInv`, lines 104-149: facing `ISidedInventory` tiles, including cable-bus layers, are returned as `WrapperMCISidedInventory`; `which.pop()` removes the only recursion marker before the wrapper is used.
- `src/main/java/appeng/parts/p2p/PartP2PItems.java`, inventory methods, lines 227-305: size, read, and write calls delegate directly to the chained destination. Extraction is disabled for sided automation, but direct inventory reads remain available to comparators.
- `src/main/java/appeng/core/Registration.java`, layer registration, lines 525-529: cable-bus runtime classes receive both `ISidedInventory` and `IFluidHandler` layers.
- `src/main/java/appeng/parts/layers/LayerISidedInventory.java`, `notifyNeighbors`, lines 47-101: every installed `ISidedInventory` part is included in a unified slot table.
- `src/main/java/appeng/parts/layers/LayerISidedInventory.java`, inventory delegation, lines 104-143: cable-bus size/read/write calls delegate into `InvLayerData`.
- `src/main/java/appeng/parts/layers/InvLayerData.java`, `getStackInSlot` and `setInventorySlotContents`, lines 53-81: unified slots delegate to their saved `InvSot` targets.
- `src/main/java/appeng/parts/layers/InvSot.java`, inventory delegation, lines 26-48: slot reads and writes call the underlying P2P part with no traversal guard.
- `src/main/java/appeng/util/inv/WrapperChainedInventory.java`, `calculateSizes`, `getStackInSlot`, and `setInventorySlotContents`, lines 39-56 and 95-133: the composed inventory forwards each global slot to the selected output wrapper.
- `src/main/java/appeng/util/inv/WrapperMCISidedInventory.java`, constructor, lines 17-25: the cross output receives a sided view of the facing cable-bus inventory.
- `src/main/java/appeng/block/networking/BlockCableBus.java`, `setupTile`, lines 438-440: the block is switched to the runtime-combined cable-bus class containing registered layers.
- `src/main/java/appeng/block/AEBaseTileBlock.java`, `setTileEntity` and `getComparatorInputOverride`, lines 83-86 and 183-189: a runtime tile implementing `IInventory` marks its block comparator-readable, and comparator reads are forwarded to `Container.calcRedstoneFromInventory`.

## Why existing validation does not prevent it

Memory Card validation checks that the stored part is a compatible normal P2P type and that the frequency is nonzero. `P2PCache` is designed to accept multiple outputs. Neither layer checks the inventory behind an output before exposing it.

`PartP2PItems.canExtractItem` returning false only blocks sided extraction; it does not block `getStackInSlot`, which vanilla comparators require. The `which` list does not span wrapper use and therefore is empty at every recursive slot hop. No packet manipulation or concurrent timing is involved.

## Minimal fix direction

Carry cycle detection through actual P2P inventory operations, not only adjacent-tile discovery. A minimal correction could use a thread-local visited set/depth guard around all delegated `PartP2PItems` inventory methods and return an empty/no-op result when the same tunnel is re-entered. The guard must cover size, read, write, insertion validation, and mutation consistently.

Alternatively, exclude a cable-bus inventory range when resolving it would lead back to any input already in the current P2P destination graph. Preserve legitimate fan-out to multiple concrete inventories and preserve noncyclic P2P-to-P2P chains.

## Regression-test proposal

- Test setup: Extend `src/main/java/appeng/gametests/network/p2p/P2PTests.java` with two powered Item P2P frequencies. Each frequency has one concrete chest output and one output facing the other frequency's input.
- Initial state: Wait until all six tunnels are active and both pairs report their intended frequency/output membership.
- Triggering action: Invoke the cable-bus inventory's `getSizeInventory` and every `getStackInSlot`, mirroring `Container.calcRedstoneFromInventory`; an end-to-end variant should place a comparator facing the input host.
- Expected assertion: The operation completes within the test tick, returns a bounded inventory/comparator result, and the test server remains alive. Concrete chest contents remain unchanged.
- Incorrect behavior the test must prevent: Recursive Pair A -> Pair B -> Pair A slot delegation ending in `StackOverflowError`, or unbounded virtual slot-count growth during topology updates.
- Suggested style: Follow the existing frequency/link helpers and continuous invariants in `P2PTests`, adding a small cycle template or constructing the facing tunnels in the test.

## Runtime confirmation

Not runtime-confirmed. Static proof covers Memory Card binding, multi-output registration, face-specific cable-bus inventory exposure, lazy wrapper construction, comparator dispatch, and the unguarded recursive slot path. Runtime testing still needs to build the two-frequency topology and place/read the comparator in HorizonQA or a client/server world.

## Remaining uncertainty

The exact first tick on which the comparator evaluation occurs can vary with Minecraft neighbor-update ordering. The failure itself is deterministic once `Container.calcRedstoneFromInventory` walks the active cross-linked slot ranges.
