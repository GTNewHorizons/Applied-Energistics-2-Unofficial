package appeng.me.storage;

import javax.annotation.Nonnull;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.config.IncludeExclude;
import appeng.api.config.Upgrades;
import appeng.api.exceptions.AppEngException;
import appeng.api.implementations.items.IUpgradeModule;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventoryHandler;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;
import appeng.items.storage.ItemVoidStorageCell;
import appeng.util.item.AEItemStack;
import appeng.util.prioitylist.FuzzyPriorityList;
import appeng.util.prioitylist.OreFilteredList;
import appeng.util.prioitylist.PrecisePriorityList;

public class VoidCellInventory extends MEInventoryHandler<IAEItemStack> {

    private final boolean fuzzy;

    protected VoidCellInventory(final ItemStack o) throws AppEngException {
        super(new NullInventory<>(), StorageChannel.ITEMS);
        if (o == null || !(o.getItem() instanceof ItemVoidStorageCell cell)) {
            throw new AppEngException("ItemStack was used as a void cell, but was not a void cell!");
        }
        final IInventory upgrades = cell.getUpgradesInventory(o);
        final IInventory config = cell.getConfigInventory(o);
        final FuzzyMode fzMode = cell.getFuzzyMode(o);
        final String filter = cell.getOreFilter(o);
        boolean hasInverter = false;
        boolean hasFuzzy = false;
        boolean hasOreFilter = false;

        for (int x = 0; x < upgrades.getSizeInventory(); x++) {
            final ItemStack is = upgrades.getStackInSlot(x);
            if (is != null && is.getItem() instanceof IUpgradeModule) {
                final Upgrades u = ((IUpgradeModule) is.getItem()).getType(is);
                if (u != null) {
                    switch (u) {
                        case FUZZY -> hasFuzzy = true;
                        case INVERTER -> hasInverter = true;
                        case ORE_FILTER -> hasOreFilter = true;
                        default -> {}
                    }
                }
            }
        }
        this.fuzzy = hasFuzzy;
        this.setWhitelist(hasInverter ? IncludeExclude.BLACKLIST : IncludeExclude.WHITELIST);
        if (hasOreFilter && !filter.isEmpty()) {
            this.setPartitionList(new OreFilteredList(filter));
        } else {
            final IItemList<IAEItemStack> priorityList = AEApi.instance().storage().createItemList();
            for (int x = 0; x < config.getSizeInventory(); x++) {
                final ItemStack is = config.getStackInSlot(x);
                if (is != null) {
                    priorityList.add(AEItemStack.create(is));
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
    }

    public static IMEInventoryHandler<IAEItemStack> getCell(final ItemStack o) {
        try {
            return new VoidCellInventory(o);
        } catch (final AppEngException e) {
            return null;
        }
    }

    @Override
    public IAEItemStack injectItems(IAEItemStack input, Actionable type, BaseActionSource src) {
        return this.canAccept(input) ? null : input;
    }

    @Override
    public IAEItemStack extractItems(IAEItemStack request, Actionable mode, BaseActionSource src) {
        return null;
    }

    @Override
    public IItemList<IAEItemStack> getAvailableItems(IItemList<IAEItemStack> out) {
        return out;
    }

    @Override
    public IAEItemStack getAvailableItem(@Nonnull IAEItemStack request) {
        return null;
    }

    @Override
    public AccessRestriction getAccess() {
        // you shouldn't try to read anything in void cell.
        return AccessRestriction.WRITE;
    }

    @Override
    public boolean canAccept(IAEItemStack input) {
        if (this.getWhitelist() == IncludeExclude.BLACKLIST && this.getPartitionList().isListed(input)) {
            return false;
        }
        if (this.getPartitionList().isEmpty() || this.getWhitelist() == IncludeExclude.BLACKLIST) {
            return true;
        }
        return this.getPartitionList().isListed(input);
    }

    @Override
    public int getSlot() {
        return 0;
    }

    public boolean isPreformatted() {
        return !this.getPartitionList().isEmpty();
    }

    public boolean isFuzzy() {
        return this.fuzzy;
    }
}
