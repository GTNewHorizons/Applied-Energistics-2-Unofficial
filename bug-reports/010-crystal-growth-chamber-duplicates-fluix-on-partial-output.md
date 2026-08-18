# Crystal Growth Chamber duplicates Fluix on a partial output fit

- Report ID: BUG-010
- Status: Accepted
- Confidence: Very high
- Confirmation: Statically proven but not runtime-confirmed
- Player impact: If the chamber has room for only one of a recipe's two Fluix Crystals, it inserts that one but consumes none of the Charged Certus Quartz, Nether Quartz, or Redstone. An ordinary hopper can keep recreating that one-space condition and generate unlimited Fluix from a single unchanged ingredient set.
- Affected mode: Both
- Affected version: `rv3-beta-1032-GTNH-1-g3654a842b`
- Minecraft version: 1.7.10
- Mod loader: Minecraft Forge 10.13.4.1614
- Branch: `master`
- Commit: `3654a842bf9b40e6e08ead7bb3a46e3d7b169eaf`
- Relevant subsystem: Crystal Growth Chamber recipe output insertion and inventory transaction ordering

## Preconditions

- A powered Crystal Growth Chamber.
- Its 27-slot inventory is completely occupied except for the space that will hold its Fluix output.
- At least one stack each of Charged Certus Quartz Crystal, Nether Quartz, and Redstone is present; the stacks chosen first by the chamber must contain more than 32 items so they remain nonempty while the initial output stack is prepared.
- Every other chamber slot contains a valid chamber input, such as additional full stacks of those three ingredient types. This prevents the two-item output from finding a second destination.
- A vanilla hopper and chest positioned to extract completed Fluix Crystals from the chamber.

All inputs can be inserted through the chamber's normal GUI or automation. The output stack is created by the chamber's supported built-in Fluix recipe; no invalid item injection, edited data, commands, or third-party inventory is required.

## Exact reproduction steps

1. Place and power a Crystal Growth Chamber.
2. Fill 26 of its 27 slots. Include a stack of at least 64 Charged Certus Quartz Crystals, 64 Nether Quartz, and 64 Redstone, and fill the remaining 23 occupied slots with any valid chamber inputs. Leave exactly one slot empty.
3. Let the chamber process. Each normal recipe consumes one of each ingredient and inserts two Fluix Crystals into the empty/output slot.
4. Wait until that output slot contains exactly 64 Fluix Crystals. The three selected ingredient stacks still contain 32 each, every other chamber slot remains occupied, and the chamber has no room for another complete two-crystal output.
5. Record the three ingredient counts.
6. Attach a vanilla hopper so it can extract from the chamber, and give the hopper an ordinary chest destination.
7. Let the setup run for several hopper transfers.
8. Inspect the chest, chamber output stack, and the three ingredient stacks.

The hopper extracts one Fluix at a time, temporarily changing the chamber's full output stack from 64 to 63. Each inventory change wakes the chamber. The hopper/chest may be attached earlier and disabled with redstone until step 6 if desired.

## Expected result

The chamber should create its two-output Fluix recipe atomically. If only one item fits, it should insert nothing and leave the ingredients untouched. Once two spaces are available, it should insert two Fluix and consume exactly one Charged Certus Quartz, one Nether Quartz, and one Redstone.

## Actual predicted result

Each time the hopper lowers the output stack to 63, the chamber attempts to insert two Fluix. Its modulating inventory adaptor immediately merges one into that stack and returns the other as a leftover. Because the return is nonnull, `tryCreateFluix` returns before decrementing any ingredient. The output stack is back at 64, the hopper eventually removes another Fluix, and the same free partial insertion repeats.

The method also returns `false`, so this free output is not counted as work and `consumePower` is not called for it. The chest fills with Fluix while the ingredient counts and chamber power cost remain unchanged after the initial legitimate recipes.

## Player-visible symptom

The hopper continuously accumulates Fluix Crystals, but the chamber's Charged Certus Quartz, Nether Quartz, and Redstone stop decreasing once the output reaches 64. The chamber repeatedly replaces each Fluix removed by the hopper for free. The loop continues until external output storage is full or the chamber layout changes.

## Reachability proof

The Crystal Growth Chamber is registered as a normal AE2 block and exposes all 27 internal slots in `ContainerCrystalGrowthChamber`. Those GUI slots accept exactly the same supported inputs as `TileCrystalGrowthChamber.isItemValidForSlot`: growable seeds, Nether Quartz, Redstone, and Charged Certus Quartz. A player can therefore fill every non-output position through the normal GUI.

On each powered tick, `tickingRequest` processes growable crystals and then calls `tryCreateFluix`. That method scans the live inventory for one Charged Certus Quartz, one Redstone, and one Nether Quartz. With the stated stacks present, the recipe remains eligible indefinitely.

The recipe constructs an output stack of two Fluix Crystals and wraps the chamber's entire internal inventory in `WrapperInventoryRange(..., true)`. The `true` flag deliberately ignores normal slot-validity restrictions so the machine can place its own output. `InventoryAdaptor.getAdaptor` resolves this wrapper to `AdaptorIInventory`.

`AdaptorIInventory.addItems` is modulating, not simulating. When it visits the 63-Fluix stack, it calculates one item of room, increments the live stack to 64, and leaves one item in its returned remainder. Its documented return contract is precisely “the left itemstack, which could not be added.”

`tryCreateFluix` checks that return only after the mutation. Any nonnull remainder causes an immediate `false` return. The three `decrStackSize` calls exist only after that branch, so none runs on the partial fit.

Vanilla hopper extraction is supported because every slot is exposed through `getAccessibleSlotsBySide`, and `canExtractItem` permits anything that is not a valid chamber input. Normal Fluix Crystal is neither an `IGrowableCrystal` seed nor Nether Quartz, Redstone, or Charged Certus Quartz, so the hopper may extract it while the ingredient stacks remain protected from extraction. `AppEngInternalInventory.decrStackSize` notifies `onChangeInventory`, which wakes the sleeping grid-tick device after each hopper removal.

## Root cause

`tryCreateFluix` uses a state-changing insertion as a capacity predicate. It assumes `addItems(output) != null` means no output was inserted, but the adaptor contract means only that not all output was inserted. Ingredient consumption is conditional on total success, while already inserted partial output is never rolled back.

## Execution path

1. The hopper extracts one normal Fluix Crystal from the chamber's 64-stack.
2. `AppEngInternalInventory.decrStackSize` changes it to 63 and calls `TileCrystalGrowthChamber.onChangeInventory`.
3. `onChangeInventory` wakes the chamber's grid tick device.
4. On the next powered `tickingRequest`, `tryCreateFluix` finds all three ordinary ingredients.
5. It creates a two-Fluix `ItemStack` and calls the internal inventory adaptor's modulating `addItems`.
6. `AdaptorIInventory.addItems` merges one Fluix into the 63-stack and returns a one-Fluix remainder.
7. `tryCreateFluix` sees the nonnull remainder and returns `false` immediately.
8. The ingredient-decrement calls and `consumePower` are skipped.
9. The hopper later extracts the newly created Fluix and restarts the same path.

## Code evidence

- `src/main/java/appeng/block/misc/BlockCrystalGrowthChamber.java`, constructor and `onActivated`, lines 21-47: the chamber is a normal placeable GUI block.
- `src/main/java/appeng/container/implementations/ContainerCrystalGrowthChamber.java`, constructor, lines 9-29: the supported GUI exposes all 27 chamber slots and delegates valid-input checks to the tile.
- `src/main/java/appeng/tile/misc/TileCrystalGrowthChamber.java`, `isItemValidForSlot`, lines 87-92: Charged Certus Quartz, Nether Quartz, Redstone, and growable crystals are legitimate player inputs.
- `src/main/java/appeng/tile/misc/TileCrystalGrowthChamber.java`, `canExtractItem`, `onChangeInventory`, and `getAccessibleSlotsBySide`, lines 94-123: normal Fluix is hopper-extractable, and each extraction wakes ticking across all exposed slots.
- `src/main/java/appeng/tile/misc/TileCrystalGrowthChamber.java`, `tickingRequest`, lines 182-206: the powered machine calls the Fluix recipe and charges power only when that call reports work.
- `src/main/java/appeng/tile/misc/TileCrystalGrowthChamber.java`, `tryCreateFluix`, lines 209-242: a modulating two-output insertion happens before the all-or-nothing return check and before any ingredient decrement.
- `src/main/java/appeng/util/inv/WrapperInventoryRange.java`, constructor and `isItemValidForSlot`, lines 34-41 and 117-124: the `true` flag makes every wrapped chamber slot accept the machine's output.
- `src/main/java/appeng/util/inv/AdaptorIInventory.java`, `addItems`, lines 187-273: partial merging mutates the existing stack, subtracts only the amount that fit, and returns the nonempty remainder.
- `src/main/java/appeng/tile/inventory/AppEngInternalInventory.java`, `decrStackSize`, lines 79-100: an external one-item extraction notifies the tile and marks the inventory dirty.
- `src/main/java/appeng/tile/AEBaseInvTile.java`, `getAccessibleSlotsFromSide` and sided inventory delegation, lines 142-159: ordinary sided automation routes through the chamber's all-slot exposure and output predicate.

## Why existing validation does not prevent it

The adaptor behaves correctly: its API explicitly permits partial insertion and returns whatever did not fit. The chamber neither simulates first nor rolls back the portion already accepted.

Input validation is not bypassed. The full-inventory setup uses only allowed chamber ingredients, while the 64-Fluix output stack is produced by 32 successful executions of the chamber's own recipe. The hopper uses the tile's explicit sided extraction rule for completed output.

The machine's power simulation does not protect the recipe invariant. Power is available at the start of the tick, but `consumePower` is called only when `hasWork` is true. A partial Fluix insert returns `false`, so it creates one output without charging power as well as without consuming ingredients.

Inventory persistence and block drops preserve the resulting stacks but cannot restore the skipped inputs; this is a live transaction-order error rather than corrupt or stale state.

## Minimal fix direction

Simulate insertion of the complete two-Fluix output before changing either side of the recipe. If `simulateAdd(output)` returns any remainder, do nothing. If the full output fits, consume exactly one of each ingredient and then perform the modulating insert; because this is the tile's private synchronous inventory, the simulation and commit have no intervening external mutation.

Alternatively, implement a small internal transaction that rolls back every inserted output if the complete result cannot be committed. Preserve the existing ability for the machine to write output into slots that reject it as external input, and preserve sided extraction of completed Fluix.

## Regression-test proposal

- Test setup: Create a powered `TileCrystalGrowthChamber` with a 63-Fluix stack, one stack each of the three recipe ingredients, and every other slot occupied by valid non-Fluix inputs.
- Initial state: Assert exactly one output space, record all three ingredient counts, total Fluix, and stored power.
- Triggering action: Run one chamber tick.
- Expected assertion: No Fluix is inserted, no ingredient or power is consumed, and the 63-stack remains 63. Then free a second output space, run another tick, and assert exactly two Fluix are added while exactly one of each ingredient and the intended power are consumed.
- Incorrect behavior the test must prevent: A one-item partial output being committed while all ingredients remain unchanged.
- Suggested style: Add a focused machine game test with both direct 63-stack state and an end-to-end hopper/chest setup; also cover zero, one, and two available output spaces.

## Runtime confirmation

Not runtime-confirmed. Static proof covers supported GUI input, the chamber's own creation of the output stack, vanilla sided extraction, tick wakeup, exact partial-insert mutation, the nonnull leftover branch, skipped ingredient decrements, and skipped power consumption. A game run should confirm the hopper cadence and visible inventory counts.

## Remaining uncertainty

None regarding the partial-insert transaction or the resulting one-item duplication per wake cycle. Runtime confirmation remains useful only for recording how quickly a vanilla hopper/chest setup repeats the deterministic cycle.
