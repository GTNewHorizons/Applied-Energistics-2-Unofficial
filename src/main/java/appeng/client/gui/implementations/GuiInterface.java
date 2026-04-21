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

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import appeng.api.config.AdvancedBlockingMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.InsertionMode;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.GuiSimpleImgButton;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerInterface;
import appeng.core.AELog;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.core.sync.packets.PacketSwitchGuis;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.IInterfaceHost;

public class GuiInterface extends GuiUpgradeable {

    private GuiTabButton priority;
    private GuiImgButton BlockMode;
    private GuiImgButton SmartBlockMode;
    private GuiImgButton fuzzyMode;
    private GuiToggleButton interfaceMode;
    private GuiImgButton insertionMode;
    private GuiSimpleImgButton doublePatterns;
    private GuiToggleButton patternOptimization;

    private GuiImgButton advancedBlockingMode;
    private GuiImgButton lockCraftingMode;

    public GuiInterface(final InventoryPlayer inventoryPlayer, final IInterfaceHost te) {
        super(new ContainerInterface(inventoryPlayer, te));
        this.ySize = 211;
    }

    @Override
    protected void addButtons() {
        this.priority = new GuiTabButton(
                this.guiLeft + 154,
                this.guiTop,
                2 + 4 * 16,
                GuiText.Priority.getLocal(),
                itemRender);
        this.buttonList.add(this.priority);

        int offset = 8;

        this.BlockMode = new GuiImgButton(this.guiLeft - 18, this.guiTop + offset, Settings.BLOCK, YesNo.NO);
        this.buttonList.add(this.BlockMode);
        this.SmartBlockMode = new GuiImgButton(this.guiLeft - 36, this.guiTop + offset, Settings.SMART_BLOCK, YesNo.NO);
        this.buttonList.add(this.SmartBlockMode);

        offset += 18;

        this.interfaceMode = new GuiToggleButton(
                this.guiLeft - 18,
                this.guiTop + offset,
                84,
                85,
                GuiText.InterfaceTerminal.getLocal(),
                GuiText.InterfaceTerminalHint.getLocal());
        this.buttonList.add(this.interfaceMode);

        offset += 18;

        this.insertionMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + offset,
                Settings.INSERTION_MODE,
                InsertionMode.DEFAULT);
        this.buttonList.add(this.insertionMode);

        offset += 18;

        this.doublePatterns = new GuiSimpleImgButton(this.guiLeft - 18, this.guiTop + offset, 71, "");
        this.doublePatterns.enabled = false;
        this.buttonList.add(this.doublePatterns);

        offset += 18;

        this.patternOptimization = new GuiToggleButton(
                this.guiLeft - 18,
                this.guiTop + offset,
                178,
                194,
                GuiText.PatternOptimization.getLocal(),
                GuiText.PatternOptimizationHint.getLocal());
        this.buttonList.add(this.patternOptimization);

        offset += 18;

        this.advancedBlockingMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + offset,
                Settings.ADVANCED_BLOCKING_MODE,
                AdvancedBlockingMode.DEFAULT);
        this.advancedBlockingMode.visible = this.bc.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) > 0;
        this.buttonList.add(advancedBlockingMode);

        offset += 18;

        this.lockCraftingMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + offset,
                Settings.LOCK_CRAFTING_MODE,
                LockCraftingMode.NONE);
        this.lockCraftingMode.visible = this.bc.getInstalledUpgrades(Upgrades.LOCK_CRAFTING) > 0;
        this.buttonList.add(lockCraftingMode);

        offset += 18;

        this.fuzzyMode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + offset,
                Settings.FUZZY_MODE,
                FuzzyMode.IGNORE_ALL);
        this.fuzzyMode.visible = this.bc.getInstalledUpgrades(Upgrades.FUZZY) > 0;
        this.buttonList.add(fuzzyMode);

        initCustomButtons(this.guiLeft - 18, offset);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (this.BlockMode != null) {
            this.BlockMode.set(((ContainerInterface) this.cvb).getBlockingMode());
        }
        if (this.SmartBlockMode != null) {
            this.SmartBlockMode.set(((ContainerInterface) this.cvb).getSmartBlockingMode());
        }

        if (this.interfaceMode != null) {
            this.interfaceMode.setState(((ContainerInterface) this.cvb).getInterfaceTerminalMode() == YesNo.YES);
        }

        if (this.insertionMode != null) {
            this.insertionMode.set(((ContainerInterface) this.cvb).getInsertionMode());
        }

        if (this.doublePatterns != null) {
            this.doublePatterns.enabled = ((ContainerInterface) this.cvb).isAllowedToMultiplyPatterns;
            if (this.doublePatterns.enabled) this.doublePatterns.setTooltip(
                    ButtonToolTips.DoublePatterns.getLocal() + "\n" + ButtonToolTips.DoublePatternsHint.getLocal());
            else this.doublePatterns.setTooltip(
                    ButtonToolTips.DoublePatterns.getLocal() + "\n" + ButtonToolTips.OptimizePatternsNoReq.getLocal());
        }

        if (this.patternOptimization != null) {
            this.patternOptimization.setState(((ContainerInterface) this.cvb).getPatternOptimization() == YesNo.YES);
        }

        if (this.advancedBlockingMode != null) {
            this.advancedBlockingMode.set(((ContainerInterface) this.cvb).getAdvancedBlockingMode());
        }

        if (this.lockCraftingMode != null) {
            this.lockCraftingMode.set(((ContainerInterface) this.cvb).getLockCraftingMode());
        }

        if (this.fuzzyMode != null) {
            this.fuzzyMode.set(((ContainerInterface) this.cvb).getFuzzyMode());
        }

        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.Interface.getLocal()),
                8,
                6,
                GuiColors.InterfaceTitle.getColor());
    }

    @Override
    public void drawBG(int offsetX, int offsetY, int mouseX, int mouseY) {
        super.drawBG(offsetX, offsetY, mouseX, mouseY);

        final int capacity = ((ContainerInterface) this.cvb).getPatternCapacityCardsInstalled();
        final int fuzzy = ((ContainerInterface) this.cvb).getFuzzyCardsInstalled();

        // config slots
        if (capacity == -1) {
            this.drawTexturedModalRect(offsetX + 7, offsetY + 14, 7, 89, 162, 18);
        } else {
            this.drawTexturedModalRect(offsetX + 7, offsetY + 14, 7, 71, 162, 18);

            if (fuzzy > 0) {
                this.drawTexturedModalRect(offsetX + 152, offsetY + 15, 8, 54, 16, 16);
                this.drawTexturedModalRect(offsetX + 134, offsetY + 15, 8, 54, 16, 16);
                this.drawTexturedModalRect(offsetX + 116, offsetY + 15, 8, 54, 16, 16);
            }

            if (fuzzy > 1) {
                this.drawTexturedModalRect(offsetX + 98, offsetY + 15, 8, 54, 16, 16);
                this.drawTexturedModalRect(offsetX + 80, offsetY + 15, 8, 54, 16, 16);
                this.drawTexturedModalRect(offsetX + 62, offsetY + 15, 8, 54, 16, 16);
            }

            if (fuzzy > 2) {
                this.drawTexturedModalRect(offsetX + 44, offsetY + 15, 8, 54, 16, 16);
                this.drawTexturedModalRect(offsetX + 26, offsetY + 15, 8, 54, 16, 16);
                this.drawTexturedModalRect(offsetX + 8, offsetY + 15, 8, 54, 16, 16);
            }
        }

        // pattern slots
        for (int i = 4; i > 0; i--) {
            if (i > capacity + 1) {
                // fadeout slots
                this.drawTexturedModalRect(offsetX + 7, offsetY + 125 - (18 * i), 7, 89, 162, 18);
            } else {
                // normal slots
                this.drawTexturedModalRect(offsetX + 7, offsetY + 125 - (18 * i), 7, 107, 162, 18);
            }
        }

        // highlight pattern slots with unsupported stack types
        for (final Object obj : this.cvb.inventorySlots) {
            if (obj instanceof Slot slot && hasInvalidTypeStack(slot.getStack())) {
                final int sx = offsetX + slot.xDisplayPosition;
                final int sy = offsetY + slot.yDisplayPosition;
                drawRect(sx, sy, sx + 16, sy + 16, GuiColors.ItemSlotOverlayFluidMismatch.getColor());
            }
        }
    }

    private boolean hasInvalidTypeStack(final ItemStack stack) {
        if (stack == null || stack.getTagCompound() == null) return false;
        final NBTTagCompound nbt = stack.getTagCompound();
        if (nbt.getBoolean("InvalidPattern")) return false;
        IAEStackType<?>[] supportedTypes = ((ContainerInterface) this.cvb).getSupportedStackTypes();
        return hasInvalidTypeInTagList(nbt.getTagList("in", NBT.TAG_COMPOUND), supportedTypes)
                || hasInvalidTypeInTagList(nbt.getTagList("out", NBT.TAG_COMPOUND), supportedTypes);
    }

    private static boolean hasInvalidTypeInTagList(final NBTTagList tagList, IAEStackType<?>[] supportedTypes) {
        outer: for (int i = 0; i < tagList.tagCount(); i++) {
            final NBTTagCompound entry = tagList.getCompoundTagAt(i);
            // Legacy fluid check: patterns created before native liquid support lack StackType
            if (entry.hasKey("FluidName")) return true;

            if (entry.hasKey("StackType")) {
                for (IAEStackType<?> type : supportedTypes) {
                    if (entry.getString("StackType").equals(type.getId())) {
                        continue outer;
                    }
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected String getBackground() {
        return "guis/interface.png";
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        if (actionPerformedCustomButtons(btn)) return;

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.priority) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis(GuiBridge.GUI_PRIORITY));
        }

        if (btn == this.interfaceMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(Settings.INTERFACE_TERMINAL, backwards));
        }

        if (btn == this.BlockMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.BlockMode.getSetting(), backwards));
        }
        if (btn == this.SmartBlockMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.SmartBlockMode.getSetting(), backwards));
        }

        if (btn == this.insertionMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.insertionMode.getSetting(), backwards));
        }

        if (btn == this.doublePatterns) {
            try {
                int val = Keyboard.isKeyDown(Keyboard.KEY_LSHIFT) ? 1 : 0;
                if (backwards) val |= 0b10;
                NetworkHandler.instance
                        .sendToServer(new PacketValueConfig("Interface.DoublePatterns", String.valueOf(val)));
            } catch (final Throwable e) {
                AELog.debug(e);
            }
        }

        if (btn == this.patternOptimization) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(Settings.PATTERN_OPTIMIZATION, backwards));
        }

        if (btn == this.advancedBlockingMode) {
            NetworkHandler.instance
                    .sendToServer(new PacketConfigButton(this.advancedBlockingMode.getSetting(), backwards));
        }

        if (btn == this.lockCraftingMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.lockCraftingMode.getSetting(), backwards));
        }

        if (btn == this.fuzzyMode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(this.fuzzyMode.getSetting(), backwards));
        }
    }

    @Override
    protected void handleButtonVisibility() {
        super.handleButtonVisibility();
        if (this.advancedBlockingMode != null) {
            this.advancedBlockingMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.ADVANCED_BLOCKING) > 0);
        }
        if (this.lockCraftingMode != null) {
            this.lockCraftingMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.LOCK_CRAFTING) > 0);
        }
        if (this.fuzzyMode != null) {
            this.fuzzyMode.setVisibility(this.bc.getInstalledUpgrades(Upgrades.FUZZY) > 0);
        }
    }
}
