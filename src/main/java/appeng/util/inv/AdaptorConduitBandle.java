package appeng.util.inv;

import java.util.Iterator;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;

import appeng.api.config.FuzzyMode;
import appeng.api.config.InsertionMode;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.InventoryAdaptor;
import crazypants.enderio.conduit.TileConduitBundle;
import crazypants.enderio.conduit.item.IItemConduit;
import crazypants.enderio.conduit.liquid.ILiquidConduit;

public class AdaptorConduitBandle extends InventoryAdaptor {

    IItemConduit itemConduit;
    ILiquidConduit fluidConduit;
    ForgeDirection toAdaptor;

    public AdaptorConduitBandle(TileConduitBundle tileEntity, ForgeDirection direction) {
        toAdaptor = direction;
        IItemConduit ic = tileEntity.getConduit(IItemConduit.class);
        if (ic != null && ic.isConnectedTo(direction)) {
            itemConduit = ic;
        } else {
            itemConduit = null;
        }
        ILiquidConduit ilc = tileEntity.getConduit(ILiquidConduit.class);
        if (ilc != null && ilc.isConnectedTo(direction)) {
            fluidConduit = ilc;
        } else {
            fluidConduit = null;
        }
    }

    @Override
    public ItemStack removeItems(int amount, ItemStack filter, IInventoryDestination destination) {
        return null; // we cant
    }

    @Override
    public ItemStack simulateRemove(int amount, ItemStack filter, IInventoryDestination destination) {
        return null; // we cant test
    }

    @Override
    public ItemStack removeSimilarItems(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return null; // we cant test
    }

    @Override
    public ItemStack simulateSimilarRemove(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return null; // we cant test
    }

    @Override
    public ItemStack addItems(ItemStack toBeAdded) {
        return itemConduit != null ? itemConduit.insertItem(toAdaptor, toBeAdded) : toBeAdded;
    }

    @Override
    public IAEStack<?> addStack(IAEStack<?> toBeAdded, InsertionMode insertionMode) {
        if (toBeAdded.getStackSize() < Integer.MAX_VALUE) {
            if (toBeAdded instanceof IAEFluidStack ifs) {
                return fill(ifs, true);
            } else return super.addStack(toBeAdded, insertionMode);
        }
        return toBeAdded;
    }

    @Override
    public IAEStack<?> simulateAddStack(IAEStack<?> toBeSimulated, InsertionMode insertionMode) {
        if (toBeSimulated.getStackSize() < Integer.MAX_VALUE) {
            if (toBeSimulated instanceof IAEFluidStack ifs) {
                return fill(ifs, false);
            } else return super.simulateAddStack(toBeSimulated, insertionMode);
        }
        return toBeSimulated;
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated) {
        return null; // we cant test
    }

    private IAEStack<?> fill(IAEFluidStack ifs, boolean doFill) {
        if (fluidConduit != null) {
            FluidStack fs = ifs.getFluidStack();
            int originalSize = fs.amount;
            int inserted = fluidConduit.fill(toAdaptor, fs, doFill);

            if (inserted == 0) {
                return ifs;
            } else if (inserted < originalSize) {
                ifs.setStackSize(originalSize - inserted);
            } else {
                return null;
            }
        }
        return ifs;
    }

    @Override
    public boolean containsItems() {
        // cant check item conduit
        if (fluidConduit != null) {
            for (FluidTankInfo tank : fluidConduit.getTankInfo(toAdaptor)) {
                FluidStack fluid = tank.fluid;
                if (fluid != null && fluid.amount > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Iterator<ItemSlot> iterator() {
        return null;
    }
}
