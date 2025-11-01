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

import static appeng.util.Platform.isServer;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.api.config.FuzzyMode;
import appeng.api.config.LevelType;
import appeng.api.config.RedstoneMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.parts.ILevelEmitter;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.client.gui.IGuiSub;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.PrimaryGui;
import appeng.container.guisync.GuiSync;
import appeng.container.interfaces.IContainerSubGui;
import appeng.container.interfaces.IVirtualSlotHolder;
import appeng.container.slot.SlotInaccessible;
import appeng.container.slot.SlotRestrictedInput;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

public class ContainerLevelEmitter extends ContainerUpgradeable implements IVirtualSlotHolder, IContainerSubGui {

    private final ILevelEmitter lvlEmitter;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField textField;

    @GuiSync(2)
    public LevelType lvType;

    @GuiSync(3)
    public long EmitterValue = -1;

    @GuiSync(4)
    public YesNo cmType;

    private final IAEStack<?>[] configClientSlot = new IAEStack[1];

    public ContainerLevelEmitter(final InventoryPlayer ip, final ILevelEmitter te) {
        super(ip, te);
        this.lvlEmitter = te;

        // sub gui copy paste
        this.primaryGuiButtonIcon = new SlotInaccessible(new AppEngInternalInventory(null, 1), 0, 0, -9000);
        this.addSlotToContainer(this.primaryGuiButtonIcon);
    }

    @SideOnly(Side.CLIENT)
    public void setTextField(final MEGuiTextField level) {
        this.textField = level;
    }

    public void setLevel(final long l, final EntityPlayer player) {
        this.lvlEmitter.setReportingValue(l);
        this.EmitterValue = l;
    }

    @Override
    protected void setupConfig() {
        final IInventory upgrades = this.getUpgradeable().getInventoryByName("upgrades");
        if (this.availableUpgrades() > 0) {
            this.addSlotToContainer(
                    (new SlotRestrictedInput(
                            SlotRestrictedInput.PlacableItemType.UPGRADES,
                            upgrades,
                            0,
                            187,
                            8,
                            this.getInventoryPlayer())).setNotDraggable());
        }
        if (this.availableUpgrades() > 1) {
            this.addSlotToContainer(
                    (new SlotRestrictedInput(
                            SlotRestrictedInput.PlacableItemType.UPGRADES,
                            upgrades,
                            1,
                            187,
                            8 + 18,
                            this.getInventoryPlayer())).setNotDraggable());
        }
        if (this.availableUpgrades() > 2) {
            this.addSlotToContainer(
                    (new SlotRestrictedInput(
                            SlotRestrictedInput.PlacableItemType.UPGRADES,
                            upgrades,
                            2,
                            187,
                            8 + 18 * 2,
                            this.getInventoryPlayer())).setNotDraggable());
        }
        if (this.availableUpgrades() > 3) {
            this.addSlotToContainer(
                    (new SlotRestrictedInput(
                            SlotRestrictedInput.PlacableItemType.UPGRADES,
                            upgrades,
                            3,
                            187,
                            8 + 18 * 3,
                            this.getInventoryPlayer())).setNotDraggable());
        }
    }

    @Override
    protected boolean supportCapacity() {
        return false;
    }

    @Override
    public int availableUpgrades() {

        return 1;
    }

    @Override
    public void detectAndSendChanges() {
        this.verifyPermissions(SecurityPermissions.BUILD, false);

        if (Platform.isServer()) {
            this.EmitterValue = this.lvlEmitter.getReportingValue();
            this.setCraftingMode(
                    (YesNo) this.getUpgradeable().getConfigManager().getSetting(Settings.CRAFT_VIA_REDSTONE));
            this.setLevelMode((LevelType) this.getUpgradeable().getConfigManager().getSetting(Settings.LEVEL_TYPE));
            this.setFuzzyMode((FuzzyMode) this.getUpgradeable().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setRedStoneMode(
                    (RedstoneMode) this.getUpgradeable().getConfigManager().getSetting(Settings.REDSTONE_EMITTER));
            this.updateVirtualSlots(
                    StorageName.NONE,
                    this.lvlEmitter.getAEInventoryByName(StorageName.NONE),
                    this.configClientSlot);
        }

        this.standardDetectAndSendChanges();
    }

    @Override
    public void onUpdate(final String field, final Object oldValue, final Object newValue) {
        if (field.equals("EmitterValue")) {
            if (this.textField != null) {
                this.textField.setText(String.valueOf(this.EmitterValue));

                if (String.valueOf(oldValue).equals("-1")) {
                    this.textField.setCursorPositionEnd();
                }
            }
        }
    }

    @Override
    public YesNo getCraftingMode() {
        return this.cmType;
    }

    @Override
    public void setCraftingMode(final YesNo cmType) {
        this.cmType = cmType;
    }

    public LevelType getLevelMode() {
        return this.lvType;
    }

    private void setLevelMode(final LevelType lvType) {
        this.lvType = lvType;
    }

    public ILevelEmitter getLvlEmitter() {
        return this.lvlEmitter;
    }

    @Override
    public void receiveSlotStacks(StorageName invName, Int2ObjectMap<IAEStack<?>> slotStacks) {
        for (var entry : slotStacks.int2ObjectEntrySet()) {
            this.lvlEmitter.getAEInventoryByName(StorageName.NONE)
                    .putAEStackInSlot(entry.getIntKey(), entry.getValue());
        }

        if (isServer()) {
            this.updateVirtualSlots(
                    StorageName.NONE,
                    this.lvlEmitter.getAEInventoryByName(StorageName.NONE),
                    this.configClientSlot);
        }
    }

    // for level terminal
    // sub gui copypaste
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
