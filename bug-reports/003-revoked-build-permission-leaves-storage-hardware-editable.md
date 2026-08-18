# Revoked permissions leave protected GUI sessions authorized

- Report ID: BUG-003
- Status: Accepted
- Confidence: Very high
- Confirmation: Statically proven but not runtime-confirmed
- Player impact: Revoking BUILD or CRAFT does not invalidate 13 protected GUI types that were opened while the player was authorized. Depending on the screen, the revoked player can still remove storage hardware, edit Interface patterns, encode patterns, control or cancel crafting CPUs, alter wireless/spatial hardware, teleport through a Spatial Link Chamber, or mass-rewrite patterns.
- Affected mode: Multiplayer (dedicated server or integrated LAN)
- Affected version: `rv3-beta-1032-GTNH-1-g3654a842b`
- Minecraft version: 1.7.10
- Mod loader: Minecraft Forge 10.13.4.1614
- Branch: `master`
- Commit: `3654a842bf9b40e6e08ead7bb3a46e3d7b169eaf`
- Relevant subsystem: Security permission revocation and server-side GUI-container lifecycle

## Preconditions

- A powered ME network with an active Security Terminal and an ME Drive containing at least one storage cell.
- Two non-operator players: the network owner and Player B.
- A Biometric Card encoded for Player B and stored in the Security Terminal with BUILD enabled.

These are normal supported multiplayer mechanics. The primary reproduction uses only the Security Terminal buttons, Biometric Card interactions, the Drive GUI, and an ordinary inventory click. It does not require a modified client, packet timing, edited NBT, commands, or third-party blocks.

## Exact reproduction steps

1. As the network owner, encode a Biometric Card for Player B by nonsneak-right-clicking Player B with the card.
2. Open the Security Terminal, put the card in the card-editor slot, and enable BUILD using the BUILD toggle.
3. Move the edited card into the Security Terminal's stored-card list so the permission becomes active.
4. As Player B, open an ME Drive on that secured network. The normal open-time BUILD check succeeds and the Drive GUI shows its storage cells.
5. Leave Player B's Drive GUI open.
6. As the owner, extract Player B's card from the Security Terminal's stored-card list, put it in the card-editor slot, disable BUILD, and return it to the stored-card list.
7. Wait several seconds while Player B keeps the Drive GUI open. Player B should now fail to open another Drive, confirming that the security cache has applied the revocation.
8. In Player B's still-open Drive GUI, click or shift-click a populated storage-cell slot.

The same stale-session behavior can be checked with CRAFT by giving Player B CRAFT and EXTRACT, opening a Crafting Terminal or Crafting CPU, then removing only CRAFT while leaving EXTRACT enabled. The old Crafting Terminal can continue manual crafting, and the old CPU screen can still cancel or suspend a job even though a newly opened protected screen is denied.

## Expected result

Once the required permission is revoked, every already-open GUI protected by that permission should be invalidated promptly. Player B must not retain server-side actions that a fresh open-time check now denies.

## Actual predicted result

The affected containers never call AE2's periodic `verifyPermissions` helper, either directly or through a superclass. Their `isValidContainer` flag therefore remains true while the host remains present. In the primary reproduction, the normal server slot click removes the Drive's storage cell even though a current security-grid query denies BUILD.

## Player-visible symptom

In the simplest case, the revoked player takes a storage cell together with all items in it. The same omission covers these 13 permission-protected `GuiBridge` entries:

| Required permission | GUI/container | Post-revocation capability retained |
| --- | --- | --- |
| BUILD | Interface Terminal / `ContainerInterfaceTerminal` | Insert, exchange, remove, multiply, or divide encoded patterns in network Interfaces; also toggle Interface Terminal visibility. |
| BUILD | Quantum Link Chamber / `ContainerQNB` | Remove or replace the Quantum Entangled Singularity. |
| BUILD | ME Chest / `ContainerChest` | Remove or replace its storage cell. |
| BUILD | Wireless Access Point / `ContainerWireless` | Remove or replace range boosters. |
| BUILD | ME Drive / `ContainerDrive` | Remove or replace storage cells. |
| BUILD | Spatial Link Chamber / `ContainerSpatialLinkChamber` | Remove or replace its spatial cell and use the normal teleport button. |
| BUILD | Optimize Patterns / `ContainerOptimizePatterns` | Apply the selected mass rewrite directly to Interface pattern inventories. |
| CRAFT | Crafting Terminal / `ContainerCraftingTerm` | Continue manual crafting when EXTRACT remains granted. |
| CRAFT | Crafting Diagnostic Terminal / `ContainerCraftingDiagnosticTerminal` | Continue viewing live protected diagnostic data and changing its display settings. |
| CRAFT | Pattern Terminal / `ContainerPatternTerm` | Change pattern inputs/outputs and encode or retrieve patterns. |
| CRAFT | Extended Pattern Terminal / `ContainerPatternTermEx` | Perform the Pattern Terminal actions and change extended-terminal settings. |
| CRAFT | Crafting CPU / `ContainerCraftingCPU` | Cancel or suspend a job, change its allow mode, and change follow state. |
| CRAFT | Crafting Status / `ContainerCraftingStatus` | Select CPUs and invoke the inherited Crafting CPU controls. |

This table is the result of tracing every non-null `requiredPermission` entry in `GuiBridge` to its concrete container and inherited `detectAndSendChanges` chain. Other protected containers do call `verifyPermissions` and close within its 20-tick interval.

## Reachability proof

A Biometric Card is encoded through `ToolBiometricCard.itemInteractionForEntity`; nonsneak-right-clicking another player stores that player's profile. The Security Terminal exposes a restricted card-editor slot and ordinary toggle buttons. Clicking BUILD sends `PacketValueConfig("TileSecurity.ToggleOption", "BUILD")`, which is accepted only for an open `ContainerSecurity` and toggles the permission tag on that card.

Cards placed in the Security Terminal's stored-card inventory are the live permission source. Removing or inserting one calls `TileSecurity.inventoryChanged`, which posts `MENetworkSecurityChange`. `SecurityCache.updatePermissions` clears and rebuilds `playerPerms` synchronously from the stored cards, so this is not merely a stale security-cache result.

Opening any listed GUI normally calls `Platform.openGUI`; its `GuiBridge` entry declares BUILD or CRAFT and `securityCheck` consults the grid's current `ISecurityGrid`. Thus Player B can open a screen while authorized and is denied a fresh open after revocation.

After construction, however, each listed concrete container reaches `AEBaseContainer.detectAndSendChanges` without calling `verifyPermissions`. In contrast, protected containers such as `ContainerStorageBus`, `ContainerIOPort`, `ContainerInterface`, `ContainerCraftConfirm`, and `ContainerUpgradeable` explicitly recheck their required permission during synchronization. The omission is therefore specific and mechanically enumerable, not a claim that AE2 has no live permission validation.

The primary Drive action needs no special packet path. `AEBaseContainer.canInteractWith` checks `isValidContainer` and the tile's `IInventory.isUseableByPlayer`; for AE inventory tiles, useability only verifies that the same tile still occupies the coordinates. `AEBaseContainer.slotClick` delegates to vanilla server-side slot handling, and `SlotRestrictedInput.canTakeStack` checks only its `allowEdit` flag. The click reaches the Drive inventory normally.

The other entries have equally ordinary server paths. `PacketInventoryAction` dispatches crafting and Interface Terminal clicks to the player's existing `AEBaseContainer`; `PacketValueConfig` calls Crafting CPU controls when the open container is a `ContainerCraftingCPU`; `PacketSpatialAction` calls `ContainerSpatialLinkChamber.teleport`; and `PacketOptimizePatterns` calls `ContainerOptimizePatterns.optimizePatterns`. Those handlers validate the open-container type but do not reevaluate the permission that allowed the container to open.

## Root cause

`GuiBridge.requiredPermission` is used for GUI creation but is not associated with the resulting container lifecycle. Every protected container must remember to call `verifyPermissions` itself. Thirteen GUI entries omit that call throughout their inheritance chain, so a once-authorized server container retains authority indefinitely after the live security grid revokes it.

## Execution path

1. The owner encodes Player B's card through `ToolBiometricCard.itemInteractionForEntity` and edits BUILD through the Security Terminal GUI.
2. `GuiSecurity.actionPerformed` sends the legitimate `TileSecurity.ToggleOption` value packet.
3. `PacketValueConfig.serverPacketData` calls `ContainerSecurity.toggleSetting`; the card's BUILD tag is added or removed.
4. Inserting the edited card into the Security Terminal inventory calls `SecurityInventory.injectItems`, then `TileSecurity.inventoryChanged`.
5. `TileSecurity.inventoryChanged` posts `MENetworkSecurityChange`; `SecurityCache.updatePermissions` immediately rebuilds Player B's permissions.
6. Before revocation, Player B opens the Drive through `BlockDrive.onActivated` -> `Platform.openGUI` -> `GuiBridge.GUI_DRIVE.securityCheck`, which accepts BUILD and constructs `ContainerDrive`.
7. After revocation, vanilla container synchronization repeatedly calls inherited `AEBaseContainer.detectAndSendChanges`; no BUILD check changes `isValidContainer`.
8. Player B clicks the existing cell slot. `AEBaseContainer.slotClick` delegates to vanilla container handling.
9. `SlotRestrictedInput.canTakeStack` returns its unchanged `allowEdit` value, and the slot removes the cell through the Drive's internal inventory.

The other affected screens substitute a legitimate GUI action at steps 8-9, but share steps 1-7 and the same missing lifecycle authorization.

## Code evidence

- `src/main/java/appeng/items/tools/ToolBiometricCard.java`, `itemInteractionForEntity` and `encode`, lines 60-89: an ordinary player interaction encodes a card for the target player's profile.
- `src/main/java/appeng/client/gui/implementations/GuiSecurity.java`, `actionPerformed`, lines 46-75: visible permission buttons send the normal `TileSecurity.ToggleOption` packet.
- `src/main/java/appeng/container/implementations/ContainerSecurity.java`, `toggleSetting`, lines 72-83: the server changes the selected permission on the editor card.
- `src/main/java/appeng/me/storage/SecurityInventory.java`, `injectItems` and `extractItems`, lines 40-86: stored-card changes call `TileSecurity.inventoryChanged` after mutation.
- `src/main/java/appeng/tile/misc/TileSecurity.java`, `inventoryChanged` and `readPermissions`, lines 175-181 and 263-278: each stored-card change posts the security event, and permissions are read from the cards.
- `src/main/java/appeng/me/cache/SecurityCache.java`, `updatePermissions` and `hasPermission`, lines 51-59 and 120-149: the event rebuilds permission state, and subsequent checks deny a removed permission.
- `src/main/java/appeng/core/sync/GuiBridge.java`, lines 154-167, 173-193, 227-228, and 250-254: all 13 listed GUI entries declare BUILD or CRAFT as an open-time requirement.
- `src/main/java/appeng/core/sync/GuiBridge.java`, `securityCheck`, lines 551-574: GUI creation consults the current security grid.
- `src/main/java/appeng/container/AEBaseContainer.java`, `verifyPermissions`, lines 294-330: the existing helper rechecks access every 20 ticks and invalidates the container, but only when invoked by a subclass.
- `src/main/java/appeng/container/implementations/ContainerDrive.java`, lines 19-38; `ContainerChest.java`, lines 19-37; and `ContainerQNB.java`, lines 19-34: editable hardware slots are added without any synchronization-time permission check.
- `src/main/java/appeng/container/implementations/ContainerWireless.java`, lines 22-58, and `ContainerSpatialLinkChamber.java`, lines 10-30: more BUILD-protected inventory/action containers synchronize without a BUILD check.
- `src/main/java/appeng/container/implementations/ContainerInterfaceTerminal.java`, `detectAndSendChanges` and `doAction`, lines 98-226: the live terminal mutates Interface pattern inventories without a BUILD recheck.
- `src/main/java/appeng/container/implementations/ContainerOptimizePatterns.java`, `optimizePatterns`, lines 143-205: the server directly rewrites pattern inventories without a BUILD recheck.
- `src/main/java/appeng/container/implementations/ContainerCraftingTerm.java`, class declaration; `ContainerPatternTerm.java`, `encode`, `craftOrGetItem`, and `detectAndSendChanges`, lines 68 and 285-427; and `ContainerPatternTermEx.java`, lines 16-56: CRAFT-protected terminal chains end in the generic base synchronization without a CRAFT check.
- `src/main/java/appeng/container/implementations/ContainerCraftingCPU.java`, `cancelCrafting`, `detectAndSendChanges`, `changeAllowMode`, and `suspendCrafting`, lines 135-238 and 311-328: server-side CPU mutation remains callable without CRAFT validation. `ContainerCraftingStatus` inherits the same omission.
- `src/main/java/appeng/container/implementations/ContainerCraftingDiagnosticTerminal.java`, `detectAndSendChanges`, lines 84-101: even the read-oriented CRAFT-protected diagnostic session is not invalidated.
- `src/main/java/appeng/container/implementations/ContainerStorageBus.java`, near line 142, and `ContainerUpgradeable.java`, lines 168-171: comparable BUILD-protected containers explicitly invoke the helper, demonstrating intended revocation behavior.
- `src/main/java/appeng/container/implementations/ContainerCraftConfirm.java`, near line 269: a comparable CRAFT-protected container explicitly invokes the helper.
- `src/main/java/appeng/container/AEBaseContainer.java`, `detectAndSendChanges`, lines 429-450, and `canInteractWith`, lines 689-698: the generic lifecycle performs synchronization and tile validation but no security query.
- `src/main/java/appeng/core/sync/packets/PacketValueConfig.java`, lines 117-124; `PacketSpatialAction.java`, lines 25-29; and `PacketOptimizePatterns.java`, lines 47-51: legitimate actions trust the still-open concrete container and do not recheck its required permission.

## Why existing validation does not prevent it

The open-time check works correctly and is part of the reproduction: Player B must possess BUILD to open the first Drive and is denied a newly opened one after revocation. The live security cache also updates correctly. The missing link is reevaluating the existing server container.

Minecraft's normal `canInteractWith` polling cannot compensate because AE2's generic container/tile validation does not know which permission the GUI required. Slot restrictions validate item types and edit flags, not current network authorization. Network storage actions may separately enforce INJECT or EXTRACT, but that neither restores the revoked CRAFT/BUILD check nor protects direct host inventories and control methods.

## Minimal fix direction

Prefer propagating each `GuiBridge` entry's `requiredPermission` into the server container and validating it generically during `AEBaseContainer.detectAndSendChanges`. That closes the complete enumerated surface and prevents future protected GUIs from silently omitting lifecycle checks.

If a generic association is too invasive, add the matching `verifyPermissions(..., false)` call to all 13 affected concrete inheritance chains. Do not add it only to Drive/Chest/QNB: that would leave the other ordinary post-revocation actions authorized. Client-side disabling alone is insufficient; the server container must become invalid.

## Regression-test proposal

- Test setup: Create a powered secured grid with an owner and Player B. Give Player B the relevant permission through an encoded Biometric Card and open each protected container through its normal GUI path.
- Initial state: Assert the container is valid, remove its required permission from Player B's stored card, trigger the Security Terminal inventory update, and assert a fresh open is denied.
- Triggering action: Run at least 20 server container ticks, then attempt one representative normal action: remove a Drive cell for BUILD and cancel a crafting job for CRAFT.
- Expected assertion: The existing container is invalid/closed, the Drive cell remains installed, the craft is not cancelled, and no protected action is dispatched.
- Incorrect behavior the test must prevent: A container opened under an old permission remaining valid and mutating its host after the current security grid denies that permission.
- Suggested style: Add a parameterized container-lifecycle test covering every non-null `GuiBridge.requiredPermission` entry, with explicit checks that each constructed container either invokes generic enforcement or its own matching `verifyPermissions` path. Add focused action assertions for Drive, Interface Terminal, Crafting CPU, Spatial Link Chamber, and Optimize Patterns.

## Runtime confirmation

Not runtime-confirmed. Static proof covers the supported Biometric Card workflow, legitimate Security Terminal packets, immediate live-cache update, normal GUI open-time authorization, every protected container's inheritance chain, and representative post-revocation mutation paths. A two-client server session should confirm the primary Drive transfer and one CRAFT control action.

## Remaining uncertainty

None regarding the missing rechecks or the primary server-side Drive mutation. Runtime confirmation remains useful for exact client presentation after the server invalidates a fixed container and for validating the full 13-entry regression matrix.
