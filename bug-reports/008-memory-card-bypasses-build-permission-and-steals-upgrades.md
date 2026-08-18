# Memory Card bypasses BUILD permission and removes automation upgrades

- Report ID: BUG-008
- Status: Accepted
- Confidence: Very high
- Confirmation: Statically proven but not runtime-confirmed
- Player impact: A player denied BUILD can overwrite protected automation-part settings and use a blank Memory Card profile to remove every installed upgrade from the target part; the removed cards are put into that unauthorized player's inventory or dropped for them.
- Affected mode: Multiplayer (dedicated server or integrated LAN)
- Affected version: `rv3-beta-1032-GTNH-1-g3654a842b`
- Minecraft version: 1.7.10
- Mod loader: Minecraft Forge 10.13.4.1614
- Branch: `master`
- Commit: `3654a842bf9b40e6e08ead7bb3a46e3d7b169eaf`
- Relevant subsystem: Part interaction, Memory Card configuration transfer, and ME security BUILD authorization

## Preconditions

- A powered ME network with an active Security Terminal.
- An Export Bus on that network with one or more upgrade cards installed. Speed Cards make the inventory transfer especially easy to observe.
- Two normal players: the network owner and Player B.
- Player B is denied BUILD on the secured network.
- Player B has an ordinary Memory Card and can physically right-click the Export Bus.

The reproduction uses only documented in-world parts, a Security Terminal, a Memory Card, and normal right-clicks. It requires no modified client, forged packet, edited NBT, race, command, or third-party inventory.

## Exact reproduction steps

1. As the owner, build and power an ME network with a Security Terminal and an Export Bus.
2. Install one or more Speed Cards in the Export Bus and configure it to export a recognizable item, such as diamonds.
3. Configure the Security Terminal so Player B does not have BUILD permission.
4. As Player B, right-click the protected Export Bus with an empty hand and confirm its normal GUI is denied. This establishes that the current security grid denies BUILD.
5. On a separate network controlled by Player B, place a second Export Bus with no upgrades and leave its filter empty.
6. Sneak-right-click that blank Export Bus with a Memory Card. The normal saved profile contains the Export Bus settings and an `upgradesList` made of empty slots.
7. Stop sneaking and right-click the protected, upgraded Export Bus with that Memory Card.
8. Inspect Player B's inventory and the protected Export Bus.

The settings-only form can be reproduced by saving any desired Export Bus filter/settings on Player B's bus and applying that profile in step 7.

## Expected result

The protected part should reject both reading and applying Memory Card settings when the interacting player lacks BUILD. Its filter, operational settings, priority, and installed upgrades should remain unchanged, and Player B should receive none of its contents.

## Actual predicted result

The server accepts the normal part interaction without consulting the target part's ME security grid. Applying the blank profile removes every installed upgrade from the protected Export Bus. Because the profile requests no replacements, the removed cards are inserted into Player B's Network Tool or inventory, or dropped at Player B if those inventories are full. The saved blank filter and other settings are then applied to the bus.

## Player-visible symptom

Player B receives the owner's Speed Cards despite being unable to open the Export Bus GUI. The bus stops or changes its automation according to the unauthorized profile. A nonblank profile can also change its item filter, redstone mode, scheduling/craft-only settings, fuzzy/ore behavior, priority, and any other state exported by the part's standard Memory Card support.

This is not limited to Export Buses. `AEBasePart` enables the standard Memory Card path by default, and only P2P tunnels opt out. Any standard-memory-card part that exposes a config manager, config inventory, priority, ore filter, or `UpgradeInventory` can have those corresponding fields changed without the permission required by its GUI. The Export Bus is the primary reproduction because its upgrade theft and automation change are immediate and unambiguous.

## Reachability proof

The client reaches this code through the ordinary cable-bus interaction. `PacketPartInteraction.process` resolves the clicked part, fires Forge's normal `RIGHT_CLICK_BLOCK` event, and invokes `IPart.onActivate` or `onShiftActivate`. That event permits protection mods to cancel a world interaction, but it is not an AE security permission query.

`AEBasePart.onActivate` and `onShiftActivate` call `useMemoryCard` before the part-specific activation that would open its protected GUI. `useMemoryCard` checks only that the held item implements `IMemoryCard`, standard support is enabled, and `ForgeEventFactory.onItemUseStart` does not cancel. It never resolves the part's grid or asks `ISecurityGrid` for BUILD.

Saving a blank Export Bus works normally. `downloadSettings` writes its config-manager state, config inventory, priority/filter where applicable, and `ToolMemoryCard.setUpgradesInfo` always writes one compound per upgrade slot. Empty slots therefore produce a present `upgradesList` containing empty compounds.

Applying the profile first sees that the stored and target unlocalized names both identify an Export Bus. Because `upgradesList` exists, `ToolMemoryCard.insertUpgrades` runs before settings are uploaded. It immediately extracts every existing target upgrade into a local list. Each empty requested compound deserializes to `null`, so none is put back. The `finally` block then routes all unmatched existing upgrades to the interacting player's Network Tools, ordinary inventory, or world drop. `AEBasePart.uploadSettings` subsequently applies the saved configuration directly to the target part.

By contrast, opening the Export Bus GUI uses `GuiBridge.GUI_BUS`, which declares `SecurityPermissions.BUILD`, and `GuiBridge.securityCheck` consults the current security grid before constructing the container. The primary reproduction therefore uses a target that demonstrably denies the player through the intended UI while the Memory Card path mutates the same protected state.

## Root cause

Standard Memory Card handling is implemented as a privileged mutation in the shared `AEBasePart` activation wrapper, outside `GuiBridge` and container authorization. The wrapper performs no AE security check before `downloadSettings`, `insertUpgrades`, or `uploadSettings`.

The upgrade-copy implementation compounds the authorization omission: synchronizing a profile is defined to take all existing upgrades out of the target and return unmatched ones to the interacting player. That behavior is reasonable for an authorized owner, but it turns the missing BUILD check into direct item theft.

## Execution path

1. Player B right-clicks the protected cable-bus host with the ordinary Memory Card.
2. The client sends the standard `PacketPartInteraction` generated by normal part use.
3. `PacketPartInteraction.process` fires the Forge interaction event and calls `PartExportBus.onActivate` through `AEBasePart.onActivate`.
4. `AEBasePart.onActivate` calls `useMemoryCard` before `PartSharedItemBus.onPartActivate` can open `GUI_BUS`.
5. `useMemoryCard` validates the item/profile name but performs no `ISecurityGrid.hasPermission(player, BUILD)` call.
6. The present `upgradesList` causes `ToolMemoryCard.insertUpgrades` to extract all installed target upgrades.
7. Empty profile entries request no upgrades, so the method's `finally` block calls `Platform.addToPlayerInvOrDrop` for every extracted Speed Card.
8. `AEBasePart.uploadSettings` reads the blank profile into the target bus's config manager and config inventory.
9. The protected bus saves and operates with the unauthorized state, while Player B owns the removed cards.

## Code evidence

- `src/main/java/appeng/core/sync/packets/PacketPartInteraction.java`, `process`, lines 57-115: a legitimate part-use packet resolves the server part, checks only the Forge interaction event, and invokes the part activation method.
- `src/main/java/appeng/parts/AEBasePart.java`, `onActivate` and `onShiftActivate`, lines 482-495: shared Memory Card handling runs before the part-specific activation path.
- `src/main/java/appeng/parts/AEBasePart.java`, `useMemoryCard`, lines 413-460: the method reads or writes settings and invokes upgrade synchronization without any ME security-grid lookup.
- `src/main/java/appeng/parts/AEBasePart.java`, `downloadSettings` and `uploadSettings`, lines 320-406: Memory Cards transfer the config manager, priority, config inventory, and ore filter directly.
- `src/main/java/appeng/parts/automation/PartUpgradeable.java`, `getInventoryByName`, lines 115-125: upgradeable automation parts expose their live `UpgradeInventory` to the shared card handler.
- `src/main/java/appeng/items/tools/ToolMemoryCard.java`, `setUpgradesInfo`, lines 155-168: a saved profile includes an entry for every upgrade slot, including empty compounds for empty slots.
- `src/main/java/appeng/items/tools/ToolMemoryCard.java`, `insertUpgrades` and `takeExistingUpgrades`, lines 170-221: applying a profile first removes all existing target upgrades; unrequested cards are then sent to the interacting player with `Platform.addToPlayerInvOrDrop`.
- `src/main/java/appeng/parts/automation/PartSharedItemBus.java`, `onPartActivate`, lines 222-236: ordinary empty-hand activation would instead open the protected Bus GUI.
- `src/main/java/appeng/core/sync/GuiBridge.java`, `GUI_BUS` and `securityCheck`, lines 201 and 551-574: that GUI requires BUILD and checks the target network's live security cache.
- `src/main/java/appeng/api/config/SecurityPermissions.java`, BUILD documentation, lines 39-43: BUILD is explicitly required to modify automation or network layout.
- `src/main/java/appeng/items/tools/ToolPriorityCard.java`, `securityCheck`, lines 122-136: the comparable direct-use configuration tool explicitly checks BUILD, demonstrating the intended authorization pattern outside a GUI.
- `src/main/java/appeng/parts/p2p/PartP2PTunnel.java`, `useStandardMemoryCard`, lines 221-225: P2P tunnels deliberately opt out, while the shared default in `AEBasePart` is enabled.

## Why existing validation does not prevent it

The target's normal GUI security is not reached because the shared Memory Card handler consumes the activation first. `ContainerUpgradeable.verifyPermissions` can protect an already opened bus container, but no container is created for this action.

Forge's interaction event is not a substitute for AE security. It represents general world/protection cancellation and does not know Player B's Biometric Card permissions. The reproduction can occur in an otherwise unprotected survival world where the ME Security Terminal is the intended access-control mechanism.

Network inventory authorization also cannot help. The exploit mutates the part's own config and upgrade inventories directly; it does not inject into or extract from the ME storage monitor through a `PlayerSource`.

The Memory Card's item-use hook only permits Forge to cancel use. Its stored profile is produced by ordinary in-game interaction, so no malformed or player-edited NBT is involved.

## Minimal fix direction

Before either saving or applying standard Memory Card data, resolve the target part's actionable node and require `SecurityPermissions.BUILD` from its current `ISecurityGrid`, following the server-side check already used by `ToolPriorityCard`. Reject the interaction before calling `downloadSettings`, `insertUpgrades`, or `uploadSettings` if authorization fails.

Keep the check server authoritative. Applying it only in client interaction/render code would still allow the ordinary server handler to mutate protected state. Prefer a shared helper for direct configuration tools so Memory Card, Priority Card, and future non-GUI tools cannot diverge in authorization behavior.

## Regression-test proposal

- Test setup: Create a powered secured grid with an Export Bus containing four Speed Cards and a diamond filter. Give Player B no BUILD permission. On a separate player-owned Export Bus, save a blank configuration to an ordinary Memory Card.
- Initial state: Assert that Player B cannot open the target Bus GUI, the target has four Speed Cards and the diamond filter, and Player B owns no Speed Cards.
- Triggering action: Invoke the same server-side part activation used by a normal nonsneak right-click while Player B holds the saved Memory Card.
- Expected assertion: The activation is rejected or reports a permission failure; the target still has all four upgrades and its original filter; Player B's inventories and nearby drops contain no Speed Cards.
- Incorrect behavior the test must prevent: A BUILD-denied player receiving target upgrades or changing target automation through Memory Card use.
- Suggested style: Add a focused cable-bus interaction game test for both save and load denial, plus a parameterized unit test over representative standard-memory-card hosts (Export Bus, Storage Bus, Level Emitter, Formation Plane, and Interface) to ensure direct configuration requires the same permission as their GUI.

## Runtime confirmation

Not runtime-confirmed. Static proof covers the legitimate interaction packet, server part dispatch, absence of an AE security lookup, deterministic blank-profile encoding, target upgrade extraction, player refund, settings upload, and the contrasting BUILD-protected GUI/tool paths.

## Remaining uncertainty

None regarding server reachability, the missing BUILD check, or ownership transfer of unmatched upgrades. Runtime confirmation remains useful for recording the exact chat message and whether a full Player B inventory leaves the returned cards in a Network Tool or drops them at the player's position.
