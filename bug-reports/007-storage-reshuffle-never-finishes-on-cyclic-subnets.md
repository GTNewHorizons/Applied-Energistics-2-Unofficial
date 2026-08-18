# Storage Reshuffle never finishes on cyclic subnets

- Report ID: BUG-007
- Status: Accepted
- Confidence: Very high
- Confirmation: Statically proven but not runtime-confirmed
- Player impact: Starting a reshuffle on two normally cross-connected ME subnetworks leaves the operation permanently in its snapshot phase and keeps selected storage monitors locked until a player manually cancels it.
- Affected mode: Both
- Affected version: `rv3-beta-1032-GTNH-1-g3654a842b`
- Minecraft version: 1.7.10
- Mod loader: Minecraft Forge 10.13.4.1614
- Branch: `master`
- Commit: `3654a842bf9b40e6e08ead7bb3a46e3d7b169eaf`
- Relevant subsystem: Storage Reshuffle recursive subnet traversal

## Preconditions

- Two separate powered and channelled ME grids, A and B.
- An unconfigured ME Interface on Grid B exposed to a Storage Bus on Grid A.
- An unconfigured ME Interface on Grid A exposed to a Storage Bus on Grid B, forming the supported storage-access cycle A -> B -> A without electrically joining the grids.
- A Storage Reshuffle on Grid A with Include Subnets set to Yes, which is its default.

The topology uses the exact Storage Bus-on-Interface subnet feature recognized by AE2. No third-party storage, packet manipulation, corrupt state, or excessive network size is required.

## Exact reproduction steps

1. Build powered Grid A with a Storage Reshuffle and a blank ME Interface. Do not put configured items or patterns in the Interface, so it exposes Grid A storage.
2. Build a physically separate powered Grid B with another blank ME Interface.
3. On Grid A, place a channelled Storage Bus facing Grid B's Interface.
4. On Grid B, place a channelled Storage Bus facing Grid A's Interface.
5. Confirm that both grids remain distinct and each terminal can see storage across the opposite Interface through its Storage Bus.
6. Open Grid A's Storage Reshuffle. Leave Include Subnets set to Yes and the item type enabled.
7. Press Start and wait indefinitely.
8. Observe that the report never advances beyond the before-snapshot phase. While it is running, try an ordinary item insert or extraction through a terminal on Grid A; the selected monitor remains locked. Press Cancel to restore normal access.

Adding a storage cell to either grid makes the repeated traversal additionally visible in growing snapshot totals, but no stored items are required for the nontermination itself.

## Expected result

The reshuffle should visit each reachable network at most once, snapshot the finite set of handlers, perform extraction/reinsertion, and complete. A cyclic graph should behave like any other finite subnet graph rather than locking storage forever.

## Actual predicted result

`ReshuffleTask` descends from each handler whose `getExternalNetworkInventory` is another `NetworkInventoryHandler`, but it records neither visited handlers nor visited networks. On A's Storage Bus it creates a child iterator for B. On B's Storage Bus it creates a child iterator for A, then another for B, and so on. Every server tick appends another `deepDig` layer and returns without completing the before snapshot. The task remains running and its monitor locks remain enabled until explicit cancellation.

## Player-visible symptom

The Storage Reshuffle progress/report stays in the initial snapshot phase forever. Ordinary terminal storage actions for the selected stack types stop working because the machine locked their `NetworkMonitor`s when the task began. The player must discover the cause and use Cancel or break/depowers the setup; it never self-recovers while the cycle remains active.

## Reachability proof

An unconfigured Interface's `DualityInterface.getItemInventory` returns the grid-backed monitor from `monitorMap`; only an Interface with a configured local stock switches to its bounded `InterfaceInventory`. The external-storage registry therefore gives a facing Storage Bus an inventory chain backed by the other grid's `NetworkMonitor` and `NetworkInventoryHandler`.

`PartStorageBus.getInternalHandler` wraps that inventory in `StorageBusInventoryHandler`. `MEInventoryHandler.getExternalNetworkInventory` unwraps an internal `IMENetworkInventory`, and the API explicitly documents this method as representing an inventory connecting two ME networks, specifically a Storage Bus on an Interface. Thus the reshuffle sees the opposite grid rather than a finite ordinary inventory.

At task initialization, `TileStorageReshuffle.startReshuffle` sets every selected root `NetworkMonitor` to locked, constructs `ReshuffleTask`, and defaults `includeSubnets` from the GUI setting. The task's first phase is BEFORE_SNAPSHOT.

`ReshuffleTask.handlerProcessor` starts with the root network's sorted handler iterator. When it encounters A's cross-network Storage Bus, it obtains B's `NetworkInventoryHandler`, installs a `deepDig` holding B's handler iterator, and returns `false`. On the next tick it selects that deeper iterator. When B's reverse Storage Bus is reached, it installs an A child and returns. There is no call to the iteration IDs used by normal `NetworkInventoryHandler` enumeration and no identity/UUID set analogous to `Grid.getAllRecursiveGridConnections`.

`deepDig.hasNextLayer` removes a child only after its iterator and all its descendants are exhausted. In the cycle, the current deepest A or B iterator always discovers and installs the opposite network before it can become an exhausted leaf. The chain therefore cannot unwind. Even if each network has other cell handlers, they are processed repeatedly on each new visit before the cycle continues; they do not terminate it.

## Root cause

The custom recursive traversal models subnet nesting as a tree, but supported ME storage connectivity is a graph and may contain cycles. It lacks a visited set keyed by `NetworkInventoryHandler` identity or grid UUID, so a two-node cycle produces unbounded traversal depth across ticks.

## Execution path

1. Player presses Start in the normal Storage Reshuffle GUI.
2. `PacketValueConfig` dispatches `Reshuffle.Start` to `ContainerStorageReshuffle.startReshuffle`.
3. `TileStorageReshuffle.startReshuffle` locks the item monitor and creates a task with `includeSubnets == true`.
4. `ReshuffleTask.initialize` prepares Grid A's root handler iterator and enters BEFORE_SNAPSHOT.
5. `handlerProcessor` reaches A's Storage Bus -> B Interface handler; `getExternalNetworkInventory` returns Grid B's `NetworkInventoryHandler`.
6. The task installs B's handler iterator as a `deepDig` child and yields for the tick.
7. A later batch reaches B's Storage Bus -> A Interface handler and installs Grid A's handler iterator as another child.
8. Steps 5-7 repeat A -> B -> A without a visited check. `snapshotBefore` never returns true, so EXTRACTION is never reached.
9. Because `activeTask.isRunning()` remains true, the tile never executes its normal completion branch and never calls `unlockStorage`.

## Code evidence

- `src/main/java/appeng/api/storage/IMEInventoryHandler.java`, `getExternalNetworkInventory`, lines 103-110: the API defines this hook specifically for inventories connecting two ME networks, such as a Storage Bus on an Interface.
- `src/main/java/appeng/helpers/DualityInterface.java`, `getItemInventory`, lines 920-932: an Interface without configured local stock exposes its grid-backed monitor.
- `src/main/java/appeng/parts/misc/PartStorageBus.java`, `getInternalHandler`, lines 624-720: the normal external-storage path obtains that inventory and wraps it in `StorageBusInventoryHandler`.
- `src/main/java/appeng/me/storage/MEInventoryHandler.java`, `getExternalNetworkInventory`, lines 258-265: the wrapper returns the internal network inventory or delegates further through the chain.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, constructor, lines 61-70: Include Subnets defaults to `YesNo.YES`.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, `startReshuffle`, lines 180-210: selected `NetworkMonitor`s are locked before a recursive task is initialized.
- `src/main/java/appeng/helpers/ReshuffleTask.java`, `deepDig`, lines 63-79: traversal state is only a linked chain of iterators, with no visited-network identity.
- `src/main/java/appeng/helpers/ReshuffleTask.java`, `handlerProcessor`, lines 127-188: every external `NetworkInventoryHandler` creates a new deeper iterator and immediately returns, with no cycle check.
- `src/main/java/appeng/helpers/ReshuffleTask.java`, `processNextBatch`, lines 202-260: the task cannot leave BEFORE_SNAPSHOT until `handlerProcessor` exhausts the traversal.
- `src/main/java/appeng/tile/misc/TileStorageReshuffle.java`, tick handler, lines 124-150: storage is unlocked only after the task reports it is no longer running or an exception changes its state; endless traversal does neither.

## Why existing validation does not prevent it

Normal network storage enumeration has recursion and iteration guards because cross-network storage graphs may contain diamonds and cycles. The reshuffle bypasses that enumeration so it can visit concrete handlers individually, but does not reproduce those guards.

The `deepDig` child cleanup handles the end of a finite nested tree only. It cannot recognize that the newly reached Grid A handler is the same root handler already being traversed. No recursion exception is thrown because the traversal advances only one handler/batch at a time rather than recursing on the Java call stack.

The task's broad exception handler is therefore not involved. State remains valid from Java's perspective, `activeTask.isRunning()` stays true, and no timeout or maximum depth exists. The user-visible lock is an intentional part of reshuffling and is released only by Cancel/power loss after the task fails to finish.

## Minimal fix direction

Maintain an identity-based visited set of external `NetworkInventoryHandler` instances (or their owning Grid UUIDs). Seed it with the root handlers and descend only when `visited.add(nextNetwork)` succeeds. Cyclic and diamond paths should be skipped without treating the same physical storage twice.

Also impose a defensive traversal depth/handler budget and fail through the normal cancellation/refund path if a provider violates graph invariants. Any error exit must release every monitor lock.

## Regression-test proposal

- Test setup: Construct two powered test grids. On each, expose a blank Interface to a Storage Bus belonging to the other grid. Put a Storage Reshuffle on Grid A with Include Subnets enabled.
- Initial state: Assert A and B are distinct grids, both cross-network handlers expose the opposite `NetworkInventoryHandler`, and terminal storage works before start.
- Triggering action: Start reshuffling and advance substantially more ticks than the finite handler count requires.
- Expected assertion: The task reaches DONE, each concrete network/handler is visited once, monitor locks are released, and terminal injection/extraction works.
- Incorrect behavior the test must prevent: The task remaining in BEFORE_SNAPSHOT while `deepDig` gains alternating A/B layers and the root monitor remains locked.
- Suggested style: A focused graph traversal unit test for self-cycle, two-node cycle, and diamond graphs, plus a HorizonQA game test using the normal Storage Bus/Interface blocks.

## Runtime confirmation

Not runtime-confirmed. Static proof covers the supported cross-network inventory hook, Interface-to-network exposure, Storage Bus wrapper propagation, default Include Subnets setting, exact alternating iterator construction, absence of a visited guard, phase transition requirement, and lock-release lifecycle.

## Remaining uncertainty

The exact ordering of ordinary cell handlers relative to each Storage Bus affects which snapshot rows repeat before each descent. It cannot make the traversal terminate: every visit to A or B eventually reaches the still-active reverse Storage Bus and installs the opposite network again.
