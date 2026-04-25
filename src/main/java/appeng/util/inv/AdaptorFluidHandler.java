package appeng.util.inv;

import static appeng.util.Platform.isAE2FCLoaded;

import java.util.Collections;
import java.util.Iterator;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.glodblock.github.common.item.ItemFluidPacket;

import appeng.api.config.FuzzyMode;
import appeng.api.config.InsertionMode;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.InventoryAdaptor;
import appeng.util.item.AEItemStack;

public class AdaptorFluidHandler extends InventoryAdaptor {

    IFluidHandler fluidHandler;
    AdaptorIInventory itemHandler;
    ForgeDirection toAdaptor;

    public AdaptorFluidHandler(IFluidHandler tank, ForgeDirection direction) {
        fluidHandler = tank;
        toAdaptor = direction;
        if (tank instanceof ISidedInventory si) {
            final int[] slots = si.getAccessibleSlotsFromSide(direction.ordinal());
            if (si.getSizeInventory() > 0 && slots != null && slots.length > 0) {
                itemHandler = new AdaptorIInventory(new WrapperMCISidedInventory(si, direction));
            }
        } else if (tank instanceof IInventory i) {
            if (i.getSizeInventory() > 0) {
                itemHandler = new AdaptorIInventory(i);
            }
        }
    }

    @Override
    public ItemStack removeItems(int amount, ItemStack filter, IInventoryDestination destination) {
        return itemHandler != null ? itemHandler.removeItems(amount, filter, destination) : null;
    }

    @Override
    public ItemStack simulateRemove(int amount, ItemStack filter, IInventoryDestination destination) {
        return itemHandler != null ? itemHandler.simulateRemove(amount, filter, destination) : null;
    }

    @Override
    public ItemStack removeSimilarItems(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return itemHandler != null ? itemHandler.removeSimilarItems(amount, filter, fuzzyMode, destination) : null;
    }

    @Override
    public ItemStack simulateSimilarRemove(int amount, ItemStack filter, FuzzyMode fuzzyMode,
            IInventoryDestination destination) {
        return itemHandler != null ? itemHandler.simulateSimilarRemove(amount, filter, fuzzyMode, destination) : null;
    }

    @Override
    public ItemStack addItems(ItemStack toBeAdded) {
        return addItems(toBeAdded, InsertionMode.DEFAULT);
    }

    @Override
    public ItemStack addItems(ItemStack toBeAdded, InsertionMode insertionMode) {
        return itemHandler != null ? itemHandler.addItems(toBeAdded, insertionMode) : toBeAdded;
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated) {
        return simulateAdd(toBeSimulated, InsertionMode.DEFAULT);
    }

    @Override
    public ItemStack simulateAdd(ItemStack toBeSimulated, InsertionMode insertionMode) {
        return itemHandler != null ? itemHandler.simulateAdd(toBeSimulated, insertionMode) : toBeSimulated;
    }

    @Override
    public boolean containsItems() {
        return (itemHandler != null && itemHandler.containsItems()) || containsFluid();
    }

    private boolean containsFluid() {
        if (fluidHandler != null) {
            FluidTankInfo[] tankInfos = fluidHandler.getTankInfo(toAdaptor);
            if (tankInfos != null) {
                for (FluidTankInfo tankInfo : tankInfos) {
                    FluidStack fluid = tankInfo.fluid;
                    if (fluid != null && fluid.amount > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public IAEStack<?> addStack(IAEStack<?> toBeAdded, InsertionMode insertionMode) {
        if (toBeAdded.getStackSize() < Integer.MAX_VALUE) {
            if (toBeAdded instanceof IAEItemStack ais) {
                return AEItemStack.create(addItems(ais.getItemStack(), insertionMode));
            } else if (toBeAdded instanceof IAEFluidStack ifs) {
                int rest = fluidHandler.fill(toAdaptor, ifs.getFluidStack(), true);
                if (rest == toBeAdded.getStackSize()) return null;
                return toBeAdded.setStackSize(toBeAdded.getStackSize() - rest);
            }
        }
        return toBeAdded;
    }

    @Override
    public IAEStack<?> simulateAddStack(IAEStack<?> toBeSimulated, InsertionMode insertionMode) {
        if (toBeSimulated.getStackSize() < Integer.MAX_VALUE) {
            if (toBeSimulated instanceof IAEItemStack ais) {
                return AEItemStack.create(simulateAdd(ais.getItemStack(), insertionMode));
            } else if (toBeSimulated instanceof IAEFluidStack ifs) {
                int rest = fluidHandler.fill(toAdaptor, ifs.getFluidStack(), false);
                if (rest == toBeSimulated.getStackSize()) return null;
                return toBeSimulated.setStackSize(toBeSimulated.getStackSize() - rest);
            }
        }
        return toBeSimulated;
    }

    @Override
    public Iterator<ItemSlot> iterator() {
        FluidTankInfo[] info = null;
        if (fluidHandler != null) {
            info = fluidHandler.getTankInfo(toAdaptor);
        }
        // Null check is needed because some tank infos return null (EIO conduits...)
        if (info == null) {
            info = new FluidTankInfo[0];
        }
        return new SlotIterator(info, itemHandler != null ? itemHandler.iterator() : Collections.emptyIterator());
    }

    private static class SlotIterator implements Iterator<ItemSlot> {

        private final FluidTankInfo[] tanks;
        private final Iterator<ItemSlot> itemSlots;
        private int nextSlotIndex = 0;

        SlotIterator(FluidTankInfo[] tanks, Iterator<ItemSlot> itemSlots) {
            this.tanks = tanks;
            this.itemSlots = itemSlots;
        }

        @Override
        public boolean hasNext() {
            return itemSlots.hasNext() || nextSlotIndex < tanks.length;
        }

        @Override
        public ItemSlot next() {
            if (isAE2FCLoaded && nextSlotIndex < tanks.length) {
                FluidStack fluid = tanks[nextSlotIndex].fluid;
                ItemSlot slot = new ItemSlot();
                slot.setSlot(nextSlotIndex++);
                slot.setItemStack(fluid != null ? ItemFluidPacket.newStack(fluid) : null);
                slot.setExtractable(false);
                return slot;
            } else {
                ItemSlot slot = itemSlots.next();
                slot.setSlot(nextSlotIndex++);
                return slot;
            }
        }
    }
}
