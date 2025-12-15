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

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.PlayerSource;
import appeng.api.storage.ITerminalHost;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.ContainerSubGui;
import appeng.container.guisync.GuiSync;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketVirtualSlot;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ContainerCraftAmount extends ContainerSubGui {

    @SideOnly(Side.CLIENT)
    private MEGuiTextField amountField;

    @GuiSync(1)
    public long initialCraftAmount = -1;

    private IAEStack<?> itemToCreate;

    public ContainerCraftAmount(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.verifyPermissions(SecurityPermissions.CRAFT, false);
    }

    public IGrid getGrid() {
        final IActionHost h = ((IActionHost) this.getTarget());
        return h.getActionableNode().getGrid();
    }

    public World getWorld() {
        return this.getPlayerInv().player.worldObj;
    }

    public BaseActionSource getActionSrc() {
        return new PlayerSource(this.getPlayerInv().player, (IActionHost) this.getTarget());
    }

    // addon
    public Slot getCraftingItem() {
        return new Slot(null, 0, 0, 0) {

            @Override
            public void putStack(ItemStack p_75215_1_) {}
        };
    }

    public IAEStack<?> getItemToCraft() {
        return this.itemToCreate;
    }

    public void setItemToCraft(@Nonnull final IAEStack<?> itemToCreate) {
        this.itemToCreate = itemToCreate;

        final PacketVirtualSlot p = new PacketVirtualSlot(StorageName.NONE, 0, itemToCreate);
        NetworkHandler.instance.sendTo(p, (EntityPlayerMP) this.getInventoryPlayer().player);
    }

    public void setInitialCraftAmount(long initialCraftAmount) {
        this.initialCraftAmount = initialCraftAmount;
    }

    @SideOnly(Side.CLIENT)
    public void setAmountField(MEGuiTextField amountField) {
        this.amountField = amountField;
        this.amountField.setText(String.valueOf(Math.max(1, this.initialCraftAmount)));
        this.amountField.setCursorPositionEnd();
        this.amountField.setSelectionPos(0);
    }

    @Override
    public void onUpdate(String field, Object oldValue, Object newValue) {
        if (field.equals("initialCraftAmount")) {
            if (this.amountField != null) {
                this.amountField.setText(String.valueOf(Math.max(1, this.initialCraftAmount)));
                this.amountField.setCursorPositionEnd();
                this.amountField.setSelectionPos(0);
            }
        }

        super.onUpdate(field, oldValue, newValue);
    }

    public void openConfirmationGUI(EntityPlayer player, TileEntity te) {
        Platform.openGUI(
                player,
                te,
                this.getOpenContext().getSide(),
                GuiBridge.GUI_CRAFTING_CONFIRM,
                getTargetSlotIndex());
        setupConfirmationGUI(player);
    }

    public void setupConfirmationGUI(EntityPlayer player) {
        if (player.openContainer instanceof ContainerCraftConfirm ccc) {
            ccc.setItemToCraft(this.itemToCreate);
        }
    }
}
