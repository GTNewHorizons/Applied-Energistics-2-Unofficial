/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.core.localization;

import net.minecraft.util.StatCollector;

import appeng.core.AELog;

public enum GuiColors {

    // ARGB Colors: Name and default value
    SearchboxFocused(0x6E000000),
    SearchboxUnfocused(0x00000000),

    ItemSlotOverlayUnpowered(0x66111111),
    ItemSlotOverlayInvalid(0x66ff6666),

    CraftConfirmMissingItem(0x1AFF0000),

    CraftingCPUActive(0x5A45A021),
    CraftingCPUInactive(0x5AFFF7AA),

    InterfaceTerminalMatch(0x2A00FF00),

    // RGB Colors: Name and default value
    SearchboxText(0xFFFFFF),

    CraftingCPUTitle(0x404040),
    CraftingCPUStored(0x404040),
    CraftingCPUAmount(0x404040),
    CraftingCPUScheduled(0x404040),

    CraftingStatusCPUName(0x202020),
    CraftingStatusCPUStorage(0x202020),
    CraftingStatusCPUAmount(0x202020),

    CraftAmountToCraft(0xFFFFFF),
    CraftAmountSelectAmount(0x404040),

    LevelEmitterValue(0xFFFFFF),

    PriorityTitle(0x404040),
    PriorityValue(0xFFFFFF),

    ChestTitle(0x404040),
    ChestInventory(0x404040),

    CondenserTitle(0x404040),
    CondenserInventory(0x404040),

    CraftConfirmCraftingPlan(0x404040),
    CraftConfirmSimulation(0x404040),
    CraftConfirmFromStorage(0x404040),
    CraftConfirmPercent25(0x1c4ca6),
    CraftConfirmPercent50(0x1a751e),
    CraftConfirmPercent75(0xe3940b),
    CraftConfirmPercent100(0x660f0f),
    CraftConfirmMissing(0x404040),
    CraftConfirmToCraft(0x404040),

    CraftingTerminalTitle(0x404040),

    DriveTitle(0x404040),
    DriveInventory(0x404040),

    FormationPlaneTitle(0x404040),
    FormationPlaneInventory(0x404040),

    GrindStoneTitle(0x404040),
    GrindStoneInventory(0x404040),

    InscriberTitle(0x404040),
    InscriberInventory(0x404040),

    InterfaceTitle(0x404040),

    InterfaceTerminalTitle(0x404040),
    InterfaceTerminalInventory(0x404040),
    InterfaceTerminalName(0x404040),

    IOPortTitle(0x404040),
    IOPortInventory(0x404040),

    NetworkStatusDetails(0x404040),
    NetworkBytesDetails(0x404040),
    NetworkStatusStoredPower(0x404040),
    NetworkStatusMaxPower(0x404040),
    NetworkStatusPowerInputRate(0x404040),
    NetworkStatusPowerUsageRate(0x404040),
    NetworkStatusItemCount(0x404040),

    NetworkToolTitle(0x404040),
    AdvancedNetworkToolTitle(0x404040),
    NetworkToolInventory(0x404040),
    AdvancedNetworkToolInventory(0x404040),

    OreFilterLabel(0x404040),

    PatternTerminalTitle(0x404040),
    PatternTerminalEx(0x404040),

    QuantumLinkChamberTitle(0x404040),
    QuantumLinkChamberInventory(0x404040),

    QuartzCuttingKnifeTitle(0x404040),
    QuartzCuttingKnifeInventory(0x404040),

    RenamerTitle(0x404040),

    SecurityCardEditorTitle(0x404040),

    SkyChestTitle(0x404040),
    SkyChestInventory(0x404040),

    SpatialIOTitle(0x404040),
    SpatialIOInventory(0x404040),
    SpatialIOStoredPower(0x404040),
    SpatialIOMaxPower(0x404040),
    SpatialIORequiredPower(0x404040),
    SpatialIOEfficiency(0x404040),

    StorageBusTitle(0x404040),
    StorageBusInventory(0x404040),

    UpgradableTitle(0x404040),
    UpgradableInventory(0x404040),

    VibrationChamberTitle(0x404040),
    VibrationChamberInventory(0x404040),

    WirelessTitle(0x404040),
    WirelessInventory(0x404040),
    WirelessRange(0x404040),
    WirelessPowerUsageRate(0x404040),

    NEIGrindstoneRecipeChance(0x000000),
    NEIGrindstoneNoSecondOutput(0x000000),

    MEMonitorableTitle(0x404040),
    MEMonitorableInventory(0x404040),
    DefaultBlack(0x404040),
    WarningOrange(0xFBA900),
    WarningRed(0xFB0000);

    private final String root;
    private final int color;

    GuiColors() {
        this.root = "gui.color.appliedenergistics2";
        this.color = 0x000000;
    }

    GuiColors(final int hex) {
        this.root = "gui.color.appliedenergistics2";
        this.color = hex;
    }

    public int getColor() {
        String hex = StatCollector.translateToLocal(this.getUnlocalized());
        int color = this.color;

        if (hex.length() <= 8) {
            try {
                color = Integer.parseUnsignedInt(hex, 16);
            } catch (final NumberFormatException e) {
                AELog.warn("Couldn't format color correctly for: " + this.root + " -> " + hex);
            }
        }
        return color;
    }

    public String getUnlocalized() {
        return this.root + '.' + this.toString();
    }
}
