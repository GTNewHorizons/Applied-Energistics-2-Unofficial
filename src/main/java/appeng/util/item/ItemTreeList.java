package appeng.util.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import appeng.api.config.FuzzyMode;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IItemList;

public class ItemTreeList implements IItemList<IAEItemStack> {

    /**
     * Used for display repo to view stacks.
     */
    private final List<IAEItemStack> view;
    /**
     * Used for display stack.
     */
    private final List<ItemStack> dsp;
    /**
     * Used for sorting automatically. Every item in this tree must exist in the item set.
     */
    private final ItemTree tree;
    /**
     * Filter to use to blacklist items from being displayed
     */
    private Predicate<IAEItemStack> filter;
    /**
     * If true, when this list is refreshed, items are resorted. Otherwise, items are appended to the end and their slot
     * indices are not updated.
     */
    public boolean shouldResort;
    /**
     * When {@link #shouldResort} is false, new items are added to this map instead, and appended to the end.
     */
    private final HashMap<IAEItemStack, IAEItemStack> dirtyStacks;

    public ItemTreeList(Comparator<IAEItemStack> comparator, Predicate<IAEItemStack> filter) {
        this.dsp = new ArrayList<>();
        this.view = new ArrayList<>();
        this.tree = new ItemTree(stackComparator(comparator));
        this.filter = filter;
        this.shouldResort = true;
        this.dirtyStacks = new HashMap<>();
    }

    public List<ItemStack> displayList() {
        return dsp;
    }

    public List<IAEItemStack> viewList() {
        return view;
    }

    /**
     * Refresh with the new filter.
     */
    public void refresh(Predicate<IAEItemStack> filter) {
        this.filter = filter;
        refresh();
    }

    /**
     * Refresh with the new comparator.
     */
    public void refresh(Comparator<IAEItemStack> comparator) {
        tree.setComparator(comparator);
        refresh();
    }

    /**
     * Update the view and displayed stacks. A full refresh does the following:
     * <ol>
     * <li>Clears the view and display lists</li>
     * <li>Tests each item in the item list against the filter.</li>
     * <li>Adds items that pass the test into the view/display lists.</li>
     * </ol>
     * A full refresh is relatively expensive, so it should be avoided where possible. <br/>
     * If {@link #shouldResort} is false, it may negatively impact performance.
     */
    public void refresh() {
        dsp.clear();
        if (shouldResort) {
            view.clear();
            if (filter != null) {
                tree.forEach((item) -> {
                    if (filter.test(item)) {
                        view.add(item);
                        dsp.add(item.getItemStack());
                    }
                });
            } else {
                tree.forEach((item) -> {
                    view.add(item);
                    dsp.add(item.getItemStack());
                });
            }
        } else {
            Iterator<IAEItemStack> it = view.iterator();
            while (it.hasNext()) {
                IAEItemStack stack = it.next();
                if (!filter.test(stack)) {
                    it.remove();
                } else {
                    dirtyStacks.remove(stack);
                }
            }
            for (IAEItemStack stack : dirtyStacks.keySet()) {
                if (stack.isMeaningful()) {
                    view.add(stack);
                }
            }
            // toList() not in java 8 even w/ jabel
            dsp.addAll(view.stream().map(IAEItemStack::getItemStack).collect(Collectors.toList()));
        }
        dirtyStacks.clear();
    }

    public void clear() {
        this.tree.clear();
        this.view.clear();
        this.dsp.clear();
    }

    public int fullSize() {
        return this.tree.size();
    }

    public void updateItem(IAEItemStack stack) {
        this.tree.update(stack);
    }

    @Override
    public void addStorage(IAEItemStack option) {
        if (option == null) return;
        IAEItemStack stack = tree.getOrDefault(option, option.copy());
        tree.add(stack);
        // Mark the node to be updated
        if (dirtyStacks.containsKey(stack)) {
            dirtyStacks.get(stack).incStackSize(stack.getStackSize());
        } else {
            dirtyStacks.put(stack, stack);
        }
    }

    @Override
    public void addCrafting(IAEItemStack option) {
        if (option == null) return;
        IAEItemStack stack = this.tree.getOrDefault(option, option.copy());
        // Stack does not exist in the list yet
        stack.setStackSize(0).setCraftable(true);
        this.tree.add(stack);
        // No resort, items should be bumped to the end of the list
        if (dirtyStacks.containsKey(stack)) {
            dirtyStacks.get(stack).setCraftable(true);
        } else {
            dirtyStacks.put(stack, stack);
        }
    }

    @Override
    public void addRequestable(IAEItemStack option) {
        if (option == null) return;
        final IAEItemStack st = this.tree.get(option);
        if (st != null) {
            st.setCountRequestable(st.getCountRequestable() + option.getCountRequestable());
        } else {
            final IAEItemStack copy = option.copy();
            copy.setStackSize(0).setCraftable(false).setCountRequestable(option.getCountRequestable());
            this.tree.add(copy);
            if (!shouldResort) {
                this.dirtyStacks.put(copy, copy);
            }
        }
    }

    @Override
    public IAEItemStack getFirstItem() {
        return this.tree.findMin();
    }

    @Override
    public int size() {
        return this.view.size();
    }

    @Override
    public Iterator<IAEItemStack> iterator() {
        return view.iterator();
    }

    @Override
    public void resetStatus() {
        this.tree.resetItems();
    }

    @Override
    public void add(IAEItemStack option) {
        addStorage(option);
    }

    @Override
    public IAEItemStack findPrecise(IAEItemStack i) {
        IAEItemStack stack = this.tree.get(i);
        if (stack != null) {
            dirtyStacks.putIfAbsent(stack, stack);
        }
        return stack;
    }

    @Override
    public Collection<IAEItemStack> findFuzzy(IAEItemStack input, FuzzyMode fuzzy) {
        if (input == null) {
            return Collections.emptyList();
        }

        final AEItemStack ais = (AEItemStack) input;

        if (ais.isOre()) {
            final OreReference or = ais.getDefinition().getIsOre();

            if (or.getAEEquivalents().size() == 1) {
                final IAEItemStack is = or.getAEEquivalents().get(0);

                return this
                        .findFuzzyDamage((AEItemStack) is, fuzzy, is.getItemDamage() == OreDictionary.WILDCARD_VALUE);
            } else {
                final Collection<IAEItemStack> output = new ArrayList<>();

                for (final IAEItemStack is : or.getAEEquivalents()) {
                    output.addAll(
                            this.findFuzzyDamage(
                                    (AEItemStack) is,
                                    fuzzy,
                                    is.getItemDamage() == OreDictionary.WILDCARD_VALUE));
                }

                return output;
            }
        }
        return this.findFuzzyDamage(ais, fuzzy, false);
    }

    @Override
    public boolean isEmpty() {
        return this.tree.isEmpty();
    }

    private Comparator<IAEItemStack> stackComparator(Comparator<IAEItemStack> cmp) {
        return (stack1, stack2) -> stack1.isSameType(stack2) ? 0 : cmp.compare(stack1, stack2);
    }

    private Collection<IAEItemStack> findFuzzyDamage(final AEItemStack filter, final FuzzyMode fuzzy,
            final boolean ignoreMeta) {
        final IAEItemStack low = filter.getLow(fuzzy, ignoreMeta);
        final IAEItemStack high = filter.getHigh(fuzzy, ignoreMeta);

        return this.tree.itemsBetween(low, high);
    }
}
