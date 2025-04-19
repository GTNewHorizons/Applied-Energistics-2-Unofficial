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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.event.ForgeEventFactory;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.INetworkToolItem;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.core.features.AEFeature;
import appeng.core.localization.ButtonToolTips;
import appeng.core.localization.GuiText;
import appeng.core.localization.PlayerMessages;
import appeng.items.AEBaseItem;
import appeng.items.contents.NetworkToolViewer;
import appeng.items.materials.ItemMultiMaterial;
import appeng.parts.automation.UpgradeInventory;
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
            lines.add(
                    StatCollector.translateToLocal(
                            this.getLocalizedName(data.getString("tooltip") + ".name", data.getString("tooltip"))));
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
        final NBTTagCompound c = Platform.openNbtData(is);
        c.setString("Config", settingsName);
        c.setTag("Data", data);
    }

    @Override
    public String getSettingsName(final ItemStack is) {
        final NBTTagCompound c = Platform.openNbtData(is);
        final String name = c.getString("Config");
        return name == null || name.isEmpty() ? GuiText.Blank.getUnlocalized() : name;
    }

    @Override
    public NBTTagCompound getData(final ItemStack is) {
        final NBTTagCompound c = Platform.openNbtData(is);
        NBTTagCompound o = c.getCompoundTag("Data");
        if (o == null) {
            o = new NBTTagCompound();
        }
        return (NBTTagCompound) o.copy();
    }

    @Override
    public void notifyUser(final EntityPlayer player, final MemoryCardMessages msg) {
        if (Platform.isClient()) {
            return;
        }

        switch (msg) {
            case SETTINGS_CLEARED -> player.addChatMessage(PlayerMessages.SettingCleared.get());
            case INVALID_MACHINE -> player.addChatMessage(PlayerMessages.InvalidMachine.get());
            case SETTINGS_LOADED -> player.addChatMessage(PlayerMessages.LoadedSettings.get());
            case SETTINGS_SAVED -> player.addChatMessage(PlayerMessages.SavedSettings.get());
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

    public static void insertUpgrades(final NBTTagCompound data, EntityPlayer player, UpgradeInventory up) {
        NBTTagList tagList = data.getTagList("upgradesList", 10);
        List<ItemStack> memoryList = new ArrayList<>(Collections.nCopies(tagList.tagCount(), null)); // Preserve order

        for (int i = 0; i < tagList.tagCount(); i++) {
            if (up.getStackInSlot(i) == null) {
                ItemStack item = ItemStack.loadItemStackFromNBT(tagList.getCompoundTagAt(i));
                memoryList.set(i, item);
            }
        }

        if (!memoryList.stream().allMatch(Objects::isNull)) {
            int resolved = 0;
            for (int j = 0; j < player.inventory.getSizeInventory(); j++) {
                ItemStack pi = player.inventory.getStackInSlot(j);
                if (pi != null) {
                    if (pi.getItem() instanceof ItemMultiMaterial) {
                        for (ItemStack is : memoryList) {
                            if (is != null && is.stackSize > 0 && is.isItemEqual(pi)) {
                                is.stackSize = 0;
                                player.inventory.decrStackSize(j, 1);
                                player.onUpdate();
                                resolved++;
                            }
                        }

                        if (resolved == memoryList.size()) break;
                    } else if (pi.getItem() instanceof INetworkToolItem inti) {
                        NetworkToolViewer ntv = new NetworkToolViewer(pi, null, inti.getInventorySize());
                        for (int k = 0; k < ntv.getSizeInventory(); k++) {
                            ItemStack isv = ntv.getStackInSlot(k);
                            if (isv != null) {
                                for (ItemStack is : memoryList) {
                                    if (is != null && is.stackSize > 0 && is.isItemEqual(isv)) {
                                        is.stackSize = 0;
                                        resolved++;
                                        ntv.decrStackSize(k, 1);
                                        ntv.markDirty();
                                    }
                                }

                                if (resolved == memoryList.size()) break;
                            }
                        }
                    }
                }
            }

            for (int i = 0; i < memoryList.size(); i++) {
                ItemStack is = memoryList.get(i);
                if (is != null && is.stackSize == 0) {
                    is.stackSize = 1;
                    up.setInventorySlotContents(i, is);
                }
            }
        }
    }
}
