# Removing an active Storage Reshuffle loses items or leaves storage locked

- Report ID: BUG-005
- Status: Accepted
- Confidence: Very high
- Confirmation: Statically proven but not runtime-confirmed
- Player impact: Items already removed by an active reshuffle disappear permanently if the block is broken before reinsertion. Breaking or chunk-unloading the active machine can also leave the grid's shared storage monitors locked indefinitely after the task is gone.
- Affected mode: Both
- Affected version: `rv3-beta-1032-GTNH-1-g3654a842b`
- Minecraft version: 1.7.10
- Mod loader: Minecraft Forge 10.13.4.1614
- Branch: `master`
- Commit: `3654a842bf9b40e6e08ead7bb3a46e3d7b169eaf`
- Relevant subsystem: Storage Reshuffle asynchronous extraction and block-break lifecycle

## Preconditions

- A powered ME network with a Storage Reshuffle and item storage containing ordinary items.
- Enough distinct storage handlers to keep the snapshot/extraction phases active long enough to break the block comfortably. For example, ten ME Drives populated with cells provide up to 100 handlers and several seconds of extraction work.
- BUILD permission if the network uses a Security Terminal.

Only normal blocks, cells, the Storage Reshuffle GUI, and ordinary block breaking are used. No modified client, packet manipulation, edited NBT, commands, third-party inventory, or tight single-tick timing is required.

## Exact reproduction steps

1. Build a powered ME network with a Storage Reshuffle and ten ME Drives.
2. Populate many Drive slots with ordinary item cells and place recognizable items in several of the cells. Using many cells makes the before-snapshot and extraction phases last for many server ticks.
3. Open the Storage Reshuffle GUI, leave the item type enabled, and press Start.
4. Watch the operation report until it enters extraction and shows a nonzero extracted-item count. With many handlers, the operation remains in this phase while it processes later cells.
5. Close the GUI and immediately break the Storage Reshuffle block with an ordinary pickaxe while the operation is still running.
6. Reconnect/reopen the network and search for the items that the report had already counted as extracted.

An equivalent deterministic test can pause immediately after one `ReshuffleTask` extraction batch and then invoke the block's normal break path.

For the lock-only chunk variant, keep the Drive/terminal portion of the grid loaded with a second player, put the Storage Reshuffle in a different chunk, start the same long operation, and have the first player leave so only the machine's chunk unloads. Terminal insertion/extraction for the selected type remains blocked even after the machine chunk reloads, because the old shared monitor was never unlocked.

## Expected result

Breaking or unloading the machine should cancel/pause the operation, return or persist every buffered stack, and release every monitor lock. If a broken machine's network cannot accept all buffered items, the remainder should be dropped as ME Stack items or ordinary item stacks. Removing the coordinator must never destroy items it owns or leave shared grid state locked.

## Actual predicted result

Every stack extracted before a break exists only in the active `ReshuffleTask`'s `extracted` list or `injectQueue`. The block-break path neither calls `cancelReshuffle` nor exposes those lists as drops. `TileStorageReshuffle` is not an `IInventory` and does not override `getDrops`, so `AEBaseTile.getDrops` contributes nothing. Once the tile and task object are removed, the buffered stacks have no remaining physical or persistent owner and are permanently lost.

For both break and chunk unload, the tile also discards `lockedMonitors` without calling `unlockStorage`. Those `NetworkMonitor` objects belong to the still-loaded grid cache, not to the tile. Proxy invalidation destroys only the tile's grid node. If other grid chunks remain loaded, their monitor's `locked` flag survives; a reloaded `TileStorageReshuffle` creates a new tile with `lockedMonitors == null` and cannot release the old ownership automatically.

## Player-visible symptom

After a break, some item types or quantities are missing from all cells even though the reshuffle extracted them successfully. The loss can range from one cell's contents to a large portion of the network. If the machine is unloaded or removed while the rest of the grid stays loaded, ordinary terminal insertion/extraction for the enabled types may instead remain disabled indefinitely, including after the machine reloads.

## Reachability proof

The Storage Reshuffle GUI's normal Start button sends `PacketValueConfig` with `Reshuffle.Start`; the server dispatches it to the open `ContainerStorageReshuffle`, which calls `TileStorageReshuffle.startReshuffle`. The tile creates a `ReshuffleTask`, locks the selected network monitors against unrelated storage activity, and ticks the task normally.

During `ReshuffleTask.handlerProcessor(..., true)`, each concrete storage handler is enumerated. Its available stacks are copied, then removed with `Actionable.MODULATE`. Fully extracted stacks are added to the task-local `extracted` list. After the extraction phase, `toQueueList` transfers those same stack objects to the task-local `injectQueue`; they are not placed in a tile inventory or dropped into the world.

The task has a recovery method: `cancel` calls `returnPendingItems` for both task buffers. `TileStorageReshuffle.cancelReshuffle` invokes it when the GUI Cancel button is used or when the tile receives a network power-loss event. This establishes that the buffered values are real item ownership which must be reconciled.

Ordinary block breaking takes a different path. `AEBaseTileBlock.breakBlock` asks the tile for additional drops and then removes the tile. `TileStorageReshuffle` has no `getDrops` override and implements no `IInventory`, so the inherited `AEBaseTile.getDrops` loop has no inventory to enumerate. Neither the block class nor tile invalidation calls `cancelReshuffle`. The task is simply discarded with its nonempty buffers.

Chunk unload likewise has no task teardown. `AENetworkTile.onChunkUnload` delegates to `AENetworkProxy.onChunkUnload`, which invalidates/destroys the node. It does not call a tile hook that cancels the task or unlocks its monitors. `NetworkMonitor.locked` is a field on the grid cache's monitor. As long as another node keeps the original grid alive, removing this one node does not reconstruct or clear the monitor.

World NBT persistence does not help. While the tile remains placed, `writeToNBT_TileStorageReshuffle` serializes a running task's `extracted` and `injectQueue` lists so chunk/world reload can recover them into `cantInject`. A harvested block does not carry that tile NBT, and the custom break drops omit the lists.

## Root cause

The asynchronous task acquires two external resources: ownership of extracted item buffers and locks on shared grid monitors. Both are released only by the normal completion/explicit-cancel paths. Permanent removal and chunk-unload lifecycle paths invalidate the grid node without invoking the task teardown, so they can destroy item ownership and/or leak shared locks.

## Execution path

1. `GuiStorageReshuffle` sends the ordinary `Reshuffle.Start` value action.
2. `PacketValueConfig.serverPacketData` calls `ContainerStorageReshuffle.startReshuffle`.
3. `TileStorageReshuffle.startReshuffle` locks selected monitors and constructs/initializes `ReshuffleTask`.
4. `TileStorageReshuffle.Tick_TileStorageReshuffle` repeatedly calls `ReshuffleTask.processNextBatch`.
5. In the EXTRACTION phase, `handlerProcessor` calls each cell handler's `extractItems(..., MODULATE, ReshuffleActionSource)` and adds successful results to `extracted`.
6. Player breaks the Storage Reshuffle while `extracted` or `injectQueue` is nonempty.
7. `AEBaseTileBlock.breakBlock` calls inherited `AEBaseTile.getDrops`; the noninventory tile contributes no buffered stacks.
8. The tile is removed without calling `ReshuffleTask.cancel`; its Java-only task buffers become unreachable and the extracted items vanish.

For chunk unload, steps 1-5 are the same; `AENetworkTile.onChunkUnload` then invalidates the proxy/node while the placed tile's NBT preserves buffers. The old grid cache remains alive elsewhere, however, and its `NetworkMonitor.locked` flag is never cleared. Reload creates a new tile/task state but does not restore the old `lockedMonitors` map needed to call `setLocked(false)`.

## Code evidence

- `src/main/java/appeng/client/gui/implementations/GuiStorageReshuffle.java`, Start-button handling: the visible GUI initiates the supported action.
- `src/main/java/appeng/core/sync/packets/PacketValueConfig.java`, `Reshuffle.Start` dispatch near lines 101-108: the server calls the open container's normal start method.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, `startReshuffle`, lines 180-213: the active tile locks monitors and constructs the task with a `ReshuffleActionSource`.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, tick handler, lines 124-150: a running task is advanced batch by batch and remains owned only by the tile's `activeTask` field.
- `src/main/java/appeng/helpers/ReshuffleTask.java`, `handlerProcessor`, lines 145-183: each handler's stacks are physically extracted with `MODULATE` and successful results are stored in the task-local list.
- `src/main/java/appeng/helpers/ReshuffleTask.java`, `toQueueList`, lines 191-200: extracted ownership moves from one task-local collection to another before reinsertion.
- `src/main/java/appeng/helpers/ReshuffleTask.java`, `cancel` and `returnPendingItems`, lines 263-267 and 290-303: explicit cancellation recognizes both buffers and attempts to return them.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, `cancelReshuffle`, lines 215-223: GUI/power cancellation uses the recovery method, but block breaking does not.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, NBT write/read, lines 106-120: running buffers are serialized for a placed tile's reload, confirming that they are durable item state while the block exists.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, `unlockStorage`, lines 265-275: the shared monitor locks can be cleared only while this tile still retains its `lockedMonitors` map.
- `src/main/java/appeng/me/cache/NetworkMonitor.java`, `setLocked`, `extractItems`, and `injectItems`, lines 75-111 and 184 onward: the persistent grid-cache monitor rejects non-reshuffle operations while its flag remains true.
- `src/main/java/appeng/tile/grid/AENetworkTile.java`, `onChunkUnload` and `invalidate`, lines 52-70: unload delegates only to proxy/node invalidation and has no task cleanup callback.
- `src/main/java/appeng/me/helpers/AENetworkProxy.java`, `onChunkUnload` and `invalidate`, lines 87-101: node destruction does not release tile-owned resources outside the grid node/caches.
- `src/main/java/appeng/block/AEBaseTileBlock.java`, `breakBlock`, lines 134-151: the block asks only `getDrops` for tile-owned contents before removing the tile.
- `src/main/java/appeng/tile/AEBaseTile.java`, `getDrops`, lines 392-403: the default implementation drops only `IInventory` slots.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, class declaration and full body: the tile is not an `IInventory` and has no `getDrops`, invalidation, or destruction override that refunds the active task.

## Why existing validation does not prevent it

The task's monitor lock prevents terminals and normal automation from racing the reshuffle, but it does not protect the coordinator block from an authorized player's pickaxe. Forge's normal block-break flow is accepted and the tile is available long enough for AE2's custom drop hook, but that hook has no knowledge of task-local stacks.

Power-loss cancellation is not a substitute for removal handling. The custom `breakBlock` method reads drops before removing the tile; there is no guaranteed power event that invokes `cancelReshuffle` and completes all refund operations before that collection. Chunk unload calls proxy invalidation directly. Later grid repathing cannot reconstruct task-local buffers or the old tile's `lockedMonitors` ownership map.

## Minimal fix direction

Before a Storage Reshuffle tile is removed or unloaded, atomically stop/pause the active task, release all monitor locks, and reconcile both `extracted` and `injectQueue`. Add any network-rejected remainder to an explicit tile-owned pending list. For chunk unload, persist it; for block removal, include it in `getDrops` as ME Stack packets (the pattern used by `TileSuperMEReplenisher`) or equivalent safe stacks.

The break hook must not rely solely on network reinsertion because breaking the block itself may split or depower the grid. It should collect leftovers returned by the refund and drop them. Reuse the same drain method for block breaking, invalidation, and any other permanent removal lifecycle.

## Regression-test proposal

- Test setup: A powered test grid with a Storage Reshuffle and multiple item-cell handlers containing known quantities of at least two items.
- Initial state: Start a reshuffle and tick until one handler's contents have been modulated out while additional handlers remain, then assert the active task has a nonempty pending buffer.
- Triggering action: Break the Storage Reshuffle through the normal block path; in a second case, unload only its chunk while another grid chunk remains active.
- Expected assertion: For break, network storage plus spawned drops exactly equals the pre-reshuffle sum. For unload, buffers survive and every selected `NetworkMonitor` is unlocked or safely reacquired by a resumable task.
- Incorrect behavior the test must prevent: Extracted items ceasing to exist or terminal storage remaining locked after the coordinator task is removed.
- Suggested style: HorizonQA tests for break and two-player/chunk unload, plus focused lifecycle tests that pause after extraction and invoke the relevant tile teardown methods.

## Runtime confirmation

Not runtime-confirmed. Static proof covers the normal GUI start, modulated extraction, task ownership, shared monitor locks, explicit-cancel recovery, placed-tile NBT persistence, block-break drops, chunk/proxy invalidation, and absence of teardown on either removal path.

## Remaining uncertainty

The exact number of cells required for a comfortable manual break window depends on server tick rate and handler count. It does not affect the invariant: any break after at least one successful extraction and before all pending items are reinserted deletes the task-owned remainder.
