/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.container.implementations;

import java.util.List;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.api.storage.ITerminalHost;
import appeng.client.gui.IGuiSub;
import appeng.container.PrimaryGui;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IContainerSubGui;
import appeng.container.interfaces.ICraftingCPUSelectorContainer;
import appeng.container.slot.SlotInaccessible;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ContainerCraftingStatus extends ContainerCraftingCPU
        implements ICraftingCPUSelectorContainer, IContainerSubGui {

    @GuiSync.Recurse(5)
    public ContainerCPUTable cpuTable;

    public ContainerCraftingStatus(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
        cpuTable = new ContainerCPUTable(this, this::setCPU, true, c -> true);

        // sub gui copy paste
        this.primaryGuiButtonIcon = new SlotInaccessible(new AppEngInternalInventory(null, 1), 0, 0, -9000);
        this.addSlotToContainer(this.primaryGuiButtonIcon);
    }

    public ContainerCPUTable getCPUTable() {
        return cpuTable;
    }

    @Override
    public void detectAndSendChanges() {
        cpuTable.detectAndSendChanges(getNetwork(), crafters);
        super.detectAndSendChanges();
    }

    @Override
    public void selectCPU(int serial) {
        cpuTable.selectCPU(serial);
    }

    public List<CraftingCPUStatus> getCPUs() {
        return cpuTable.getCPUs();
    }

    // sub gui copy paste
    private final Slot primaryGuiButtonIcon;

    @SideOnly(Side.CLIENT)
    private IGuiSub guiLink;

    @Override
    public void onSlotChange(Slot s) {
        if (Platform.isClient() && this.primaryGuiButtonIcon == s && this.primaryGuiButtonIcon.getHasStack()) {
            this.guiLink.initPrimaryGuiButton();
        }
    }

    @Override
    public void setPrimaryGui(PrimaryGui primaryGui) {
        super.setPrimaryGui(primaryGui);
        this.primaryGuiButtonIcon.putStack(primaryGui.getIcon());
    }

    @SideOnly(Side.CLIENT)
    public ItemStack getPrimaryGuiIcon() {
        return this.primaryGuiButtonIcon.getStack();
    }

    @SideOnly(Side.CLIENT)
    public void setGuiLink(IGuiSub gs) {
        this.guiLink = gs;
    }
}
