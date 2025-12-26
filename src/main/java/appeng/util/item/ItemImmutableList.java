package appeng.util.item;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;

/**
 * Represents a wrapper for {@code IItemList<IAEItemStack>}. This class aggregates one or more instances of
 * {@code IItemList<IAEItemStack>} and provides a read-only interface for accessing their contents.
 */
public final class ItemImmutableList implements IItemList<IAEItemStack> {

    private final IItemList<IAEItemStack>[] lists;

    @SafeVarargs
    public ItemImmutableList(final IItemList<IAEItemStack>... itemLists) {
        if (itemLists == null || itemLists.length == 0) {
            throw new IllegalArgumentException("ItemImmutableList must be initialized with at least one list");
        }
        lists = Arrays.copyOf(itemLists, itemLists.length);
    }

    @Override
    public void addStorage(IAEItemStack option) {
        throw new UnsupportedOperationException("Cannot add to immutable list");
    }

    @Override
    public void addCrafting(IAEItemStack option) {
        throw new UnsupportedOperationException("Cannot add to immutable list");
    }

    @Override
    public void addRequestable(IAEItemStack option) {
        throw new UnsupportedOperationException("Cannot add to immutable list");
    }

    @Override
    public IAEItemStack getFirstItem() {
        for (IItemList<IAEItemStack> list : lists) {
            if (!list.isEmpty()) {
                return list.getFirstItem();
            }
        }
        return null;
    }

    @Override
    public int size() {
        int totalSize = 0;
        for (IItemList<IAEItemStack> list : lists) {
            totalSize += list.size();
        }
        return totalSize;
    }

    @Override
    public Iterator<IAEItemStack> iterator() {
        return new Iterator<>() {

            private int currentListIndex = 0;
            private Iterator<IAEItemStack> currentIterator = lists[currentListIndex].iterator();

            @Override
            public boolean hasNext() {
                if (currentIterator.hasNext()) {
                    return true;
                }
                if (currentListIndex < lists.length - 1) {
                    currentListIndex++;
                    currentIterator = lists[currentListIndex].iterator();
                    return hasNext();
                }
                return false;
            }

            @Override
            public IAEItemStack next() {
                return currentIterator.next();
            }
        };
    }

    @Override
    public void resetStatus() {
        throw new UnsupportedOperationException("Cannot reset immutable list");
    }

    @Override
    public void add(IAEItemStack option) {
        throw new UnsupportedOperationException("Cannot add to immutable list");
    }

    @Override
    public IAEItemStack findPrecise(IAEItemStack i) {
        IAEItemStack result = null;
        for (IItemList<IAEItemStack> list : lists) {
            IAEItemStack found = list.findPrecise(i);
            if (found != null) {
                if (result == null) {
                    result = found.copy();
                } else {
                    result.setStackSize(result.getStackSize() + found.getStackSize());
                }
            }
        }
        return result;
    }

    @Override
    public Collection<IAEItemStack> findFuzzy(IAEItemStack input, FuzzyMode fuzzy) {
        Collection<IAEItemStack> found = new ArrayList<>(lists[0].findFuzzy(input, fuzzy));
        for (int i = 1; i < lists.length; i++) {
            IItemList<IAEItemStack> itemsList = lists[i];
            skip: for (IAEItemStack item : itemsList.findFuzzy(input, fuzzy)) {
                for (IAEItemStack existing : found) {
                    if (existing.isSameType(item)) {
                        existing.setStackSize(existing.getStackSize() + item.getStackSize());
                        continue skip;
                    }
                }
                found.add(item.copy());
            }
        }
        return found;
    }

    @Override
    public boolean isEmpty() {
        for (IItemList<IAEItemStack> itemsList : lists) {
            if (!itemsList.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean hasWriteAccess() {
        return false;
    }

    @Override
    public IAEStackType<IAEItemStack> getStackType() {
        return ITEM_STACK_TYPE;
    }
}
