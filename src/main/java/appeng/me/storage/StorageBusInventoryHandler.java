package appeng.me.storage;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import javax.annotation.Nonnull;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.chunk.Chunk;

import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.storage.IMEInventory;
import appeng.api.storage.IMENetworkInventory;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.Platform;
import appeng.util.item.ItemFilterList;
import appeng.util.item.NetworkItemList;
import appeng.util.item.PrioritizedNetworkItemList;

public class StorageBusInventoryHandler<T extends IAEStack<T>> extends MEInventoryHandler<T> {

    private final Supplier<AccessRestriction> reshuffleAccess;
    private final TileEntity target;
    private final Chunk targetChunk;
    private final TileEntity[] otherTargets;
    private final Chunk[] otherChunks;

    public StorageBusInventoryHandler(IMEInventory<T> i, IAEStackType<T> type,
            Supplier<AccessRestriction> reshuffleAccess, TileEntity target) {
        super(i, type);
        this.reshuffleAccess = reshuffleAccess;
        List<TileEntity> targets = Platform.getInventoryTiles(target);
        this.target = targets.isEmpty() ? null : targets.get(0);
        this.targetChunk = this.target == null ? null
                : this.target.getWorldObj().getChunkFromBlockCoords(this.target.xCoord, this.target.zCoord);
        this.otherTargets = new TileEntity[Math.max(0, targets.size() - 1)];
        this.otherChunks = new Chunk[this.otherTargets.length];
        for (int index = 1; index < targets.size(); index++) {
            TileEntity tile = targets.get(index);
            this.otherTargets[index - 1] = tile;
            this.otherChunks[index - 1] = tile.getWorldObj().getChunkFromBlockCoords(tile.xCoord, tile.zCoord);
        }
    }

    public boolean isValid() {
        if (this.target == null || this.target.isInvalid() || !this.targetChunk.isChunkLoaded) return false;
        for (int index = 0; index < this.otherTargets.length; index++) {
            if (this.otherTargets[index].isInvalid() || !this.otherChunks[index].isChunkLoaded) return false;
        }
        return true;
    }

    @Override
    public boolean canAccept(T input) {
        return this.isValid() && super.canAccept(input);
    }

    @Override
    public T extractItems(T request, Actionable type, BaseActionSource src) {
        return this.isValid() ? super.extractItems(request, type, src) : null;
    }

    @Override
    public T getAvailableItem(@Nonnull T request, int iteration) {
        return this.isValid() ? super.getAvailableItem(request, iteration) : null;
    }

    @Override
    public AccessRestriction getReshuffleAccess() {
        return this.getAccess().restrictPermissions(this.reshuffleAccess.get())
                .restrictPermissions(super.getReshuffleAccess());
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out, int iteration) {
        return this.getAvailableItems(out, iteration, Optional.empty());
    }

    @Override
    public IItemList<T> getAvailableItems(final IItemList<T> out, int iteration, Optional<Predicate<T>> filter) {
        if (!this.isValid() || (!this.hasReadAccess && !isVisible())) {
            return out;
        }

        if (out instanceof ItemFilterList) return this.getAvailableItemsFilter(out, iteration);

        Predicate<T> storageBusFilter = $ -> true;

        if (this.isExtractFilterActive() && !this.getExtractPartitionList().isEmpty()) {
            storageBusFilter = this.getExtractFilterCondition();
        }

        final IItemList<T> availableItems = this.getInternal()
                .getAvailableItems((IItemList<T>) this.getStackType().createList(), iteration, filter);

        if (availableItems instanceof NetworkItemList networkItemList) {
            // when we cross between networks on a NetworkInventoryHandler dive, we need to break the "out" contract to
            // avoid modifying the passed in list which belongs to a different network (and would cause double-counting
            // for triangle-shaped networks)
            NetworkItemList<T> itemList = new NetworkItemList<>(networkItemList);
            itemList.addFilter(storageBusFilter);
            return itemList;
        } else {
            // for non-cross-network dives, we need to honor the "out" contract
            // and put the results in the passed in list
            for (T items : availableItems) {
                if (storageBusFilter == null || storageBusFilter.test(items)) {
                    out.add(items);
                }
            }
            return out;
        }
    }

    @Override
    public PrioritizedNetworkItemList<T> getAvailableItemsWithPriority(int iteration) {
        if (!this.isValid()) return new PrioritizedNetworkItemList<>(this.getExternalNetworkInventory());
        final Predicate<T> predicate = this.isExtractFilterActive() && !this.getExtractPartitionList().isEmpty()
                ? this.getExtractFilterCondition()
                : e -> true;
        return this.getAvailableItemsWithPriority(iteration, predicate);
    }

    private PrioritizedNetworkItemList<T> getAvailableItemsWithPriority(int iteration, Predicate<T> filterCondition) {
        final IMENetworkInventory<T> externalNetworkInventory = this.getExternalNetworkInventory();
        final PrioritizedNetworkItemList<T> available = externalNetworkInventory
                .getAvailableItemsWithPriority(iteration);

        final PrioritizedNetworkItemList<T> copy = new PrioritizedNetworkItemList<>(available);
        copy.addFilter(filterCondition);
        return copy;
    }
}
