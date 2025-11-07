package appeng.me.storage;

import com.glodblock.github.util.Util;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEFluidStack;
import appeng.util.prioitylist.PrecisePriorityList;

public class FluidCellInventoryHandler extends CellInventoryHandler<IAEFluidStack> {

    public FluidCellInventoryHandler(IMEInventory<IAEFluidStack> c) {
        super(c, StorageChannel.FLUIDS);
    }

    @Override
    protected void setOreFilteredList(String filter) {
        // no ore dict
    }

    @Override
    protected void setPriorityList(boolean hasFuzzy, IAEStackInventory config, FuzzyMode fzMode) {
        final IItemList<IAEFluidStack> priorityList = AEApi.instance().storage().createFluidList();
        for (int x = 0; x < config.getSizeInventory(); x++) {
            final IAEStack<?> aes = config.getAEStackInSlot(x);
            final IAEFluidStack fluid = aes instanceof IAEFluidStack afs ? afs
                    : aes instanceof IAEItemStack ais ? AEFluidStack.create(Util.getFluidFromItem(ais.getItemStack()))
                            : null;
            if (fluid != null) {
                fluid.setStackSize(1);
                priorityList.add(fluid);

                // convert partition on cells in network
                if (aes instanceof IAEItemStack) {
                    config.putAEStackInSlot(x, fluid);
                    config.markDirty();
                }
            }
        }
        if (!priorityList.isEmpty()) {
            this.setPartitionList(new PrecisePriorityList<>(priorityList));
        }
    }

    @Override
    public StorageChannel getStorageChannel() {
        return StorageChannel.FLUIDS;
    }
}
