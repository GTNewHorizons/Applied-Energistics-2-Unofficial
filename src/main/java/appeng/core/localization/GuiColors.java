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

import appeng.api.util.AEColor;
import appeng.core.AELog;

public enum GuiColors implements Localization {

    // ARGB Colors: Name and default value

    SearchboxFocused(0x6E000000),
    SearchboxUnfocused(0x00000000),

    ItemSlotOverlayUnpowered(0x66111111),
    ItemSlotOverlayInvalid(0x66ff6666),
    ItemSlotOverlayFluidMismatch(0x66FF0000),

    CraftConfirmMissingItem(0x1AFF0000),

    CraftingCPUActive(0x5A45F021),
    CraftingCPUInactive(0x5AFFF7AA),
    CraftingCPUUnsupportedStack(0x5AE07070),
    CraftingCPUSameNetwork(0x5AE07070),
    CraftingCPUSomethingStuck(0x5AC9A53A),
    CraftingCPUNoTarget(0x5AE07070),

    InterfaceTerminalMatch(0x2A00FF00),

    CraftingPinSlotBackground(0x38E6731A),
    PlayerPinSlotBackground(0x00000000),
    CraftingDiagnosticTerminalLine(0x668C8C8C),
    CraftingDiagnosticTerminalRowHover(0x33808080),

    // RGB Colors: Name and default value
    GuiTextColorGray(0x404040),
    GuiTextColorBlack(0x000000),
    SearchboxText(0xFFFFFF),

    CraftingStatusCPUName(0x202020),
    CraftingStatusCPUStorage(0x202020),
    CraftingStatusCPUAmount(0x202020),

    CraftAmountToCraft(0xFFFFFF),

    LevelEmitterValue(0xFFFFFF),

    PriorityValue(0xFFFFFF),

    CraftConfirmPercent25(0x1c4ca6),
    CraftConfirmPercent50(0x1a751e),
    CraftConfirmPercent75(0xe3940b),
    CraftConfirmPercent100(0x660f0f),

    OreFilterTextLengthFull(0xff0000),

    NEIGrindstoneRecipeChance(0x000000),
    NEIGrindstoneNoSecondOutput(0x000000),
    NEICellView(0x000000),

    CellStatusOrange(0xFBA900),
    CellStatusRed(0xFB0000),
    CellStatusBlue(0x00AAFF),
    CellStatusGreen(0x00FF00),
    SearchHighlight(0xFFFFFF55),
    SearchGoToHighlight(0xFFFFAA00),

    ProcessBarStartColor(0xFFE60A00),
    ProcessBarMiddleColor(0xFFE6E600),
    ProcessBarEndColor(0xFF0AE600),

    ColorSelectBackground(0xFF000000),
    ColorSelectBorder(0xFFC6C6C6),

    ColorSelectBtnBg(0xFF8B8B8B),
    ColorSelectBtnBorderSelected(0xFF38de38),
    ColorSelectBtnBorderHover(0xFFFFFFFF),
    ColorSelectBtnBorder(0xFF000000),
    ColorSelectBtnBorderDisabled(0xFF555555),

    ColorSelectBtnOverlayDisabled(0xB0000000),
    ColorSelectBtnOverlayHover(0x80FFFFFF),

    ColorSelectBtnText(0xFFFFFF),

    ReshuffleStatusBeforeSnapshot(0xDDAA00),
    ReshuffleStatusAfterSnapshot(0x00AA00),
    ReshuffleStatusExtracting(0xDDAA00),
    ReshuffleStatusInjecting(0x00AA00),
    ReshuffleStatusComplete(0x0055FF),
    ReshuffleStatusFailed(0xCC0000),
    ReshuffleStatusCancelled(0xFF6600),
    ReshuffleProgressBorder(0xFF333333),
    ReshuffleProgressBackground(0xFF111111),
    ReshuffleProgressFillStart(0xFF00FFFF),
    ReshuffleProgressFillEnd(0xFF00FF00),
    ReshuffleProgressMarker(0xFFFFFFFF),
    ReshuffleScanRowHover(0x80FFFF00),

    CellHealthOk(0xFF00CC44),
    CellHealthWarn(0xFFFFAA00),
    CellHealthCrit(0xFFFF2222),
    CellHealthBarBackground(0xFF222222),

    ReshuffleReportPositive(0x00AA00),
    ReshuffleReportNegative(0xCC0000),
    ReshuffleReportDimmed(0x555555),
    ReshuffleReportHighlight(0xDDAA00),

    ReshuffleTooltipPrimary(0xFFFFFF),
    ReshuffleTooltipSecondary(0xAAAAAA),
    ReshuffleTooltipDimmed(0x555555),

    ReshuffleToggleDisabledOverlay(0x80000000),

    ColorButtonOutline(0xFF404040),

    WirelessKitGood(AEColor.Lime.mediumVariant),
    WirelessKitNeutral(AEColor.Orange.mediumVariant),
    WirelessKitBad(AEColor.Red.mediumVariant),

    NetworkVisualiserFloatingText(0xffffffff),
    NetworkVisualiserNodeMissing(0xffff0000),
    NetworkVisualiserNodeDense(0xffffff00),
    NetworkVisualiserNodeProxy(0xffffa500),
    NetworkVisualiserNodeDefault(0xff0000ff),

    NetworkVisualiserLinkCompressed(0xffff00ff),
    NetworkVisualiserLinkDense(0xffffff00),
    NetworkVisualiserLinkProxy(0xffffa500),
    NetworkVisualiserLinkDefault(0xff0000ff),

    SuperMEReplenisherInventory(0x404040),
    SuperMEReplenisherStatus(0x404040);

    private final int color;

    GuiColors() {
        this.color = 0x000000;
    }

    GuiColors(final int hex) {
        this.color = hex;
    }

    public int getColor() {
        String hex = StatCollector.translateToLocal(this.getUnlocalized());
        int color = this.color;

        if (hex.length() <= 8) {
            try {
                color = Integer.parseUnsignedInt(hex, 16);
            } catch (final NumberFormatException e) {
                AELog.warn("Couldn't format color correctly for: " + "gui.color.appliedenergistics2" + " -> " + hex);
            }
        }
        return color;
    }

    public String getUnlocalized() {
        return "gui.color.appliedenergistics2." + this;
    }
}
