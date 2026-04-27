/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2014, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.parts.automation;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemFirework;
import net.minecraft.item.ItemReed;
import net.minecraft.item.ItemSkull;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.BlockEvent;

import org.jetbrains.annotations.NotNull;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Settings;
import appeng.api.config.Upgrades;
import appeng.api.config.YesNo;
import appeng.api.networking.events.MENetworkCellArrayUpdate;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellContainer;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.client.texture.CableBusTextures;
import appeng.core.AEConfig;
import appeng.me.GridAccessException;
import appeng.me.storage.MEInventoryHandler;
import appeng.util.Platform;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.PrecisePriorityList;

public class PartFormationPlane extends PartBaseFormationPlane implements ICellContainer, IMEInventory<IAEItemStack> {

    private final MEInventoryHandler myHandler = new MEInventoryHandler(this, ITEM_STACK_TYPE);
    private EntityPlayer owner = null;

    public PartFormationPlane(final ItemStack is) {
        super(is);

        this.updateHandler();
    }

    @Override
    public void onPlacement(EntityPlayer player, ItemStack held, ForgeDirection side) {
        super.onPlacement(player, held, side);
        this.owner = player;
    }

    protected void updateHandler() {
        this.myHandler.setBaseAccess(AccessRestriction.WRITE);
        this.myHandler.setWhitelist(
                this.getInstalledUpgrades(Upgrades.INVERTER) > 0 ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
        this.myHandler.setPriority(this.priority);

        final IItemList<IAEItemStack> priorityList = AEApi.instance().storage().createItemList();

        final int slotsToUse = 18 + this.getInstalledUpgrades(Upgrades.CAPACITY) * 9;
        for (int x = 0; x < this.Config.getSizeInventory() && x < slotsToUse; x++) {
            if (!(this.Config.getAEStackInSlot(x) instanceof IAEItemStack ais)) continue;
            priorityList.add(ais);
        }

        if (this.getInstalledUpgrades(Upgrades.FUZZY) > 0) {
            this.myHandler.setPartitionList(
                    new FuzzyPriorityList(
                            priorityList,
                            (FuzzyMode) this.getConfigManager().getSetting(Settings.FUZZY_MODE)));
        } else {
            this.myHandler.setPartitionList(new PrecisePriorityList(priorityList));
        }

        try {
            this.getProxy().getGrid().postEvent(new MENetworkCellArrayUpdate());
        } catch (final GridAccessException e) {
            // :P
        }
    }

    @Override
    @NotNull
    public List<IMEInventoryHandler> getCellArray(final IAEStackType<?> type) {
        if (this.getProxy().isActive() && type == ITEM_STACK_TYPE) {
            final List<IMEInventoryHandler> Handler = new ArrayList<>(1);
            Handler.add(this.myHandler);
            return Handler;
        }
        return new ArrayList<>();
    }

    @Override
    public IAEItemStack injectItems(final IAEItemStack input, final Actionable type, final BaseActionSource src) {
        if (this.blocked || input == null || input.getStackSize() <= 0) {
            return input;
        }

        final YesNo placeBlock = (YesNo) this.getConfigManager().getSetting(Settings.PLACE_BLOCK);

        final ItemStack is = input.getItemStack();
        final Item i = is.getItem();

        long maxStorage = Math.min(input.getStackSize(), is.getMaxStackSize());
        boolean worked = false;

        final TileEntity te = this.getHost().getTile();
        final World w = te.getWorldObj();
        final ForgeDirection side = this.getSide();

        final int x = te.xCoord + side.offsetX;
        final int y = te.yCoord + side.offsetY;
        final int z = te.zCoord + side.offsetZ;

        if (w.getBlock(x, y, z).isReplaceable(w, x, y, z)) {
            if (placeBlock == YesNo.YES && (i instanceof ItemBlock || i instanceof IPlantable
                    || i instanceof ItemSkull
                    || i instanceof ItemFirework
                    || i instanceof ItemReed)) {
                final EntityPlayer player = Platform.getPlayer((WorldServer) w);
                Platform.configurePlayer(player, side, this.getTile());

                if (i instanceof ItemFirework) {
                    final Chunk c = w.getChunkFromBlockCoords(x, z);
                    int sum = 0;
                    for (final List Z : c.entityLists) {
                        sum += Z.size();
                    }
                    if (sum > 32) {
                        return input;
                    }
                }
                maxStorage = is.stackSize;
                worked = true;
                if (type == Actionable.MODULATE) {
                    if (i instanceof IPlantable || i instanceof ItemSkull || i instanceof ItemReed) {
                        boolean Worked = false;

                        if (side.offsetX == 0 && side.offsetZ == 0) {
                            Worked = i.onItemUse(
                                    is,
                                    player,
                                    w,
                                    x + side.offsetX,
                                    y + side.offsetY,
                                    z + side.offsetZ,
                                    side.getOpposite().ordinal(),
                                    side.offsetX,
                                    side.offsetY,
                                    side.offsetZ);
                        }

                        if (!Worked && side.offsetX == 0 && side.offsetZ == 0) {
                            Worked = i.onItemUse(
                                    is,
                                    player,
                                    w,
                                    x - side.offsetX,
                                    y - side.offsetY,
                                    z - side.offsetZ,
                                    side.ordinal(),
                                    side.offsetX,
                                    side.offsetY,
                                    side.offsetZ);
                        }

                        if (!Worked && side.offsetY == 0) {
                            Worked = i.onItemUse(
                                    is,
                                    player,
                                    w,
                                    x,
                                    y - 1,
                                    z,
                                    ForgeDirection.UP.ordinal(),
                                    side.offsetX,
                                    side.offsetY,
                                    side.offsetZ);
                        }

                        if (!Worked) {
                            i.onItemUse(
                                    is,
                                    player,
                                    w,
                                    x,
                                    y,
                                    z,
                                    side.getOpposite().ordinal(),
                                    side.offsetX,
                                    side.offsetY,
                                    side.offsetZ);
                        }

                        maxStorage -= is.stackSize;
                    } else if (i instanceof ItemFirework) {
                        i.onItemUse(
                                is,
                                player,
                                w,
                                x,
                                y,
                                z,
                                side.getOpposite().ordinal(),
                                side.offsetX,
                                side.offsetY,
                                side.offsetZ);
                        maxStorage -= is.stackSize;
                    } else {
                        player.setCurrentItemOrArmor(0, is.copy());
                        BlockSnapshot blockSnapshot = new BlockSnapshot(
                                w,
                                x,
                                y,
                                z,
                                ((ItemBlock) i).field_150939_a,
                                i.getMetadata(is.getItemDamage()));
                        BlockEvent.PlaceEvent event = new BlockEvent.PlaceEvent(
                                blockSnapshot,
                                w.getBlock(x, y, z),
                                owner == null ? player : owner);
                        MinecraftForge.EVENT_BUS.post(event);
                        if (!event.isCanceled()) {
                            i.onItemUse(
                                    is,
                                    player,
                                    w,
                                    x,
                                    y,
                                    z,
                                    side.getOpposite().ordinal(),
                                    side.offsetX,
                                    side.offsetY,
                                    side.offsetZ);
                            maxStorage -= is.stackSize;
                        } else {
                            worked = false;
                        }
                    }
                } else {
                    maxStorage = 1;
                }
            } else {
                worked = true;
                final Chunk c = w.getChunkFromBlockCoords(x, z);
                int sum = 0;
                for (final List Z : c.entityLists) {
                    sum += Z.size();
                }

                if (sum < AEConfig.instance.formationPlaneEntityLimit) {
                    if (type == Actionable.MODULATE) {

                        is.stackSize = (int) maxStorage;
                        final EntityItem ei = new EntityItem(
                                w,
                                ((side.offsetX != 0 ? 0.0 : 0.7) * (Platform.getRandomFloat() - 0.5f)) + 0.5
                                        + side.offsetX * -0.3
                                        + x,
                                ((side.offsetY != 0 ? 0.0 : 0.7) * (Platform.getRandomFloat() - 0.5f)) + 0.5
                                        + side.offsetY * -0.3
                                        + y,
                                ((side.offsetZ != 0 ? 0.0 : 0.7) * (Platform.getRandomFloat() - 0.5f)) + 0.5
                                        + side.offsetZ * -0.3
                                        + z,
                                is.copy());

                        Entity result = ei;

                        ei.motionX = side.offsetX * 0.2;
                        ei.motionY = side.offsetY * 0.2;
                        ei.motionZ = side.offsetZ * 0.2;

                        if (is.getItem().hasCustomEntity(is)) {
                            result = is.getItem().createEntity(w, ei, is);
                            if (result != null) {
                                ei.setDead();
                            } else {
                                result = ei;
                            }
                        }

                        if (!w.spawnEntityInWorld(result)) {
                            if (((EntityItem) result).getEntityItem().getItem()
                                    == Item.getItemFromBlock(Blocks.dragon_egg)) { // Ducttape fix for HEE replacing the
                                                                                   // Dragon Egg
                                // HEE does cancel the event but does not mark passed entity as dead
                                worked = true;
                            } else {
                                // e.g. ExU item collector cancels item spawn, but takes the item inside
                                worked = result.isDead;
                                result.setDead();
                            }
                        }
                    }
                } else {
                    worked = false;
                }
            }
        }

        this.blocked = !w.getBlock(x, y, z).isReplaceable(w, x, y, z);

        if (worked) {
            final IAEItemStack out = input.copy();
            out.decStackSize(maxStorage);
            if (out.getStackSize() == 0) {
                return null;
            }
            return out;
        }

        return input;
    }

    @Override
    public IAEItemStack extractItems(final IAEItemStack request, final Actionable mode, final BaseActionSource src) {
        return null;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(final IItemList<IAEItemStack> out, int iteration) {
        return out;
    }

    @Override
    public IAEItemStack getAvailableItem(@Nonnull IAEItemStack request, int iteration) {
        return null;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.ITEMS;
    }

    @Override
    public void saveChanges(final IMEInventory cellInventory) {
        // nope!
    }

    @Override
    public @NotNull IAEStackType<?> getStackType() {
        return ITEM_STACK_TYPE;
    }

    @Override
    public boolean supportItemDrop() {
        return true;
    }

    @Override
    public IIcon getActiveIcon() {
        return CableBusTextures.BlockFormPlaneOn.getIcon();
    }
}
