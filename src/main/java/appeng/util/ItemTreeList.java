package appeng.util;

import java.util.*;
import java.util.function.Predicate;

import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEItemStack;

public class ItemTreeList implements IItemTree {

    private final ArrayList<ItemStack> dsp;
    private TreeSet<IAEItemStack> list;
    private final ArrayList<IAEItemStack> view;

    /**
     * Filter.
     */
    private Predicate<IAEItemStack> filter;

    public ItemTreeList(Comparator<IAEItemStack> comparator, Predicate<IAEItemStack> filter) {
        this.dsp = new ArrayList<>();
        this.view = new ArrayList<>();
        this.list = new TreeSet<>(stackComparator(comparator));
        this.filter = filter;
    }

    @Override
    public List<ItemStack> displayList() {
        return dsp;
    }

    @Override
    public List<IAEItemStack> viewList() {
        return view;
    }

    @Override
    public void refresh(Comparator<IAEItemStack> comparator, Predicate<IAEItemStack> filter) {
        refresh(comparator);
        refresh(filter);
    }

    @Override
    public void refresh(Predicate<IAEItemStack> filter) {
        this.filter = filter;
        view.clear();
        dsp.clear();
        if (filter != null) {
            list.forEach(item -> {
                if (filter.test(item)) {
                    view.add(item);
                    dsp.add(item.getItemStack());
                }
            });
        } else {
            for (IAEItemStack stack : list) {
                view.add(stack);
                dsp.add(stack.getItemStack());
            }
        }
    }

    @Override
    public void refresh(Comparator<IAEItemStack> comparator) {
        list = new TreeSet<>(stackComparator(comparator));
        list.addAll(view);
        refresh(this.filter);
    }

    @Override
    public void update(IAEItemStack stack) {
        list.remove(stack);
        view.clear();
        dsp.clear();
        if (filter == null || filter.test(stack)) {
            // Update the sorted display list
            if (stack.getStackSize() > 0 || stack.isCraftable()) {
                list.add(stack);
            }
            for (IAEItemStack is : list) {
                view.add(is);
                dsp.add(is.getItemStack());
            }
        }
    }

    @Override
    public void clear() {
        this.list.clear();
        this.view.clear();
        this.dsp.clear();
    }

    @Override
    public Iterator<IAEItemStack> iterator() {
        return view.iterator();
    }

    private Comparator<IAEItemStack> stackComparator(Comparator<IAEItemStack> cmp) {
        return (stack1, stack2) -> Platform.isSameItem(stack1.getItemStack(), stack2.getItemStack()) ? 0
                : cmp.compare(stack1, stack2);
    }
}
