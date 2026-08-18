# Export Bus crafting crashes if its target inventory is removed

- Report ID: BUG-004
- Status: Accepted
- Confidence: Very high
- Confirmation: Statically proven but not runtime-confirmed
- Player impact: Breaking the inventory in front of an Export Bus while its Crafting Card request is in progress causes the server to crash when the crafted result returns to the ME network.
- Affected mode: Both
- Affected version: `rv3-beta-1032-GTNH-1-g3654a842b`
- Minecraft version: 1.7.10
- Mod loader: Minecraft Forge 10.13.4.1614
- Branch: `master`
- Commit: `3654a842bf9b40e6e08ead7bb3a46e3d7b169eaf`
- Relevant subsystem: Export Bus Crafting Card requester lifecycle and crafting-output delivery

## Preconditions

- A powered ME network with a crafting CPU, an Export Bus, and a Crafting Card installed in the bus.
- A chest or other ordinary item inventory directly in front of the Export Bus.
- The Export Bus filter configured for an item that is craftable but not currently stored in the network.
- A deliberately slow normal crafting route, such as a processing pattern that smelts one iron ore into one iron ingot in a furnace, with an Interface supplying the furnace and an Import Bus returning the result.

No unusual inventory implementation is required. A vanilla chest and furnace are sufficient, and the trigger is an ordinary player breaking the chest after AE2 has started its own supported Crafting Card request.

## Exact reproduction steps

1. Build a powered ME network with storage, a crafting CPU, an Interface containing a processing pattern for one iron ore -> one iron ingot, a vanilla furnace that receives the pattern input, and an Import Bus that returns the furnace output to the network.
2. Attach an Export Bus to a separate vanilla chest.
3. Install a Crafting Card in the Export Bus, configure its export filter to one iron ingot, and leave Craft Only enabled or ensure no iron ingots are currently stored.
4. Wait until the Export Bus submits a crafting request and the furnace starts smelting the ore. The furnace's normal smelting delay makes the in-progress window clearly visible.
5. Before the ingot finishes smelting, break and collect the chest in front of the Export Bus. Leave the Export Bus itself attached to the network.
6. Let the furnace complete and allow the Import Bus to process the resulting iron ingot for insertion into the ME network.

The same failure can be triggered with another delayed crafting setup as long as the target exists when the Export Bus requests the craft and is absent when the final output is delivered.

## Expected result

If the requester can no longer accept the result, it should return the entire crafted stack as unwanted. The crafting CPU should retain it or return it to network storage, and the outstanding job should finish or cancel safely. Removing an automation target must not crash the logical server.

## Actual predicted result

When the Import Bus determines how much of the iron ingot the network can accept, it simulates removing the furnace output and calls the destination monitor with `Actionable.SIMULATE`. The crafting inventory routes that simulated insertion to the CPU waiting for the final output. The CPU calls the requester link, which reaches the Export Bus. Because the adjacent chest is gone, `getTarget()` is not an `InventoryAdaptor`, and `PartBaseExportBus.injectCraftedItems` throws `IllegalStateException("Target is not a InventoryAdaptor")`.

Nothing in the crafting link, CPU delivery loop, network storage handler, Import Bus, or grid tick invocation catches that exception as a recoverable failed delivery. `TickManagerCache` catches it only to wrap it in a `ReportedException`, which crashes the logical server with a "Ticking GridNode" crash report.

## Player-visible symptom

As soon as the delayed crafted output is evaluated for import, the dedicated server crashes and disconnects all players. In singleplayer, the integrated server crashes and the world closes/crash-reports. In the stated reproduction the crash report identifies the Import Bus being ticked. The furnace output has not yet been removed because the exception occurs during the capacity simulation.

## Reachability proof

With a Crafting Card installed, `PartBaseExportBus.doBusWork` first attempts to export the configured ingot from storage. If none is available, the unchanged `itemToSend` value and a target that can accept the configured stack cause `MultiCraftingTracker.handleCrafting` to start and then submit a normal crafting job for the Export Bus.

The tracker stores the resulting `ICraftingLink`, includes it in `getRequestedJobs`, and persists it in the part's NBT. Removing the adjacent chest calls `PartSharedItemBus.onNeighborChanged`, which refreshes tick state and makes subsequent `getTarget()` calls resolve the missing tile to no adaptor. It does not cancel or detach the tracker link. The Export Bus itself remains on the cable-bus host and remains the live requester.

Crafting results entering network storage encounter `CraftingGridCache` as an auto-crafting inventory with maximum priority. Its `canAccept` finds the CPU waiting for the iron ingot, and `injectItems` calls `CraftingCPUCluster.injectItems`. For final output, both simulation and modulation delegate through `CraftingLink.injectItems` to the requester. The link performs no exception handling.

`PartBaseExportBus.injectCraftedItems` evaluates `getTarget()` before its `try` block. When the chest has been broken, the pattern match fails and immediately throws `IllegalStateException`. Returning the input stack would be a supported rejection under `ICraftingRequester`, but this implementation never reaches that return.

In the stated reproduction, `PartImportBus.calculateMaximumAmountToImport` first simulates removal from the furnace, then immediately calls `destination.injectItems(..., Actionable.SIMULATE, ...)` to calculate network capacity. That simulation reaches the crafting requester and throws before `importStuff` performs the real `removeItems` call. The bus-work call stack catches only `GridAccessException`. `TickManagerCache.onUpdateTick` catches the escaping throwable, creates a crash report, and rethrows `ReportedException`; it does not disable or skip the faulty operation.

## Root cause

The Export Bus treats a missing or changed target as an impossible programmer state during asynchronous crafting delivery, even though target removal is a normal world transition and the target is explicitly allowed to differ between job submission and completion.

The requester API already defines the safe result for this state: return whatever items cannot be accepted. Throwing instead bypasses the CPU's leftover accounting. The crafting link also outlives target changes by design, so there is no lifecycle guard that makes the assertion valid.

## Execution path

1. The active Export Bus ticks through `PartBaseExportBus.doBusWork` with an empty network stock and a chest that can accept the filtered ingot.
2. `MultiCraftingTracker.handleCrafting` begins the job, submits it to `CraftingGridCache`, and stores the returned requester link.
3. The crafting CPU dispatches the processing pattern; the vanilla furnace begins smelting.
4. Player breaks the target chest. `PartSharedItemBus.onNeighborChanged` updates bus scheduling/cache state but leaves the crafting link active.
5. The furnace finishes; the Import Bus simulates removing the ingot and passes that simulated stack to the ME item monitor with `Actionable.SIMULATE` to calculate import capacity.
6. `NetworkInventoryHandler.injectItems` prioritizes `CraftingGridCache`, whose `canAccept` finds the waiting CPU and whose simulated `injectItems` calls `CraftingCPUCluster.injectItems`.
7. The CPU recognizes simulated final output and calls `CraftingLink.injectItems` with `Actionable.SIMULATE`.
8. `CraftingLink.injectItems` calls `PartBaseExportBus.injectCraftedItems` on the still-live requester.
9. `PartBaseExportBus.injectCraftedItems` calls `getTarget`; the missing chest produces no `InventoryAdaptor`, so it throws `IllegalStateException`.
10. The exception escapes the capacity simulation and Import Bus work before the real source removal. `TickManagerCache.onUpdateTick` wraps it in `ReportedException`, terminating the logical server tick.

## Code evidence

- `src/main/java/appeng/parts/automation/PartBaseExportBus.java`, `doBusWork`, lines 72-146: when direct export makes no progress, a Crafting Card starts/submits a job for a configured stack that the current target can accept.
- `src/main/java/appeng/helpers/MultiCraftingTracker.java`, `handleCrafting`, lines 79-125: the asynchronous job progresses from a future to a submitted requester link and remains stored while active.
- `src/main/java/appeng/helpers/MultiCraftingTracker.java`, `getRequestedJobs`, `jobStateChange`, and `cancel`, lines 128-179: the link remains live until the job changes state or an explicit cancellation occurs.
- `src/main/java/appeng/parts/automation/PartBaseExportBus.java`, `readFromNBT`, `writeToNBT`, and `getRequestedJobs`, lines 57-69 and 219-222: requester links persist across save/load and are re-advertised to the grid.
- `src/main/java/appeng/parts/automation/PartSharedItemBus.java`, `onNeighborChanged` and `getTarget`, lines 97-132: removing the chest refreshes the target adaptor to null but does not cancel any Export Bus crafting request.
- `src/main/java/appeng/me/storage/NetworkInventoryHandler.java`, insertion ordering and `injectItems`, lines 61-89 and 112-252: auto-crafting inventories are visited first, and handler exceptions are not converted to rejected leftovers.
- `src/main/java/appeng/me/cache/CraftingGridCache.java`, `canAccept`, `isAutoCraftingInventory`, and `injectItems`, lines 450-499: the crafting cache accepts a requested result and delegates it directly to each crafting CPU.
- `src/main/java/appeng/me/cluster/implementations/CraftingCPUCluster.java`, `injectItems`, lines 366-519: requested final output is passed to `myLastLink.injectItems` in both simulation and modulation with no exception boundary around requester delivery.
- `src/main/java/appeng/crafting/CraftingLink.java`, `injectItems`, lines 145-151: a connected link calls the requester's `injectCraftedItems` directly and propagates any exception.
- `src/main/java/appeng/parts/automation/PartBaseExportBus.java`, `injectCraftedItems`, lines 224-247: a missing/noninventory target throws before the method's `try`; the API-compatible `return items` fallback is therefore unreachable for this ordinary target transition.
- `src/main/java/appeng/api/networking/crafting/ICraftingRequester.java`, `injectCraftedItems`, lines 33-49: the requester contract explicitly permits returning unwanted/unaccepted items.
- `src/main/java/appeng/parts/automation/PartImportBus.java`, `importStuff` and `calculateMaximumAmountToImport`, lines 110-187: network insertion capacity is simulated before source removal, and a requester runtime exception is not caught.
- `src/main/java/appeng/me/cache/TickManagerCache.java`, `onUpdateTick`, lines 64-123: any escaping throwable from a grid-ticked bus is wrapped into a crash-report `ReportedException` and rethrown.

## Why existing validation does not prevent it

The Export Bus correctly checks that the target exists and can accept the item before requesting the craft, but asynchronous completion means that validation is necessarily stale. A normal block break invalidates the premise. Neighbor notification updates the adaptor cache but has no relationship to `MultiCraftingTracker`.

The CPU is designed to handle refusal: `injectCraftedItems` returns a leftover stack, and the CPU threads that leftover back to the current insertion. Power checks inside the Export Bus are also able to return the input when power is insufficient. Only the missing-target branch throws instead of using the same rejection mechanism. No player timing outside the furnace's several-second processing window is required.

## Minimal fix direction

In `PartBaseExportBus.injectCraftedItems`, resolve the target safely and return `items` when it is absent or no longer adapts to the supported inventory type. Do not throw for a world-state change. The existing active-network, power, simulation, and `addStack` paths can remain unchanged.

Optionally cancel outstanding tracker jobs when the Export Bus itself is removed or when its Crafting Card/configuration changes. Target disappearance alone should still be safe even without cancellation, because inventories may be replaced or chunks may transition normally while a job is active.

## Regression-test proposal

- Test setup: A powered grid with a crafting CPU, an Export Bus with Crafting Card targeting a chest, and a controllable delayed crafting provider for the configured output.
- Initial state: Let the bus submit the job and assert one active requester link while the chest is present.
- Triggering action: Remove the chest, then simulate and modulate insertion of the requested final output into the grid as the returning crafting medium would.
- Expected assertion: Simulation and modulation complete without throwing; the undelivered output is returned/retained, the server tick completes, and no item is lost.
- Incorrect behavior the test must prevent: `IllegalStateException("Target is not a InventoryAdaptor")` escaping through the CPU and becoming a `Ticking GridNode` `ReportedException`.
- Suggested style: Add a focused unit/integration test for `PartBaseExportBus.injectCraftedItems` with target removal, plus a HorizonQA game test using a delayed processing pattern and a breakable vanilla chest.

## Runtime confirmation

Not runtime-confirmed. Static proof covers the normal Crafting Card request, outstanding-link lifetime, neighbor target invalidation, crafting-inventory priority, CPU/link delegation, missing-target throw, and grid-tick crash wrapper. Runtime confirmation should use a furnace processing pattern to make the target-removal window deterministic and capture the resulting crash report.

## Remaining uncertainty

Other ordinary crafting-output return paths may reach modulation instead of this Import Bus simulation first. For the stated steps, the Import Bus capacity simulation is the deterministic first failing call. The uncaught exception and logical-server crash do not depend on whether another setup reaches simulation or modulation first.
