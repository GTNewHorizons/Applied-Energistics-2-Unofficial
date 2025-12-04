/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import appeng.api.config.AccessRestriction;
import appeng.api.config.ActionItems;
import appeng.api.config.FuzzyMode;
import appeng.api.config.Settings;
import appeng.api.config.StorageFilter;
import appeng.api.config.Upgrades;
import appeng.api.parts.IStorageBus;
import appeng.api.storage.StorageChannel;
import appeng.client.gui.slots.VirtualMEPhantomSlot;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.implementations.ContainerStorageBus;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.tile.inventory.IAEStackInventory;

public class GuiStorageBus extends GuiUpgradeable {

    private GuiImgButton rwMode;
    private GuiImgButton storageFilter;
    private GuiTabButton priority;
    private GuiImgButton partition;
    private GuiImgButton clear;
    private VirtualMEPhantomSlot[] configSlots;
    private final ContainerStorageBus containerStorageBus;

    public GuiStorageBus(final InventoryPlayer inventoryPlayer, final IStorageBus te) {
        super(new ContainerStorageBus(inventoryPlayer, te));
        this.containerStorageBus = (ContainerStorageBus) inventorySlots;
        this.ySize = 251;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void addButtons() {
        this.clear = new GuiImgButton(this.guiLeft - 18, this.guiTop + 8, Settings.ACTIONS, ActionItems.CLOSE);
        this.partition = new GuiImgButton(this.guiLeft - 18, this.guiTop + 28, Settings.ACTIONS, ActionItems.WRENCH);
        this.rwMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 48,
                Settings.ACCESS,
                AccessRestriction.READ_WRITE);
        this.storageFilter = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 68,
                Settings.STORAGE_FILTER,
                StorageFilter.EXTRACTABLE_ONLY);
        this.fuzzyMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 88,
                Settings.FUZZY_MODE,
                FuzzyMode.IGNORE_ALL);
        this.oreFilter = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 88,
                Settings.ACTIONS,
                ActionItems.ORE_FILTER);

        this.buttonList.add(
                this.priority = new GuiTabButton(
                        this.guiLeft + 154,
                        this.guiTop,
                        2 + 4 * 16,
                        GuiText.Priority.getLocal(),
                        itemRender));

        this.buttonList.add(this.storageFilter);
        this.buttonList.add(this.fuzzyMode);
        this.buttonList.add(this.rwMode);
        this.buttonList.add(this.partition);
        this.buttonList.add(this.clear);
        this.buttonList.add(this.oreFilter);
    }

    @Override
    public void initGui() {
        super.initGui();
        initVirtualSlots();
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.StorageBus.getLocal()),
                8,
                6,
                GuiColors.StorageBusTitle.getColor());
        this.fontRendererObj.drawString(
                GuiText.inventory.getLocal(),
                8,
                this.ySize - 96 + 3,
                GuiColors.StorageBusInventory.getColor());

        if (this.cvb instanceof ContainerStorageBus csb) {
            if (this.fuzzyMode != null) {
                this.fuzzyMode.set(csb.getFuzzyMode());
            }
            if (this.storageFilter != null) {
                this.storageFilter.set(csb.getStorageFilter());
            }
            if (this.rwMode != null) {
                this.rwMode.set(csb.getReadWriteMode());
            }
            if (this.partition != null) {
                this.partition.set(csb.getPartitionMode());
            }
        }

        updateSlotVisibility();
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);

        for (int i = 0; i < 5; i++) {
            if (i >= this.containerStorageBus.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY)) {
                GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 0.4F);
                GL11.glEnable(GL11.GL_BLEND);
            }

            this.drawTexturedModalRect(offsetX + 7, offsetY + 64 + (18 * i), 7, 28, 162, 18);

            if (i >= this.containerStorageBus.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY)) {
                GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
                GL11.glPopAttrib();
            }
        }
    }

    @Override
    protected String getBackground() {
        return "guis/storagebus.png";
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        try {
            if (btn == this.partition) {
                NetworkHandler.instance.sendToServer(
                        new PacketValueConfig("StorageBus.Action", backwards ? "Partition-Clear" : "Partition"));
            } else if (btn == this.clear) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("StorageBus.Action", "Clear"));
            } else if (btn == this.priority) {
                NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
            } else if (btn == this.rwMode) {
                NetworkHandler.instance.sendToServer(new PacketConfigButton(this.rwMode.getSetting(), backwards));
            } else if (btn == this.storageFilter) {
                NetworkHandler.instance
                        .sendToServer(new PacketConfigButton(this.storageFilter.getSetting(), backwards));
            }
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    private void initVirtualSlots() {
        this.configSlots = new VirtualMEPhantomSlot[63];
        final IAEStackInventory inputInv = this.containerStorageBus.getConfig();
        final int xo = 8;
        final int yo = -133;

        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 9; x++) {
                VirtualMEPhantomSlot slot = new VirtualMEPhantomSlot(
                        xo + x * 18,
                        yo + y * 18 + 9 * 18,
                        inputInv,
                        x + y * 9);
                this.configSlots[x + y * 9] = slot;
                this.registerVirtualSlots(slot);
            }
        }
    }

    protected void updateSlotVisibility() {
        for (VirtualMEPhantomSlot slot : this.configSlots) {
            slot.setHidden(
                    slot.getSlotIndex() > (18
                            + (9 * this.containerStorageBus.getUpgradeable().getInstalledUpgrades(Upgrades.CAPACITY))));
        }
    }

    @Override
    protected void handlePhantomSlotInteraction(VirtualMEPhantomSlot slot, int mouseButton) {
        StorageChannel channel = containerStorageBus.getStorageChannel();
        slot.handleMouseClicked(channel == StorageChannel.ITEMS, channel == StorageChannel.FLUIDS, false);
    }
}
