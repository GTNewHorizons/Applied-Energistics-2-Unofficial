package appeng.tile.misc;

import java.util.IdentityHashMap;
import java.util.Map;

import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.tile.grid.AENetworkTile;
import appeng.tile.inventory.AppEngInternalInventory;
import appeng.tile.inventory.IAEStackInventory;
import appeng.tile.inventory.IIAEStackInventory;

public class TileSuperMEReplenisher extends AENetworkTile implements IMEInventory<IAEStack<?>>, IIAEStackInventory {

    private final Map<IAEStackType<?>, IItemList> lists = new IdentityHashMap<>();
    private final IAEStackInventory config = new IAEStackInventory(this, 11 * 9, StorageName.CONFIG);
    private final AppEngInternalInventory cells = new AppEngInternalInventory(null, 6) {

        @Override
        public void markDirty() {
            TileSuperMEReplenisher.this.markDirty();
        }
    };

    public TileSuperMEReplenisher() {
        this.getProxy().setFlags(GridFlags.REQUIRE_CHANNEL);
        this.getProxy().setIdlePowerUsage(4.0);

        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.lists.put(type, type.createList());
        }
    }

    @Override
    public IItemList<IAEStack<?>> getAvailableItems(IItemList<IAEStack<?>> out, int iteration) {
        final IItemList<?> list = this.lists.get(out.getStackType());
        list.forEach(out::add);
        return out;
    }

    @Override
    public IAEStack<?> injectItems(IAEStack<?> input, Actionable type, BaseActionSource src) {
        if (!(type == Actionable.SIMULATE)) this.lists.get(input.getStackType()).add(input);
        return null;
    }

    @Override
    public IAEStack<?> extractItems(IAEStack<?> request, Actionable mode, BaseActionSource src) {
        if (request == null) return null;

        final IAEStack<?> stack = this.lists.get(request.getStackType()).findPrecise(request);
        if (stack == null || stack.getStackSize() <= 0) return null;

        if (stack.getStackSize() >= request.getStackSize()) {
            if (mode == Actionable.MODULATE) stack.decStackSize(request.getStackSize());
            return request;
        }

        final IAEStack<?> ret = request.copy();
        ret.setStackSize(stack.getStackSize());

        if (mode == Actionable.MODULATE) stack.reset();

        return ret;
    }

    public Map<IAEStackType<?>, IItemList> getStorage() {
        return this.lists;
    }

    @Override
    public void saveAEStackInv() {
        this.markDirty();
    }

    @Override
    public IAEStackInventory getAEInventoryByName(StorageName name) {
        if (name == StorageName.CONFIG) return this.config;
        return null;
    }

    public AppEngInternalInventory getCellInventory() {
        return this.cells;
    }
}
