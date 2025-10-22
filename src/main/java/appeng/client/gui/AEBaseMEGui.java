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

import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.slots.VirtualMESlot;

public abstract class AEBaseMEGui extends AEBaseGui implements IGuiTooltipHandler {

    public AEBaseMEGui(final Container container) {
        super(container);
    }

    @Override
    public List<String> handleItemTooltip(final ItemStack stack, final int mouseX, final int mouseY,
            final List<String> currentToolTip) {
        VirtualMESlot hoveredSlot = this.getVirtualMESlotUnderMouse();
        // TODO crash when hover on fluid
        if (hoveredSlot == null || currentToolTip.isEmpty()) return currentToolTip;

        if (hoveredSlot.getAEStack() instanceof IAEFluidStack afs) {
            currentToolTip.set(0, afs.getDisplayName());
        }

        return currentToolTip;
    }

    @Override
    public ItemStack getHoveredStack() {
        VirtualMESlot hoveredSlot = this.getVirtualMESlotUnderMouse();
        if (hoveredSlot == null) return null;

        IAEStack<?> hoveredAEStack = hoveredSlot.getAEStack();
        if (hoveredAEStack == null) return null;

        return hoveredAEStack.getItemStackForNEI();
    }
}
