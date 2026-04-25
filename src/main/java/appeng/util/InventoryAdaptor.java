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

import static appeng.util.Platform.isEIOLoaded;

import java.util.ArrayList;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.IFluidHandler;

import org.intellij.lang.annotations.MagicConstant;

import com.gtnewhorizon.gtnhlib.capability.CapabilityProvider;
import com.gtnewhorizon.gtnhlib.capability.item.ItemIO;
import com.gtnewhorizon.gtnhlib.util.ItemUtil;

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
import appeng.util.inv.AdaptorItemIO;
import appeng.util.inv.AdaptorList;
import appeng.util.inv.AdaptorMEChest;
import appeng.util.inv.AdaptorP2PFluid;
import appeng.util.inv.AdaptorP2PItem;
import appeng.util.inv.AdaptorPlayerInventory;
import appeng.util.inv.IInventoryDestination;
import appeng.util.inv.ItemSlot;
import appeng.util.inv.WrapperMCISidedInventory;
import appeng.util.item.AEItemStack;
import crazypants.enderio.conduit.TileConduitBundle;

public abstract class InventoryAdaptor implements Iterable<ItemSlot> {

    private static int counter = 0;
    /// Item adaptors may be returned. Absence means item adaptors will not be returned, if possible.
    public static final int ALLOW_ITEMS = 0b1 << counter++;
    /// Fluid adaptors may be returned. Absence means fluid adaptors will not be returned, if possible.
    public static final int ALLOW_FLUIDS = 0b1 << counter++;
    /// Adaptors must be insertable, if possible. Absence means they must not be insertable, if possible.
    public static final int FOR_INSERTS = 0b1 << counter++;
    /// Adaptors must be extractable, if possible. Absence means they must not be extractable, if possible.
    public static final int FOR_EXTRACTS = 0b1 << counter++;

    public static final int DEFAULT = ALLOW_ITEMS | ALLOW_FLUIDS | FOR_INSERTS | FOR_EXTRACTS;

    public static InventoryAdaptor getAdaptor(Object te, final ForgeDirection d) {
        return getAdaptor(te, d, DEFAULT);
    }

    // returns an appropriate adaptor, or null
    public static InventoryAdaptor getAdaptor(Object te, final ForgeDirection d,
            @MagicConstant(flagsFromClass = InventoryAdaptor.class) int flags) {
        if (te == null) {
            return null;
        }

        boolean invs = (flags & ALLOW_ITEMS) != 0;
        boolean tanks = (flags & ALLOW_FLUIDS) != 0;

        final IBetterStorage bs = IntegrationRegistry.INSTANCE.getInstanceIfEnabled(IntegrationType.BetterStorage);
        final IThaumicTinkerer tt = IntegrationRegistry.INSTANCE.getInstanceIfEnabled(IntegrationType.ThaumicTinkerer);

        if (tt != null && tt.isTransvectorInterface(te)) {
            te = tt.getTile(te);
        }

        if (te instanceof CapabilityProvider provider) {
            InventoryAdaptor adaptor = provider.getCapability(InventoryAdaptor.class, d);

            if (adaptor != null) return adaptor;
        }

        // spotless:off
        if (invs && isEIOLoaded && te instanceof TileConduitBundle tcb) {
            return new AdaptorConduitBandle(tcb, d);
        }

        if (invs && te instanceof EntityPlayer) {
            return new AdaptorIInventory(new AdaptorPlayerInventory(((EntityPlayer) te).inventory, false));
        }

        if (invs && te instanceof ArrayList) {
            @SuppressWarnings("unchecked")
            final ArrayList<ItemStack> list = (ArrayList<ItemStack>) te;

            return new AdaptorList(list);
        }

        if (invs && bs != null && bs.isStorageCrate(te)) {
            return bs.getAdaptor(te, d);
        }

        if (invs && te instanceof TileEntityChest) {
            return new AdaptorIInventory(Platform.GetChestInv(te));
        }

        if (te instanceof ISidedInventory sided) {
            if (invs && te instanceof TileInterface) {
                return new AdaptorDualityInterface(new WrapperMCISidedInventory(sided, d), (IInterfaceHost) te);
            }

            if (te instanceof TileCableBus cableBus) {
                IPart part = cableBus.getPart(d);
                if (invs && part instanceof IInterfaceHost host) {
                    return new AdaptorDualityInterface(new WrapperMCISidedInventory(sided, d), host);
                }

                if (invs && part instanceof PartP2PItems p2p) {
                    return new AdaptorP2PItem(p2p);
                }

                if (tanks && part instanceof PartP2PLiquids p2p) {
                    return new AdaptorP2PFluid(p2p, d);
                }
            }

            if (invs && te instanceof TileChest) {
                return new AdaptorMEChest(new WrapperMCISidedInventory(sided, d), (TileChest) te);
            }
        }

        if (tanks && te instanceof IFluidHandler tank && !((tank.getTankInfo(d) == null || !(tank.getTankInfo(d).length > 0)))) {
            return new AdaptorFluidHandler(tank, d);
        }

        if (invs) {
            int ioFlags = 0;

            if ((flags & FOR_INSERTS) != 0) ioFlags |= ItemUtil.FOR_INSERTS;
            if ((flags & FOR_EXTRACTS) != 0) ioFlags |= ItemUtil.FOR_EXTRACTS;

            ItemIO itemIO = ItemUtil.getItemIO(te, d, ioFlags);

            if (itemIO != null) {
                return new AdaptorItemIO(itemIO);
            }
        }

        if (te instanceof ISidedInventory sided) {
            final int[] slots = sided.getAccessibleSlotsFromSide(d.ordinal());
            
            if (invs && sided.getSizeInventory() > 0 && slots != null && slots.length > 0) {
                return new AdaptorIInventory(new WrapperMCISidedInventory(sided, d));
            }
        }

        if (tanks && te instanceof IFluidHandler tank && !(tank.getTankInfo(d) == null || !(tank.getTankInfo(d).length > 0))) {
            return new AdaptorFluidHandler(tank, d);
        }

        if (invs && te instanceof IInventory i && i.getSizeInventory() > 0) {
            return new AdaptorIInventory(i);
        }
        // spotless:on

        return null;
    }

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

    public IAEStack<?> addStack(IAEStack<?> toBeAdded, InsertionMode insertionMode) {
        if (toBeAdded.getStackSize() < Integer.MAX_VALUE) {
            if (toBeAdded instanceof IAEItemStack ais) {
                return AEItemStack.create(addItems(ais.getItemStack(), insertionMode));
            }
        }
        return toBeAdded;
    }

    public IAEStack<?> simulateAddStack(IAEStack<?> toBeSimulated, InsertionMode insertionMode) {
        if (toBeSimulated.getStackSize() < Integer.MAX_VALUE) {
            if (toBeSimulated instanceof IAEItemStack ais) {
                return AEItemStack.create(simulateAdd(ais.getItemStack(), insertionMode));
            }
        }
        return toBeSimulated;
    }

    public abstract boolean containsItems();
}
