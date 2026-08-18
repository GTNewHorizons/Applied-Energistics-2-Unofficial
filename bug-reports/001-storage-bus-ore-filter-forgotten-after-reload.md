# Storage bus ore filter is forgotten after reload and card reinsertion

- Report ID: BUG-001
- Status: Accepted
- Confidence: Very high
- Confirmation: Statically proven but not runtime-confirmed
- Player impact: A configured Storage Bus silently loses its ore-dictionary restriction and becomes unfiltered after a normal save/load followed by removing and reinserting its Ore Dictionary Filter Card. Items that the player intended to keep out of the attached inventory can then be routed into it.
- Affected mode: Both
- Affected version: `rv3-beta-1032-GTNH-1-g3654a842b`
- Minecraft version: 1.7.10
- Mod loader: Minecraft Forge 10.13.4.1614
- Branch: `master`
- Commit: `3654a842bf9b40e6e08ead7bb3a46e3d7b169eaf`
- Relevant subsystem: Storage Bus configuration, upgrade lifecycle, and NBT persistence

## Preconditions

- A powered ME network with a channel available.
- A Storage Bus attached to an item inventory such as a chest.
- One Ore Dictionary Filter Card.
- At least one item whose ore-dictionary name can be used for a visible routing check, for example an ingot registered under `ingotIron`, plus a nonmatching item.

All of these conditions are established through normal crafting, placement, and GUI interaction. No commands, edited NBT, unusual packets, or third-party contract violations are required.

## Exact reproduction steps

1. Place a chest and attach a Storage Bus to it as part of a powered ME network.
2. Insert an Ore Dictionary Filter Card into the Storage Bus upgrade slot.
3. Click the Storage Bus's ore-filter button, enter `ingotIron`, press Enter, and return to the Storage Bus GUI.
4. Verify the filter is active by inserting or routing an ore-dictionary iron ingot and a nonmatching item through the ME network: only the matching ingot should be accepted by this Storage Bus.
5. Save and quit the world, then reopen it. On a dedicated server, stop and restart the server with the chunk loaded instead.
6. Open the Storage Bus, remove the Ore Dictionary Filter Card, and immediately put the same card back without another reload.
7. Open the ore-filter sub-GUI again. The expression is blank.
8. Insert or route both the matching ingot and the previously rejected nonmatching item through the ME network.

An equivalent second variant is to configure the filter, remove the card, save/reload, and then reinstall it. The remembered expression is also lost in that sequence because only the now-empty active field is written to world NBT.

## Expected result

Removing an upgrade may temporarily disable its feature, but reinserting the card should restore the Storage Bus's remembered ore-filter expression. This is exactly what the current code does when the removal/reinsertion happens before a reload.

## Actual predicted result

After the world reload, removing the card clears `oreFilterString`. Reinserting the card attempts to restore it from `previousOreFilterString`, but that field was never reconstructed from NBT and is empty. The filter GUI is blank and the Storage Bus is no longer partitioned by `ingotIron`.

## Player-visible symptom

The saved expression disappears from the ore-filter text field. More importantly, with an empty `OreFilteredList`, the Storage Bus is treated as having no partition filter, so nonmatching items can enter the attached inventory. This can contaminate a dedicated storage inventory or redirect items away from the player's intended destinations.

## Reachability proof

The Storage Bus supports one Ore Dictionary Filter Card in this build (`Registration` registers `Upgrades.ORE_FILTER` for the Storage Bus). With the card installed, `GuiUpgradeable` makes the ore-filter button visible and sends the normal GUI-switch packet. Pressing Enter in `GuiOreFilter` sends `PacketValueConfig("OreFilter", value)`. The server accepts this only while `ContainerOreFilter` is open and calls `PartStorageBus.setFilter` through the container host.

`setFilter` writes the same player-entered value to both `oreFilterString` and `previousOreFilterString`. `upgradesChanged` deliberately clears only the active value when the card is absent and restores it from `previousOreFilterString` when the card returns. Therefore ordinary remove/reinsert behavior before a reload proves that remembering the expression is intended.

World persistence breaks that state transition. `writeToNBT` stores only `oreFilterString` under `filter`; `readFromNBT` loads only `oreFilterString` and never initializes `previousOreFilterString`. If the save occurred while the card was installed, the active expression initially appears after reload, but the first card removal clears it and the first reinsertion restores the empty, unpersisted remembered field. If the save occurred while the card was absent, the active field written to NBT is already empty and the remembered field is again unavailable after reload.

Once the empty string reaches handler construction, `OreFilteredList.makeFilter("")` returns `null`, `OreFilteredList.isEmpty()` returns true, and `MEInventoryHandler.canAccept` treats an empty partition list as unrestricted. No later validation reconstructs the lost expression.

## Root cause

The Storage Bus uses two fields for one upgrade-gated setting:

- `oreFilterString` is the active expression and is cleared while the card is absent.
- `previousOreFilterString` is the hidden remembered expression used when the card is reinstalled.

Only the active field participates in world NBT persistence. The remembered field is initialized when a configured Storage Bus item is placed, but not when an existing placed part is loaded from world NBT. This makes the intended remove/reinsert state machine dependent on whether a reload occurred.

## Execution path

1. Player clicks the visible ore-filter button in `GuiUpgradeable.actionPerformed`.
2. The normal GUI switch opens `GuiOreFilter` / `ContainerOreFilter` for the Storage Bus.
3. Player presses Enter; `GuiOreFilter.keyTyped` sends `PacketValueConfig("OreFilter", expression)`.
4. `PacketValueConfig.serverPacketData` calls `ContainerOreFilter.setFilter` for the open container.
5. `ContainerOreFilter.setFilter` calls `PartStorageBus.setFilter`, setting both active and remembered strings.
6. World save calls `PartStorageBus.writeToNBT`, which writes only the active string.
7. World load calls `PartStorageBus.readFromNBT`, which restores only the active string.
8. Removing the card changes the upgrade inventory and invokes `PartStorageBus.upgradesChanged`, clearing the active string.
9. Reinserting the card invokes `upgradesChanged` again and copies the still-empty `previousOreFilterString` over the active string.
10. Cache reconstruction creates `OreFilteredList("")`; `MEInventoryHandler.canAccept` sees the partition as empty and permits otherwise acceptable items.

## Code evidence

- `src/main/java/appeng/client/gui/implementations/GuiUpgradeable.java`, `updateScreen` and `actionPerformed`, lines 182-183 and 280-281: the Ore Filter button is available through normal GUI interaction only while the upgrade is installed, and clicking it opens the ore-filter GUI.
- `src/main/java/appeng/client/gui/implementations/GuiOreFilter.java`, `keyTyped`, lines 142-152: pressing Enter sends the legitimate `OreFilter` value packet and returns to the parent GUI.
- `src/main/java/appeng/core/sync/packets/PacketValueConfig.java`, `serverPacketData`, lines 141-142: the server applies the value only through an open `ContainerOreFilter`.
- `src/main/java/appeng/container/implementations/ContainerOreFilter.java`, `setFilter`, lines 34-37: the server container delegates the value to the actual Storage Bus host.
- `src/main/java/appeng/parts/misc/PartStorageBus.java`, `setFilter`, lines 795-805: a normal filter edit stores the expression in both `oreFilterString` and `previousOreFilterString`, proving the latter is the intended restoration copy.
- `src/main/java/appeng/parts/misc/PartStorageBus.java`, `upgradesChanged`, lines 243-255: removing the card clears the active expression, while reinstalling it restores from `previousOreFilterString`.
- `src/main/java/appeng/parts/misc/PartStorageBus.java`, `readFromNBT` and `writeToNBT`, lines 290-311: world persistence reads and writes only `oreFilterString`; `previousOreFilterString` is omitted.
- `src/main/java/appeng/parts/misc/PartStorageBus.java`, `getInternalHandler`, lines 677-710: with the card installed, the current string becomes the handler's `OreFilteredList` partition.
- `src/main/java/appeng/util/prioitylist/OreFilteredList.java`, constructor, `isEmpty`, and `makeMatcher`, lines 89-101 and 119-152: the empty string produces a null predicate and therefore an empty partition.
- `src/main/java/appeng/me/storage/MEInventoryHandler.java`, `canAccept`, lines 219-231: an empty partition list permits insertion according to the attached inventory rather than applying an ore restriction.
- `src/main/java/appeng/core/Registration.java`, upgrade registrations near line 765: the Ore Dictionary Filter Card is explicitly supported by Storage Buses.

## Why existing validation does not prevent it

The GUI and packet path are valid and scoped to the player's open Storage Bus container. Upgrade-slot validation correctly accepts the supported card. The defect occurs later in legitimate persistence and upgrade-change handling: neither NBT loading nor `upgradesChanged` can distinguish "no remembered expression" from a genuinely blank expression. Cache reset faithfully applies the empty value, and the partition handler intentionally treats an empty list as unrestricted.

## Minimal fix direction

Persist the remembered expression independently, or reconstruct it from the active expression on world load before any card-change event can run. A robust minimal approach is to save a dedicated `previousFilter` value and load it with backward-compatible fallback to `filter`. When the card is absent, keep writing the remembered value rather than only the intentionally blank active value. Preserve the current behavior that an actual player-entered empty string clears both active and remembered configuration.

The wrench-picked item path should receive the same treatment: it currently stores the active filter only, so wrenching a bus while its card is absent can also discard the hidden remembered expression.

## Regression-test proposal

- Test setup: A `PartStorageBus` attached to a chest, with a valid Ore Dictionary Filter Card in its upgrade inventory. Use the existing HorizonQA Storage Bus game-test style in `src/main/java/appeng/gametests/automation/storagebus/StorageBusTests.java`, plus an NBT round-trip helper for the part.
- Initial state: Set the filter to `ingotIron` through `setFilter`, ensure the upgrade is installed, and serialize then deserialize the part as a world reload would.
- Triggering action: Remove the Ore Dictionary Filter Card and reinsert it after deserialization.
- Expected assertion: `getFilter()` remains `ingotIron`; matching items are accepted and a nonmatching item is rejected.
- Incorrect behavior the test must prevent: `getFilter()` becoming empty and the Storage Bus accepting the nonmatching item.
- Suggested style: Add a focused NBT round-trip test for the state restoration and a Storage Bus game test for the resulting routing behavior. Also cover the variant that saves while the card is absent.

## Runtime confirmation

Not runtime-confirmed. Static proof covers the complete GUI, persistence, upgrade-change, partition-construction, and insertion paths. Runtime testing should execute the save/reload and card remove/reinsert sequence in a client or HorizonQA game test and verify the filter text plus matching/nonmatching routing.

## Remaining uncertainty

None regarding the state loss and resulting unrestricted partition behavior. Only an end-to-end runtime observation remains outstanding.
