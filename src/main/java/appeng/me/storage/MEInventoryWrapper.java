package appeng.me.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

public class MEInventoryWrapper<T extends IAEStack<T>> implements IMEInventory<T> {

    private final IMEInventory<T> delegate;
    private final IAEStackType<T> type;

    public MEInventoryWrapper(IMEInventory<T> delegate, IAEStackType<T> type) {
        this.delegate = delegate;
        this.type = type;
    }

    @Override
    public T injectItems(T input, Actionable type, BaseActionSource src) {
        return this.delegate.injectItems(input, type, src);
    }

    @Override
    public T extractItems(T request, Actionable mode, BaseActionSource src) {
        return this.delegate.extractItems(request, mode, src);
    }

    @Override
    public IItemList<T> getAvailableItems(IItemList<T> out, int iteration) {
        return this.delegate.getAvailableItems(out, iteration);
    }

    @Override
    public IAEStackType<?> getStackType() {
        return this.type;
    }
}
