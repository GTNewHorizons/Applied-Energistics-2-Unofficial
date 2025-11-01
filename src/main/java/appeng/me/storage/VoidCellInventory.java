package appeng.me.storage;

import javax.annotation.Nonnull;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.ICellInventory;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IItemList;
import appeng.items.storage.ItemVoidStorageCell;
import appeng.tile.inventory.IAEStackInventory;

public class VoidCellInventory<StackType extends IAEStack<StackType>> implements ICellInventory<StackType> {

    private final ItemStack cellItem;
    private final ItemVoidStorageCell cellType;

    protected VoidCellInventory(ItemStack is) {
        super();

        this.cellItem = is;
        if (this.cellItem.getItem() instanceof ItemVoidStorageCell type) {
            this.cellType = type;
        } else this.cellType = null;
    }

    public static IMEInventoryHandler<?> getCell(final ItemStack o, final StorageChannel sc) {
        if (sc == StorageChannel.ITEMS) return new ItemCellInventoryHandler(new VoidCellInventory<>(o));
        if (sc == StorageChannel.FLUIDS) return new FluidCellInventoryHandler(new VoidCellInventory<>(o));
        return null;
    }

    @Override
    public StackType injectItems(StackType input, Actionable type, BaseActionSource src) {
        return null;
    }

    @Override
    public StackType extractItems(StackType request, Actionable mode, BaseActionSource src) {
        return null;
    }

    @Override
    public IItemList<StackType> getAvailableItems(IItemList<StackType> out, int iteration) {
        return out;
    }

    @Override
    public StackType getAvailableItem(@Nonnull StackType request, int iteration) {
        return null;
    }

    @Override
    public StorageChannel getChannel() {
        return this.cellType.getStorageChannel();
    }

    @Override
    public ItemStack getItemStack() {
        return this.cellItem;
    }

    @Override
    public double getIdleDrain() {
        return 0;
    }

    @Override
    public FuzzyMode getFuzzyMode() {
        return this.cellType.getFuzzyMode(this.cellItem);
    }

    @Override
    @Deprecated
    public IInventory getConfigInventory() {
        return this.cellType.getConfigInventory(this.cellItem);
    }

    @Override
    public IAEStackInventory getConfigAEInventory() {
        return this.cellType.getConfigAEInventory(this.cellItem);
    }

    @Override
    public IInventory getUpgradesInventory() {
        return this.cellType.getUpgradesInventory(this.cellItem);
    }

    @Override
    public int getBytesPerType() {
        return 0;
    }

    @Override
    public boolean canHoldNewItem() {
        return true;
    }

    @Override
    public long getTotalBytes() {
        return 0;
    }

    @Override
    public long getFreeBytes() {
        return 0;
    }

    @Override
    public long getUsedBytes() {
        return 0;
    }

    @Override
    public long getTotalItemTypes() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getStoredItemCount() {
        return 0;
    }

    @Override
    public long getStoredItemTypes() {
        return 0;
    }

    @Override
    public long getRemainingItemTypes() {
        return Long.MAX_VALUE;
    }

    @Override
    public long getRemainingItemCount() {
        return 0;
    }

    @Override
    public long getRemainingItemsCountDist(StackType l) {
        return 0;
    }

    @Override
    public int getUnusedItemCount() {
        return 0;
    }

    @Override
    public int getStatusForCell() {
        return 1;
    }

    @Override
    public String getOreFilter() {
        return this.cellType.getOreFilter(this.cellItem);
    }
}
