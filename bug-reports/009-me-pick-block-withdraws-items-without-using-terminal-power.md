# ME Pick Block withdraws items without using Wireless Terminal power

- Report ID: BUG-009
- Status: Accepted
- Confidence: Very high
- Confirmation: Statically proven but not runtime-confirmed
- Player impact: A player can repeatedly withdraw full stacks from an ME network through the supported ME Pick Block key while the linked Wireless Terminal consumes no energy for either the item transfer or wireless use. Any terminal with at least 0.5 AE can perform unlimited Pick Block withdrawals until its charge changes through some other action.
- Affected mode: Both
- Affected version: `rv3-beta-1032-GTNH-1-g3654a842b`
- Minecraft version: 1.7.10
- Mod loader: Minecraft Forge 10.13.4.1614
- Branch: `master`
- Commit: `3654a842bf9b40e6e08ead7bb3a46e3d7b169eaf`
- Relevant subsystem: ME Pick Block packet, wireless range/power validation, and powered storage extraction

## Preconditions

- A powered ME network with an active Wireless Access Point and at least several stacks of an ordinary placeable block, such as Stone.
- A standard Wireless Terminal linked to that network and holding at least 0.5 AE of charge.
- The player is in survival mode, within Wireless Access Point range, and has EXTRACT permission if the network is secured.
- The client-side ME Pick Block key is assigned in Controls. It may be bound to the vanilla Pick Block button or a separate key.
- At least one empty player-inventory slot and no full stack of the target block.

Every precondition is a supported AE2 or vanilla gameplay setting. The reproduction uses the client feature's own packet and requires no altered packet contents, NBT editing, commands, or timing.

## Exact reproduction steps

1. Store at least 256 Stone in the ME network and activate a Wireless Access Point.
2. Link a normal Wireless Terminal to the network, charge it, and keep it anywhere in the main inventory or supported Baubles inventory.
3. In Controls, bind **ME Pick Block** to an available key (or to the vanilla Pick Block button).
4. Record the Wireless Terminal's current charge from its normal power tooltip/bar.
5. In survival mode and within range, look at a placed Stone block while no GUI is open and press the ME Pick Block key.
6. Observe that the server withdraws enough Stone from the ME network to make a full inventory stack and selects it in the hotbar.
7. Drop or place that stack so the inventory again has no full Stone stack.
8. Repeat steps 5-7 several times, then inspect the Wireless Terminal's charge again.

For a sharper boundary test, first drain the terminal to a charge below the normal cost of extracting 64 item units but at or above 0.5 AE. Pick Block still withdraws the full stack because the server tests only the 0.5-AE threshold and never charges the actual operation.

## Expected result

ME Pick Block is a wireless storage extraction and should be limited by and consume Wireless Terminal energy just like extracting the same items through the Wireless Terminal GUI. A full 64-item request should be capped to affordable units and charge the energy source for the amount actually removed, in addition to any intended wireless-distance cost.

## Actual predicted result

The server verifies that the terminal is linked, in range, and has 0.5 AE, then obtains the network's item monitor and calls `extractItems(..., MODULATE, PlayerSource)` directly. It never calls `IWirelessTermHandler.usePower`, `IEnergySource.extractAEPower`, or `Platform.poweredExtraction`. The full requested stack enters the player's inventory while terminal charge remains unchanged.

## Player-visible symptom

The player can use ME Pick Block as a free remote item-withdrawal mechanism. Repeating the action can move an arbitrary number of block stacks out of the network without discharging the Wireless Terminal. A terminal just above the 0.5-AE availability threshold continues to withdraw full stacks even though the normal wireless terminal path would be unable to fund them.

## Reachability proof

`KeyBindHandler` is registered for ordinary key and mouse input. In survival mode with no GUI open, it reads the block currently under the crosshair through vanilla `Block.getPickBlock`, constructs `PacketPickBlock`, and sends it to the server. If the ME and vanilla Pick Block bindings are identical, `ClientHelper.onPickBlockEvent` deliberately routes the normal vanilla event into the same handler. The packet contents therefore come directly from the supported client action.

On the server, `PacketPickBlock` consolidates existing partial stacks and calculates the exact amount needed to reach the target block's maximum stack size. It finds the first registered Wireless Terminal in the player's inventory or Baubles slots and calls `WirelessRegistry.performCheck`.

`performCheck` validates the registered terminal handler, encryption key, locatable Security Terminal, active in-range Wireless Access Point, and a minimum charge of 0.5 AE. This rejects unlinked, out-of-range, inactive, and truly empty terminals. It is only a predicate: its successful path returns `true` without invoking `usePower`.

`PacketPickBlock.getWirelessItemInventory` then follows the encryption key to the linked grid's `IStorageGrid` and returns its item monitor. The packet constructs a normal `PlayerSource` and calls that monitor's modulated extraction directly. `NetworkInventoryHandler.extractItems` enforces EXTRACT against the player, so the report does not rely on bypassing network security; after that guard it removes the requested items without any energy API involvement.

The normal terminal path establishes the intended contrast. `ContainerMEMonitorable` uses `Platform.poweredExtraction`, which first simulates available energy, caps the request, extracts, and modulates the energy source for the actual amount. For a `WirelessTerminalGuiObject`, `extractAEPower` delegates simulation to `hasPower` and modulation to `IWirelessTermHandler.usePower`. An open portable container also drains its wireless-distance cost periodically. None of those paths is constructed or called by `PacketPickBlock`.

## Root cause

The Pick Block implementation treats `WirelessRegistry.performCheck` as if it paid for wireless access and item movement. That method only checks for 0.5 AE. The packet then bypasses the powered extraction abstraction and mutates the storage monitor directly, leaving the Wireless Terminal out of the transaction entirely.

## Execution path

1. The survival player presses the configured ME Pick Block key while looking at Stone.
2. `KeyBindHandler.handlePickBlock` obtains the legitimate picked-block stack and sends `PacketPickBlock`.
3. `PacketPickBlock.serverPacketData` calculates the missing quantity needed for a full stack.
4. `PlayerInventoryUtil.getFirstWirelessTerminal` finds the linked terminal in main inventory or Baubles.
5. `PacketPickBlock.getWirelessItemInventory` calls `WirelessRegistry.performCheck`.
6. `performCheck` proves link, range, active access point, and at least 0.5 AE, then returns without consuming charge.
7. The packet resolves the linked grid's `IStorageGrid` and item monitor.
8. `NetworkInventoryHandler.extractItems` accepts the normal `PlayerSource`, verifies EXTRACT, and removes up to the requested block count.
9. `PacketPickBlock` inserts the extracted `ItemStack` into the player's inventory and selects it.
10. No call in this path reaches `ToolWirelessTerminal.usePower` or any other modulated energy extraction.

## Code evidence

- `src/main/java/appeng/client/KeyBindHandler.java`, `handlePickBlock`, lines 48-83: the supported survival keybind derives a real block pick result and sends the packet while no GUI is open.
- `src/main/java/appeng/client/ClientHelper.java`, `onPickBlockEvent`, lines 463-477: binding ME Pick Block to vanilla Pick Block intentionally routes the ordinary event to the same feature.
- `src/main/java/appeng/core/sync/packets/PacketPickBlock.java`, `serverPacketData`, lines 48-168: the server calculates a full-stack request and directly calls `wirelessInventory.extractItems(..., MODULATE, source)` before placing the result in the player inventory.
- `src/main/java/appeng/core/sync/packets/PacketPickBlock.java`, `getWirelessItemInventory`, lines 171-213: range/power validation is followed by direct lookup of the linked grid's item inventory; no energy source is returned.
- `src/main/java/appeng/util/PlayerInventoryUtil.java`, `getFirstWirelessTerminal`, lines 120-179: a normal terminal in inventory or the supported Baubles slot makes the feature reachable.
- `src/main/java/appeng/core/features/registries/WirelessRegistry.java`, `performCheck`, lines 135-170: the only power logic is `hasPower(player, 0.5, item)`; success returns without `usePower`.
- `src/main/java/appeng/items/tools/powered/ToolWirelessTerminal.java`, `usePower` and `hasPower`, lines 102-110: checking charge and consuming charge are separate operations, and only the latter mutates the battery.
- `src/main/java/appeng/me/storage/NetworkInventoryHandler.java`, `extractItems`, lines 311-343, and `testPermission`, lines 265-290: direct monitor extraction enforces EXTRACT but performs no power accounting.
- `src/main/java/appeng/util/Platform.java`, `poweredExtraction`, lines 1293-1335: the normal storage helper caps an extraction to available power and then charges for the amount actually extracted.
- `src/main/java/appeng/helpers/WirelessTerminalGuiObject.java`, `extractAEPower`, lines 144-153: normal wireless extraction delegates modulated power use to the terminal handler.
- `src/main/java/appeng/container/AEBaseContainer.java`, `portableSourceTick`, lines 1192-1215: an open Wireless Terminal container additionally applies range-dependent idle drain, which the one-shot Pick Block path never enters.

## Why existing validation does not prevent it

`performCheck` is functioning as written: it prevents use with no link, no active access point, out of range, or less than 0.5 AE. It neither promises nor performs payment for the subsequent operation. Repeating it does not lower the stored charge, so passing once means passing indefinitely while no other feature drains the item.

The network storage monitor's security guard only answers whether the player may extract; it has no reference to the Wireless Terminal and cannot charge it. Network power and channel state keep the grid operational but also do not represent the portable terminal's battery.

The normal portable-container tick cannot run because ME Pick Block explicitly requires no GUI to be open and never creates a `WirelessTerminalGuiObject` or container. Client-side item-bar updates cannot reveal a charge change that the server never makes.

## Minimal fix direction

Route Pick Block withdrawal through the same powered-extraction semantics as Wireless Terminal GUI actions. Resolve a server-side wireless energy source for the exact terminal stack, simulate the affordable amount, call `Platform.poweredExtraction`, and charge the terminal for the quantity actually extracted. Preserve `performCheck` for link/range/access-point validation and preserve `PlayerSource` so EXTRACT authorization and extraction statistics still work.

If the feature intentionally has a distinct one-shot wireless access fee, define and modulate that fee explicitly in addition to item-transfer power. Do not merely call `usePower(0.5)` after an unrestricted extraction: that would still allow a nearly empty terminal to withdraw a full stack and could remove items before failed payment.

## Regression-test proposal

- Test setup: Create a linked, in-range standard Wireless Terminal with a known finite charge and a secured grid containing 128 Stone; grant the player EXTRACT. Give the player an empty inventory slot.
- Initial state: Record exact terminal charge and assert the grid contains 128 Stone.
- Triggering action: Invoke the server-side Pick Block path for Stone using the ordinary packet produced by `KeyBindHandler`.
- Expected assertion: At most the power-affordable amount is removed, the exact corresponding charge is deducted, the player receives that amount, and storage plus player counts remain conserved.
- Incorrect behavior the test must prevent: Receiving 64 Stone while terminal charge is unchanged or while the terminal cannot fund 64 item units.
- Suggested style: Add a focused packet/game test alongside wireless terminal range/security tests, with cases for adequate power, charge between 0.5 and a full-stack cost, zero power, out of range, and denied EXTRACT.

## Runtime confirmation

Not runtime-confirmed. Static proof covers the normal keybind/event entry point, legitimate packet creation, server terminal discovery, complete range/link/minimum-charge validation, secure monitor extraction, inventory delivery, and the absence of every modulated power-consumption path. A client/server run should record the exact charge tooltip before and after several stack withdrawals.

## Remaining uncertainty

The intended fixed cost for a one-shot ME Pick Block operation is not documented in this code. That does not affect the defect: the current successful path consumes exactly zero energy regardless of item count or distance, while all neighboring wireless/storage paths explicitly modulate an energy source.
