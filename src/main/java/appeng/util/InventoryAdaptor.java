/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.util;

import static com.glodblock.github.util.ModAndClassUtil.EIO;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.IFluidHandler;

import appeng.api.config.FuzzyMode;
import appeng.api.config.InsertionMode;
import appeng.api.parts.IPart;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.helpers.IInterfaceHost;
import appeng.integration.IntegrationRegistry;
import appeng.integration.IntegrationType;
import appeng.integration.abstraction.IBetterStorage;
import appeng.integration.abstraction.IThaumicTinkerer;
import appeng.parts.p2p.PartP2PItems;
import appeng.parts.p2p.PartP2PLiquids;
import appeng.tile.misc.TileInterface;
import appeng.tile.networking.TileCableBus;
import appeng.tile.storage.TileChest;
import appeng.util.inv.AdaptorConduitBandle;
import appeng.util.inv.AdaptorDualityInterface;
import appeng.util.inv.AdaptorFluidHandler;
import appeng.util.inv.AdaptorIInventory;
import appeng.util.inv.AdaptorList;
import appeng.util.inv.AdaptorMEChest;
import appeng.util.inv.AdaptorNull;
import appeng.util.inv.AdaptorP2PFluid;
import appeng.util.inv.AdaptorP2PItem;
import appeng.util.inv.AdaptorPlayerInventory;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.ItemSlot;
import appeng.util.inv.WrapperMCISidedInventory;
import appeng.util.item.AEItemStack;
import crazypants.enderio.conduit.TileConduitBundle;

public abstract class InventoryAdaptor implements Iterable<ItemSlot> {

    // returns an appropriate adaptor, or null
    public static InventoryAdaptor getAdaptor(Object te, final ForgeDirection d) {
        if (te == null) {
            return null;
        }

        final IBetterStorage bs = (IBetterStorage) (IntegrationRegistry.INSTANCE.isEnabled(
                IntegrationType.BetterStorage) ? IntegrationRegistry.INSTANCE.getInstance(IntegrationType.BetterStorage)
                        : null);
        final IThaumicTinkerer tt = (IThaumicTinkerer) (IntegrationRegistry.INSTANCE
                .isEnabled(IntegrationType.ThaumicTinkerer)
                        ? IntegrationRegistry.INSTANCE.getInstance(IntegrationType.ThaumicTinkerer)
                        : null);

        if (tt != null && tt.isTransvectorInterface(te)) {
            te = tt.getTile(te);
        }

        if (EIO && te instanceof TileConduitBundle tcb) {
            return new AdaptorConduitBandle(tcb, d);
        } else if (te instanceof EntityPlayer) {
            return new AdaptorIInventory(new AdaptorPlayerInventory(((EntityPlayer) te).inventory, false));
        } else if (te instanceof ArrayList) {
            @SuppressWarnings("unchecked")
            final ArrayList<ItemStack> list = (ArrayList<ItemStack>) te;

            return new AdaptorList(list);
        } else if (bs != null && bs.isStorageCrate(te)) {
            return bs.getAdaptor(te, d);
        } else if (te instanceof TileEntityChest) {
            return new AdaptorIInventory(Platform.GetChestInv(te));
        } else if (te instanceof ISidedInventory si) {
            if (te instanceof TileInterface) {
                return new AdaptorDualityInterface(new WrapperMCISidedInventory(si, d), (IInterfaceHost) te);
            } else if (te instanceof TileCableBus) {
                IPart part = ((TileCableBus) te).getPart(d);
                if (part instanceof IInterfaceHost host) {
                    return new AdaptorDualityInterface(new WrapperMCISidedInventory(si, d), host);
                } else if (part instanceof PartP2PItems p2p) {
                    return new AdaptorP2PItem(p2p);
                } else if (part instanceof PartP2PLiquids p2p) {
                    return new AdaptorP2PFluid(p2p, d);
                }
            } else if (te instanceof TileChest) {
                return new AdaptorMEChest(new WrapperMCISidedInventory(si, d), (TileChest) te);
            } else if (te instanceof IFluidHandler tank
                    && !((tank.getTankInfo(d) == null || !(tank.getTankInfo(d).length > 0)))) {
                        return new AdaptorFluidHandler(tank, d);
                    }

            final int[] slots = si.getAccessibleSlotsFromSide(d.ordinal());
            if (si.getSizeInventory() > 0 && slots != null && slots.length > 0) {
                return new AdaptorIInventory(new WrapperMCISidedInventory(si, d));
            }
        } else if (te instanceof IFluidHandler tank
                && !((tank.getTankInfo(d) == null || !(tank.getTankInfo(d).length > 0)))) {
                    return new AdaptorFluidHandler(tank, d);
                } else
            if (te instanceof IInventory i) {
                if (i.getSizeInventory() > 0) {
                    return new AdaptorIInventory(i);
                }
            }

        return null;
    }

    public static InventoryAdaptor getNullAdaptor() {
        return new AdaptorNull();

    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out, int iteration) {
        return out;
    }

    // return what was extracted.
    public abstract ItemStack removeItems(int amount, ItemStack filter, IInventoryDestination destination);

    public abstract ItemStack simulateRemove(int amount, ItemStack filter, IInventoryDestination destination);

    // return what was extracted.
    public abstract ItemStack removeSimilarItems(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination);

    public abstract ItemStack simulateSimilarRemove(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination);

    // return what isn't used...
    public abstract ItemStack addItems(ItemStack toBeAdded);

    /**
     * @param insertionMode advice implementation on how ItemStacks should be inserted. Might not has an effect
     *                      whatsoever!
     */
    public ItemStack addItems(ItemStack toBeAdded, InsertionMode insertionMode) {
        return addItems(toBeAdded);
    }

    public abstract ItemStack simulateAdd(ItemStack toBeSimulated);

    /**
     * @param insertionMode advice implementation on how ItemStacks should be inserted. Might not has an effect
     *                      whatsoever!
     * @return The leftover itemstack, or null if everything could be inserted
     */
    public ItemStack simulateAdd(ItemStack toBeSimulated, InsertionMode insertionMode) {
        return simulateAdd(toBeSimulated);
    }

    // AE
    public IAEStack<?> removeStack(int amount, IAEStack<?> filter, IInventoryDestination destination) {
        return null;
    }

    public IAEStack<?> simulateStackRemove(int amount, IAEStack<?> filter, IInventoryDestination destination) {
        return null;
    }

    public IAEStack<?> removeSimilarItems(int amount, IAEStack<?> filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return null;
    }

    public IAEStack<?> simulateSimilarRemove(int amount, IAEStack<?> filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return null;
    }

    public IAEStack<?> addStack(IAEStack<?> toBeAdded) {
        if (toBeAdded.getStackSize() < Integer.MAX_VALUE) {
            if (toBeAdded instanceof IAEItemStack ais) {
                return AEItemStack.create(addItems(ais.getItemStack()));
            }
        }
        return toBeAdded;
    }

    public IAEStack<?> addStack(IAEStack<?> toBeAdded, InsertionMode insertionMode) {
        return addStack(toBeAdded);
    }

    public IAEStack<?> simulateAddStack(IAEStack<?> toBeSimulated) {
        if (toBeSimulated.getStackSize() < Integer.MAX_VALUE) {
            if (toBeSimulated instanceof IAEItemStack ais) {
                return AEItemStack.create(simulateAdd(ais.getItemStack()));
            }
        }
        return toBeSimulated;
    }

    public IAEStack<?> simulateAddStack(IAEStack<?> toBeSimulated, InsertionMode insertionMode) {
        return simulateAddStack(toBeSimulated);
    }

    public abstract boolean containsItems();
}
