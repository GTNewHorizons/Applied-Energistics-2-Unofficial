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

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import appeng.api.config.FuzzyMode;
import appeng.api.config.LevelType;
import appeng.api.config.RedstoneMode;
import appeng.api.config.SecurityPermissions;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.parts.ILevelEmitter;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.client.gui.IGuiSub;
import appeng.client.gui.implementations.GuiLevelEmitter;
import appeng.client.gui.widgets.MEGuiTextField;
import appeng.container.PrimaryGui;
import appeng.container.interfaces.IContainerSubGui;
import appeng.container.slot.SlotInaccessible;
import appeng.container.slot.SlotRestrictedInput;
import appeng.container.sync.SyncCodecs;
import appeng.container.sync.SyncRegistrar;
import appeng.container.sync.TypeFilterDelta;
import appeng.container.sync.codecs.AEStackTypeFilterSyncCodec;
import appeng.container.sync.handlers.ConfigEnumSyncHandler;
import appeng.container.sync.handlers.DeltaObjectSyncHandler;
import appeng.container.sync.handlers.LongSyncHandler;
import appeng.container.sync.handlers.ObjectSyncHandler;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.util.AEStackTypeFilter;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class ContainerLevelEmitter extends ContainerUpgradeable implements IContainerSubGui {

    private final ILevelEmitter lvlEmitter;

    @SideOnly(Side.CLIENT)
    private MEGuiTextField textField;

    private final LongSyncHandler emitterValueSync;
    private final DeltaObjectSyncHandler<@NotNull AEStackTypeFilter, TypeFilterDelta> typeFiltersSync;
    private final ConfigEnumSyncHandler<@NotNull LevelType> levelModeSync;
    private final ConfigEnumSyncHandler<@NotNull YesNo> craftingModeSync;
    private final ObjectSyncHandler<@Nullable IAEStack<?>> configStackSync;

    public ContainerLevelEmitter(final InventoryPlayer ip, final ILevelEmitter te) {
        super(ip, te);
        this.lvlEmitter = te;

        final SyncRegistrar sync = this.syncRegistrar();
        this.emitterValueSync = sync.longSync("emitterValue").onClientChange((oldValue, newValue) -> {
            if (this.textField != null) {
                this.textField.setText(String.valueOf(newValue));
                this.textField.setCursorPositionEnd();
            }
        }).onServerChange((oldValue, newValue) -> this.lvlEmitter.setReportingValue(newValue));

        this.typeFiltersSync = sync
                .object("typeFilters", AEStackTypeFilterSyncCodec.INSTANCE, this.lvlEmitter.getTypeFilters())
                .onClientChange((oldValue, newValue) -> {
                    if (Minecraft.getMinecraft().currentScreen instanceof GuiLevelEmitter guiLevelEmitter) {
                        guiLevelEmitter.onUpdateTypeFilters();
                    }
                }).onServerChange((oldValue, newValue) -> {
                    if (newValue == null) {
                        return;
                    }

                    this.lvlEmitter.getTypeFilters().copyFrom(newValue);
                    this.lvlEmitter.onChangeTypeFilters();
                });

        this.levelModeSync = sync.configEnum(
                "levelMode",
                Settings.LEVEL_TYPE,
                LevelType.class,
                this.getUpgradeable().getConfigManager(),
                (LevelType) lvlEmitter.getConfigManager().getSetting(Settings.LEVEL_TYPE));
        this.craftingModeSync = sync.configEnum(
                "craftingMode",
                Settings.CRAFT_VIA_REDSTONE,
                YesNo.class,
                this.getUpgradeable().getConfigManager(),
                (YesNo) lvlEmitter.getConfigManager().getSetting(Settings.CRAFT_VIA_REDSTONE));
        this.configStackSync = sync
                .object(
                        "configStack",
                        SyncCodecs.aeStack(),
                        this.lvlEmitter.getAEInventoryByName(StorageName.CONFIG).getAEStackInSlot(0))
                .onServerChange((oldValue, newValue) -> {
                    final IAEStack<?> stack = newValue == null ? null : newValue.copy();
                    this.lvlEmitter.getAEInventoryByName(StorageName.CONFIG).putAEStackInSlot(0, stack);
                });

        // sub gui copy paste
        this.primaryGuiButtonIcon = new SlotInaccessible(new AppEngInternalInventory(null, 1), 0, 0, -9000);
        this.addSlotToContainer(this.primaryGuiButtonIcon);
    }

    @SideOnly(Side.CLIENT)
    public void setTextField(final MEGuiTextField level) {
        this.textField = level;
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
            this.emitterValueSync.set(this.lvlEmitter.getReportingValue());
            this.typeFiltersSync.set(this.lvlEmitter.getTypeFilters());
            this.craftingModeSync.syncFromConfig();
            this.levelModeSync.syncFromConfig();
            this.setFuzzyMode((FuzzyMode) this.getUpgradeable().getConfigManager().getSetting(Settings.FUZZY_MODE));
            this.setRedStoneMode(
                    (RedstoneMode) this.getUpgradeable().getConfigManager().getSetting(Settings.REDSTONE_EMITTER));
            this.configStackSync.set(this.lvlEmitter.getAEInventoryByName(StorageName.CONFIG).getAEStackInSlot(0));
        }

        this.standardDetectAndSendChanges();
    }

    public long getEmitterValue() {
        return this.emitterValueSync.get();
    }

    public void setLevel(final long l) {
        this.emitterValueSync.set(l);
    }

    @Override
    public YesNo getCraftingMode() {
        return this.craftingModeSync.get();
    }

    @Override
    public void setCraftingMode(final YesNo cmType) {
        this.craftingModeSync.set(cmType);
    }

    public void rotateCraftingMode(final boolean backwards) {
        this.craftingModeSync.rotate(backwards);
    }

    public LevelType getLevelMode() {
        return this.levelModeSync.get();
    }

    public void rotateLevelMode(final boolean backwards) {
        this.levelModeSync.rotate(backwards);
    }

    @NotNull
    public AEStackTypeFilter getTypeFilters() {
        return this.typeFiltersSync.get();
    }

    public void toggleTypeFilter(@NotNull final IAEStackType<?> type) {
        this.typeFiltersSync.applyAndQueueDelta(TypeFilterDelta.toggle(type));
    }

    public ILevelEmitter getLvlEmitter() {
        return this.lvlEmitter;
    }

    public @Nullable IAEStack<?> getConfigStack() {
        return this.configStackSync.get();
    }

    public void setConfigStack(@Nullable final IAEStack<?> stack) {
        this.configStackSync.set(stack);
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
