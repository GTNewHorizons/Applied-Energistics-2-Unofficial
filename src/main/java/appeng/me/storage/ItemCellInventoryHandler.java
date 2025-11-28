package appeng.me.storage;

import appeng.api.AEApi;
import appeng.api.config.FuzzyMode;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.OreFilteredList;
import appeng.util.prioitylist.PrecisePriorityList;

public class ItemCellInventoryHandler extends CellInventoryHandler<IAEItemStack> {

    public ItemCellInventoryHandler(IMEInventory<IAEItemStack> c) {
        super(c, StorageChannel.ITEMS);
    }

    @Override
    protected void setOreFilteredList(String filter) {
        this.setPartitionList(new OreFilteredList(filter));
    }

    @Override
    protected void setPriorityList(boolean hasFuzzy, IAEStackInventory config, FuzzyMode fzMode) {
        final IItemList<IAEItemStack> priorityList = AEApi.instance().storage().createItemList();
        for (int x = 0; x < config.getSizeInventory(); x++) {
            final IAEStack<?> aes = config.getAEStackInSlot(x);
            if (aes != null) {
                priorityList.add((IAEItemStack) aes);
            }
        }
        if (!priorityList.isEmpty()) {
            if (hasFuzzy) {
                this.setPartitionList(new FuzzyPriorityList<>(priorityList, fzMode));
            } else {
                this.setPartitionList(new PrecisePriorityList<>(priorityList));
            }
        }
    }

    @Override
    public StorageChannel getStorageChannel() {
        return StorageChannel.ITEMS;
    }
}
