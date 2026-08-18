# Bug audit

Audit date: 2026-08-13  
Reviewed revision: `3654a842b` (`master`)

The fluid-container transfer energy issue from #1504 is intentionally excluded because it is already being fixed/has landed on the reviewed branch.

## Confirmed findings

### 1. Interface visibility can be toggled remotely without validating the target

- Severity: High
- Files: `src/main/java/appeng/core/sync/packets/PacketToggleInterfaceVisibility.java:49-75`
- Trigger: A modified client sends the packet while any `AEBaseContainer` is open, supplying the dimension, coordinates, and side of an arbitrary loaded interface.
- Impact: The server resolves that interface and changes its `INTERFACE_TERMINAL` setting without checking that the player has an interface-terminal container open, that the target is part of that terminal's reachable grid, that the player is in range, or that the player has build permission on the target network.

### 2. Part and facade interaction packets bypass the normal reach check

- Severity: High
- Files: `src/main/java/appeng/core/sync/packets/PacketPartInteraction.java:31-38`, `src/main/java/appeng/core/sync/packets/PacketPartInteraction.java:61-117`
- Trigger: A modified client supplies coordinates for an AE part host in any loaded chunk of the player's current dimension.
- Impact: The server invokes wrench logic or `onActivate`/`onShiftActivate` at the supplied position without checking the player's distance from the block. Forge's interaction event can enforce protection rules, but it does not restore vanilla's server-side reach validation.

### 3. `PacketVirtualSlot` trusts client-controlled inventory sizes and slot indices

- Severity: High
- Files: `src/main/java/appeng/core/sync/packets/PacketVirtualSlot.java:24-35`, `src/main/java/appeng/core/sync/packets/PacketVirtualSlot.java:89-93`, `src/main/java/appeng/container/implementations/ContainerBusIO.java:34-38`, `src/main/java/appeng/container/implementations/ContainerFormationPlane.java:118-122`, `src/main/java/appeng/container/implementations/ContainerStorageBus.java:298-302`
- Trigger: A modified client sends a virtual-slot map containing a negative or oversized slot index while one of the affected containers is open.
- Impact: The handlers ignore the supplied storage name and write every entry directly into a fixed-size `IAEStackInventory`. Invalid indices throw on the server; incompatible stack channels can also be written into configuration inventories that expect a specific stack type.

### 4. `PacketPatternValueSet` can write out of bounds or inject the wrong stack type

- Severity: High
- Files: `src/main/java/appeng/core/sync/packets/PacketPatternValueSet.java:26-29`, `src/main/java/appeng/core/sync/packets/PacketPatternValueSet.java:51-64`, `src/main/java/appeng/container/implementations/ContainerPatternTerm.java:684-706`
- Trigger: A modified client supplies an invalid storage-name ordinal, slot index, or non-item stack while a compatible pattern container is open.
- Impact: The server performs no bounds or channel validation before writing the stack. In crafting mode it additionally casts the supplied value directly to `IAEItemStack`, allowing an out-of-bounds exception or class-cast failure on the server packet path.

### 5. An unbounded craft amount can make complex-pattern planning allocate until the server stalls or runs out of memory

- Severity: High
- Files: `src/main/java/appeng/core/sync/packets/PacketCraftRequest.java:46-51`, `src/main/java/appeng/core/sync/packets/PacketCraftRequest.java:78-107`, `src/main/java/appeng/crafting/v2/resolvers/CraftableItemResolver.java:691-705`, `src/main/java/appeng/crafting/v2/resolvers/CraftableItemResolver.java:720-728`
- Trigger: A client requests a very large `long` amount for an item resolved through a complex pattern.
- Impact: The packet handler accepts the amount without a positive-value or upper-bound check. The resolver then creates one `CraftFromPatternTask` per requested unit using an `int` loop counter compared with a `long`, causing extreme CPU/memory use and eventual counter wrap for sufficiently large requests.

### 6. Partial-item packet assembly has an unbounded memory-retention path

- Severity: High
- Files: `src/main/java/appeng/core/sync/packets/PacketPartialItem.java:27-40`, `src/main/java/appeng/core/sync/packets/PacketPartialItem.java:46-54`, `src/main/java/appeng/container/AEBaseContainer.java:201-230`
- Trigger: A client repeatedly sends partial packets whose advertised page count never equals the number of chunks accumulated by the open container.
- Impact: Every payload is retained in `dataChunks`, and the list is cleared only after an exact equality match. The packed page count is also decoded with signed right shift, so a legitimate payload of 128 or more pages produces a negative count and can never complete. Either case retains data until the container is destroyed and can be used for memory exhaustion.

### 7. `PacketPinsUpdate` allocates an array using an unbounded client length

- Severity: High
- Files: `src/main/java/appeng/core/sync/packets/PacketPinsUpdate.java:28-43`
- Trigger: A client sends a large non-negative array length in a server-bound pins packet.
- Impact: The packet constructor allocates `new IAEStack<?>[arrLength]` before validating the length against the fixed pin capacity or remaining packet bytes. A single malicious length can request a multi-gigabyte allocation on the networking thread.

### 8. Pattern optimization trusts arbitrary client multipliers and can corrupt every matching encoded pattern

- Severity: High
- Files: `src/main/java/appeng/core/sync/packets/PacketOptimizePatterns.java:24-29`, `src/main/java/appeng/core/sync/packets/PacketOptimizePatterns.java:47-51`, `src/main/java/appeng/container/implementations/ContainerOptimizePatterns.java:143-183`, `src/main/java/appeng/util/PatternMultiplierHelper.java:55-95`
- Trigger: A modified client submits negative or oversized bit-shift multipliers from the optimization GUI.
- Impact: The server never clamps the submitted values to the computed per-pattern maximum. It applies them to all matching patterns across every reachable grid, and Java masks oversized shift distances; counts can therefore become zero, negative, or overflowed, corrupting pattern NBT network-wide.

### 9. The annihilation plane's permission-cache timer runs backwards while active

- Severity: High
- Files: `src/main/java/appeng/parts/automation/PartAnnihilationPlane.java:58-65`, `src/main/java/appeng/parts/automation/PartAnnihilationPlane.java:301-341`, `src/main/java/appeng/core/settings/TickRates.java:23`
- Trigger: The plane ticks faster than its 120-tick maximum interval, especially at the 2-tick urgent interval used while working.
- Impact: `cacheTime += ticksSinceLastCall - 120` subtracts time on every fast tick, so the 60-tick expiry is never reached. An allowed break result can remain cached for later protected blocks, while a denied result can keep the plane disabled indefinitely; protection changes are not rechecked as intended.

### 10. A misbehaving fluid handler can convert one fluid into another during extraction

- Severity: High
- Files: `src/main/java/appeng/me/storage/MEMonitorIFluidHandler.java:93-138`
- Trigger: An external `IFluidHandler` returns a different fluid from the one requested, which the code already detects and logs.
- Impact: After draining the unexpected fluid, the wrapper returns a copy of the *requested* fluid with the drained amount and decrements the requested-fluid cache entry. The warning therefore documents a dupe/conversion condition but still completes it instead of rejecting or reconciling the returned fluid.

### 11. Simulated extraction through `MEIInventoryWrapper` actually removes items

- Severity: High
- Files: `src/main/java/appeng/me/storage/MEIInventoryWrapper.java:107-154`, `src/main/java/appeng/tile/inventory/AppEngInternalInventory.java:57`
- Trigger: Any caller invokes `extractItems(..., Actionable.SIMULATE, ...)` on an internal inventory exposed through `toMEInventory()`, or on a wrapper backed by an inventory adaptor.
- Impact: The method never checks `mode`: it always calls `removeItems` on the adaptor path and always calls `decrStackSize` on the direct path. Read-only capacity/planning probes therefore consume real items.

### 12. Simulated insertion underreports capacity in partially filled inventories

- Severity: Medium
- Files: `src/main/java/appeng/me/storage/MEIInventoryWrapper.java:39-103`
- Trigger: A matching partial stack has room, but the target inventory has no empty slot, and a caller simulates insertion.
- Impact: Existing stacks are considered only in `MODULATE` mode. `SIMULATE` skips their free space and searches only empty slots, returning the entire input as rejected even though the real insertion would succeed. This breaks the standard simulation/modulation contract and can incorrectly abort transfers.

### 13. Empty-slot insertion can create stacks larger than the item's own maximum

- Severity: Medium
- Files: `src/main/java/appeng/me/storage/MEIInventoryWrapper.java:81-95`, `src/main/java/appeng/tile/inventory/AppEngInternalInventory.java:113-115`, `src/main/java/appeng/tile/inventory/AppEngInternalInventory.java:151-154`
- Trigger: An AE stack containing more than one normally unstackable item is inserted into an empty internal-inventory slot whose inventory limit is greater than the item's maximum stack size.
- Impact: The empty-slot path clamps only to `getInventoryStackLimit()` and omits the `getMaxStackSize()` clamp used by the existing-stack path. `AppEngInternalInventory` stores the value unchanged, so max-stack-size-1 items can become illegal multi-item stacks.

### 14. Processing-pattern multiplication can pass its overflow check and write negative counts

- Severity: High
- Files: `src/main/java/appeng/core/sync/packets/PacketPatternMultiSet.java:17-39`, `src/main/java/appeng/container/implementations/ContainerPatternTerm.java:585-647`
- Trigger: A modified client supplies a large multiplier, or `Integer.MIN_VALUE`, while the processing-pattern multiplier sub-GUI is open.
- Impact: The preflight converts the multiplication to `double`; near `Long.MAX_VALUE`, rounding can make an overflowing product compare equal to rather than greater than the limit, after which the real `long` multiplication wraps negative. Negating `Integer.MIN_VALUE` also leaves it negative, so the division path can write negative stack counts when the existing counts are divisible by that value.

### 15. Deep-storage integrations silently narrow AE's long item counts to `int`

- Severity: High
- Files: `src/main/java/appeng/integration/modules/helpers/MinefactoryReloadedDeepStorageUnit.java:35-68`, `src/main/java/appeng/integration/modules/helpers/JabbaBarrel.java:24-66`, `src/main/java/appeng/integration/modules/helpers/FactorizationBarrel.java:42-75`
- Trigger: More than `Integer.MAX_VALUE` items are inserted in one AE operation, or an existing stored count plus the input overflows 32 bits.
- Impact: The MFR and JABBA adapters cast accepted long counts to `int` yet can return `null`, telling AE that the entire input was stored even when the written count wrapped or truncated. The Factorization adapter performs the sum in `int`, allowing a negative `newTotal` and likewise reporting no remainder. This can void items or corrupt the external storage count.

### 16. IC2-powered tools cannot use their final exact amount of charge

- Severity: Medium
- Files: `src/main/java/appeng/items/tools/powered/powersink/IC2.java:56-70`
- Trigger: An IC2-backed AE tool contains exactly the amount of EU required for an operation.
- Impact: `canUse` checks `getCharge(is) > amount` rather than `>=`. `use` therefore rejects an operation that should consume the remaining charge, leaving the tool unusable at the exact boundary.

### 17. `AdaptorList` merges NBT-distinct items and discards one stack's data

- Severity: High
- Files: `src/main/java/appeng/util/inv/AdaptorList.java:136-154`, `src/main/java/appeng/util/Platform.java:1727-1729`, `src/main/java/appeng/util/InventoryAdaptor.java:215-220`
- Trigger: An adapted `ArrayList<ItemStack>` already contains an item with the same item ID and damage but different NBT, and another such stack is inserted.
- Impact: `addItems` uses `isSameItem`, which delegates to `ItemStack.isItemEqual` and does not compare NBT. It adds the incoming count to the existing stack and returns success, discarding enchantments, custom names, inventories, or any other distinct NBT from the incoming stack.

### 18. Crafting-CPU follow packets can subscribe arbitrary player names

- Severity: Medium
- Files: `src/main/java/appeng/core/sync/packets/PacketValueConfig.java:117-123`, `src/main/java/appeng/container/implementations/ContainerCraftingCPU.java:301-304`, `src/main/java/appeng/me/cluster/implementations/CraftingCPUCluster.java:187-220`, `src/main/java/appeng/me/cluster/implementations/CraftingCPUCluster.java:1943-1955`
- Trigger: A modified client with a crafting-CPU container open sends `TileCrafting.Follow` with another player's name instead of its own.
- Impact: The server toggles the supplied string without binding it to the sender. The victim receives chat, sound, and completion packets if online; if offline, notifications are queued and persisted under that name. Arbitrary names can also inflate the persisted follower/notification data.

### 19. Pin actions trust a client-controlled index and can index outside the fixed pin array

- Severity: Medium
- Files: `src/main/java/appeng/core/sync/packets/PacketMonitorableAction.java:25-27`, `src/main/java/appeng/core/sync/packets/PacketMonitorableAction.java:43-74`, `src/main/java/appeng/container/implementations/ContainerMEMonitorable.java:659-668`, `src/main/java/appeng/items/contents/PinsHandler.java:35-60`, `src/main/java/appeng/items/contents/PinList.java:25-31`
- Trigger: A client sends `SET_PIN` or `UNSET_PIN` with an index below zero or at least 288 while a monitorable terminal is open.
- Impact: No layer validates the index before direct array access. The server packet path throws an `ArrayIndexOutOfBoundsException`; `SET_PIN` can also access the invalid index during duplicate-pin handling before the final write.

### 20. `ColoredItemDefinition.allStacks` ignores its requested stack size

- Severity: Low
- Files: `src/main/java/appeng/api/util/AEColoredItemDefinition.java:47`, `src/main/java/appeng/core/features/ColoredItemDefinition.java:61-67`
- Trigger: An API caller asks for all color variants with a stack size other than one.
- Impact: The implementation hard-codes `stack(1)` instead of using the method parameter, violating the public API contract and returning incorrectly sized recipe or integration inputs. It also dereferences missing color definitions even though the single-color `stack` method handles them as nullable.

## Verification status

- `./gradlew test --no-daemon` passes on revision `3654a842b` (`BUILD SUCCESSFUL`, 2026-08-13).
- The existing suite does not exercise the adversarial packet values, large-count boundaries, or simulation-contract cases above; a green baseline therefore does not invalidate the findings.
- This audit changed no production or test source files. Only this report was added.
