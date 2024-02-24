/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.api.config.TerminalFontSize;
import appeng.api.storage.data.IAEItemStack;
import appeng.client.me.SlotME;
import appeng.container.slot.SlotFake;
import appeng.core.AEConfig;
import appeng.core.localization.ButtonToolTips;
import appeng.util.Platform;

public abstract class AEBaseMEGui extends AEBaseGui implements IGuiTooltipHandler {

    public AEBaseMEGui(final Container container) {
        super(container);
    }

    @Override
    public List<String> handleItemTooltip(final ItemStack stack, final int mouseX, final int mouseY,
            final List<String> currentToolTip) {
        if (stack != null) {
            final Slot s = this.getSlot(mouseX, mouseY);
            final boolean isSlotME = s instanceof SlotME;
            if (isSlotME || s instanceof SlotFake) {
                final int BigNumber = AEConfig.instance.getTerminalFontSize() == TerminalFontSize.SMALL ? 9999 : 999;

                IAEItemStack myStack = null;

                try {
                    myStack = Platform.getAEStackInSlot(s);
                } catch (final Throwable ignore) {}

                if (myStack != null) {
                    if (myStack.getStackSize() > BigNumber || (myStack.getStackSize() > 1 && stack.isItemDamaged())) {
                        final String local = isSlotME ? ButtonToolTips.ItemsStored.getLocal()
                                : ButtonToolTips.ItemCount.getLocal();
                        final String formattedAmount = NumberFormat.getNumberInstance(Locale.US)
                                .format(myStack.getStackSize());
                        final String format = String.format(local, formattedAmount);

                        currentToolTip.add("\u00a77" + format);
                    }

                    if (myStack.getCountRequestable() > 0) {
                        final String local = ButtonToolTips.ItemsRequestable.getLocal();
                        final String formattedAmount = NumberFormat.getNumberInstance(Locale.US)
                                .format(myStack.getCountRequestable());
                        final String format = String.format(local, formattedAmount);

                        currentToolTip.add("\u00a77" + format);
                    }
                } else if (stack.stackSize > BigNumber || (stack.stackSize > 1 && stack.isItemDamaged())) {
                    final String local = ButtonToolTips.ItemsStored.getLocal();
                    final String formattedAmount = NumberFormat.getNumberInstance(Locale.US).format(stack.stackSize);
                    final String format = String.format(local, formattedAmount);

                    currentToolTip.add("\u00a77" + format);
                }
            }
        }
        return currentToolTip;
    }

    // Vanilla version...
    // protected void drawItemStackTooltip(ItemStack stack, int x, int y)
    @Override
    public void renderToolTip(final ItemStack stack, final int x, final int y) {
        final Slot s = this.getSlot(x, y);
        if ((s instanceof SlotME || s instanceof SlotFake) && stack != null) {
            final int BigNumber = AEConfig.instance.getTerminalFontSize() == TerminalFontSize.SMALL ? 9999 : 999;

            IAEItemStack myStack = null;

            try {
                myStack = Platform.getAEStackInSlot(s);
            } catch (final Throwable ignore) {}

            if (myStack != null) {
                @SuppressWarnings("unchecked")
                final List<String> currentToolTip = stack
                        .getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);

                if (myStack.getStackSize() > BigNumber || (myStack.getStackSize() > 1 && stack.isItemDamaged())) {
                    currentToolTip.add(
                            "Items Stored: "
                                    + NumberFormat.getNumberInstance(Locale.US).format(myStack.getStackSize()));
                }

                if (myStack.getCountRequestable() > 0) {
                    currentToolTip.add(
                            "Items Requestable: "
                                    + NumberFormat.getNumberInstance(Locale.US).format(myStack.getCountRequestable()));
                }

                this.drawTooltip(x, y, 0, join(currentToolTip, "\n"));
            } else if (stack.stackSize > BigNumber) {
                final List<String> var4 = stack
                        .getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);
                var4.add("Items Stored: " + NumberFormat.getNumberInstance(Locale.US).format(stack.stackSize));
                this.drawTooltip(x, y, 0, join(var4, "\n"));
                return;
            }
        }
        super.renderToolTip(stack, x, y);
        // super.drawItemStackTooltip( stack, x, y );
    }
}
