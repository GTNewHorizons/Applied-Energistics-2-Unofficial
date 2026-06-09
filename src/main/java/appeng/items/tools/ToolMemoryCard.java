/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.items.tools;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.INetworkToolItem;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.core.features.AEFeature;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.items.AEBaseItem;
import appeng.items.contents.NetworkToolViewer;
import appeng.parts.automation.UpgradeInventory;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;

public class ToolMemoryCard extends AEBaseItem implements IMemoryCard {

    public ToolMemoryCard() {
        this.setFeature(EnumSet.of(AEFeature.Core));
        this.setMaxStackSize(1);
    }

    @Override
    public void addCheckedInformation(final ItemStack stack, final EntityPlayer player, final List<String> lines,
            final boolean displayMoreInfo) {
        lines.add(this.getLocalizedName(this.getSettingsName(stack) + ".name", this.getSettingsName(stack)));

        final NBTTagCompound data = this.getData(stack);
        if (data.hasKey("tooltip")) {
            lines.add(this.getLocalizedName(data.getString("tooltip") + ".name", data.getString("tooltip")));
        }

        if (data.hasKey("freq")) {
            final long freq = data.getLong("freq");
            final String freqTooltip = String.format("%X", freq).replaceAll("(.{4})", "$0 ").trim();

            final String local = ButtonToolTips.P2PFrequency.getLocal();

            lines.add(String.format(local, freqTooltip));
        }

        if (data.hasKey("custom_name")) {
            lines.add(data.getString("custom_name"));
        } else if (data.hasKey("display") && data.getCompoundTag("display").hasKey("Name")) {
            lines.add(data.getCompoundTag("display").getString("Name"));
        }
    }

    /**
     * Find the localized string...
     *
     * @param name possible names for the localized string
     * @return localized name
     */
    private String getLocalizedName(final String... name) {
        for (final String n : name) {
            final String l = StatCollector.translateToLocal(n);
            if (!l.equals(n)) {
                return l;
            }
        }

        for (final String n : name) {
            return n;
        }

        return "";
    }

    @Override
    public void setMemoryCardContents(final ItemStack is, final String settingsName, final NBTTagCompound data) {
        ItemStackNBT.of(is).setString("Config", settingsName).setCompoundTag("Data", data);
    }

    @Override
    public String getSettingsName(final ItemStack is) {
        final String name = ItemStackNBT.getString(is, "Config");
        return name == null || name.isEmpty() ? GuiText.Blank.getUnlocalized() : name;
    }

    @Override
    public NBTTagCompound getData(final ItemStack is) {
        final NBTTagCompound data = ItemStackNBT.getCompoundTag(is, "Data");
        if (data == null) {
            return new NBTTagCompound();
        }
        return (NBTTagCompound) data.copy();
    }

    @Override
    public void notifyUser(final EntityPlayer player, final MemoryCardMessages msg) {
        if (Platform.isClient()) {
            return;
        }

        switch (msg) {
            case SETTINGS_CLEARED -> player.addChatMessage(PlayerMessages.SettingCleared.toChat());
            case INVALID_MACHINE -> player.addChatMessage(PlayerMessages.InvalidMachine.toChat());
            case SETTINGS_LOADED -> player.addChatMessage(PlayerMessages.LoadedSettings.toChat());
            case SETTINGS_SAVED -> player.addChatMessage(PlayerMessages.SavedSettings.toChat());
            default -> {}
        }
    }

    @Override
    public boolean onItemUse(final ItemStack is, final EntityPlayer player, final World w, final int x, final int y,
            final int z, final int side, final float hx, final float hy, final float hz) {
        if (player.isSneaking() && !w.isRemote) {
            if (ForgeEventFactory.onItemUseStart(player, is, 1) <= 0) return false;
            final IMemoryCard mem = (IMemoryCard) is.getItem();
            mem.notifyUser(player, MemoryCardMessages.SETTINGS_CLEARED);
            is.setTagCompound(null);
            return true;
        } else {
            return super.onItemUse(is, player, w, x, y, z, side, hx, hy, hz);
        }
    }

    @Override
    public boolean doesSneakBypassUse(final World world, final int x, final int y, final int z,
            final EntityPlayer player) {
        return true;
    }

    public static void setUpgradesInfo(NBTTagCompound data, UpgradeInventory ui) {
        if (ui != null) {
            NBTTagList tagList = new NBTTagList();
            for (int i = 0; i < ui.getSizeInventory(); i++) {
                ItemStack uis = ui.getStackInSlot(i);
                NBTTagCompound newIs = new NBTTagCompound();
                if (uis != null) {
                    uis.writeToNBT(newIs);
                }
                tagList.appendTag(newIs);
            }
            if (tagList.tagCount() > 0) data.setTag("upgradesList", tagList);
        }
    }

    public static void insertUpgrades(final NBTTagCompound data, final EntityPlayer player, final UpgradeInventory up) {
        if (up == null) {
            return;
        }

        final List<ItemStack> existingUpgrades = takeExistingUpgrades(up);

        try {
            final NBTTagList tagList = data.getTagList("upgradesList", NBT.TAG_COMPOUND);
            final int slots = Math.min(tagList.tagCount(), up.getSizeInventory());

            for (int i = 0; i < slots; i++) {
                final ItemStack requested = ItemStack.loadItemStackFromNBT(tagList.getCompoundTagAt(i));
                if (requested == null) {
                    continue;
                }

                ItemStack resolved = extractUpgrade(existingUpgrades, requested);
                if (resolved == null) {
                    resolved = extractUpgradeFromPlayer(player, requested);
                }

                if (resolved != null) {
                    up.setInventorySlotContents(i, resolved);
                }
            }
        } finally {
            for (final ItemStack upgrade : existingUpgrades) {
                final ItemStack leftOver = insertIntoNetworkTools(player, upgrade);
                Platform.addToPlayerInvOrDrop(player, leftOver);
            }
        }
    }

    private static List<ItemStack> takeExistingUpgrades(final IInventory upgrades) {
        final List<ItemStack> existingUpgrades = new ArrayList<>();

        for (int i = 0; i < upgrades.getSizeInventory(); i++) {
            final ItemStack existing = upgrades.getStackInSlot(i);
            if (existing == null) {
                continue;
            }

            upgrades.setInventorySlotContents(i, null);
            existingUpgrades.add(existing);
        }

        return existingUpgrades;
    }

    @Nullable
    private static ItemStack insertIntoNetworkTools(final EntityPlayer player, @Nullable ItemStack stack) {
        for (int i = 0; stack != null && i < player.inventory.getSizeInventory(); i++) {
            final ItemStack toolStack = player.inventory.getStackInSlot(i);
            if (toolStack != null && toolStack.getItem() instanceof INetworkToolItem networkTool) {
                final NetworkToolViewer networkToolInventory = new NetworkToolViewer(
                        toolStack,
                        null,
                        networkTool.getInventorySize());
                final InventoryAdaptor adaptor = InventoryAdaptor
                        .getAdaptor(networkToolInventory, ForgeDirection.UNKNOWN);
                if (adaptor != null) {
                    stack = adaptor.addItems(stack);
                }
            }
        }

        return stack;
    }

    @Nullable
    private static ItemStack extractUpgrade(final List<ItemStack> upgrades, @NotNull final ItemStack requested) {
        for (int i = 0; i < upgrades.size(); i++) {
            final ItemStack upgrade = upgrades.get(i);
            if (isMatchingUpgrade(requested, upgrade)) {
                upgrades.remove(i);
                return upgrade;
            }
        }

        return null;
    }

    @Nullable
    private static ItemStack extractUpgradeFromPlayer(final EntityPlayer player, @NotNull final ItemStack requested) {
        for (int i = 0; i < player.inventory.getSizeInventory(); i++) {
            final ItemStack stack = player.inventory.getStackInSlot(i);
            if (isMatchingUpgrade(requested, stack)) {
                final ItemStack extracted = player.inventory.decrStackSize(i, 1);
                player.inventory.markDirty();
                player.onUpdate();
                if (extracted != null) {
                    return extracted;
                }
            } else if (stack != null && stack.getItem() instanceof INetworkToolItem networkTool) {
                final ItemStack extracted = extractUpgradeFromNetworkTool(stack, networkTool, requested);
                if (extracted != null) {
                    return extracted;
                }
            }
        }

        return null;
    }

    @Nullable
    private static ItemStack extractUpgradeFromNetworkTool(@NotNull final ItemStack toolStack,
            @NotNull final INetworkToolItem networkTool, @NotNull final ItemStack requested) {
        final NetworkToolViewer networkToolInventory = new NetworkToolViewer(
                toolStack,
                null,
                networkTool.getInventorySize());

        for (int i = 0; i < networkToolInventory.getSizeInventory(); i++) {
            if (isMatchingUpgrade(requested, networkToolInventory.getStackInSlot(i))) {
                final ItemStack extracted = networkToolInventory.decrStackSize(i, 1);
                networkToolInventory.markDirty();
                if (extracted != null) {
                    return extracted;
                }
            }
        }

        return null;
    }

    private static boolean isMatchingUpgrade(@NotNull final ItemStack requested, @Nullable final ItemStack candidate) {
        return candidate != null && candidate.stackSize > 0 && requested.isItemEqual(candidate);
    }
}
