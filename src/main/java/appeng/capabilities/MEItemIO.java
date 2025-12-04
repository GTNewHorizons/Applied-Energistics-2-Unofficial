package appeng.capabilities;

import java.util.Collection;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.capability.item.ItemIO;
import com.gtnewhorizon.gtnhlib.item.AbstractInventoryIterator;
import com.gtnewhorizon.gtnhlib.item.ImmutableItemStack;
import com.gtnewhorizon.gtnhlib.item.InventoryIterator;
import com.gtnewhorizon.gtnhlib.item.ItemStack2IntFunction;
import com.gtnewhorizon.gtnhlib.item.ItemStackPredicate;
import com.gtnewhorizon.gtnhlib.util.ItemUtil;

import appeng.api.config.Actionable;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.DualityInterface;
import appeng.me.GridAccessException;
import appeng.parts.misc.PartInterface;
import appeng.tile.inventory.AppEngInternalAEInventory;
import appeng.tile.misc.TileInterface;
import appeng.util.IterationCounter;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.ImmutableAEItemStackWrapper;
import it.unimi.dsi.fastutil.objects.ObjectIterators;
import it.unimi.dsi.fastutil.objects.ObjectList;

public class MEItemIO implements ItemIO {

    private final DualityInterface duality;
    private final IEnergyGrid energyGrid;
    private final IMEMonitor<IAEItemStack> storage;

    private int[] allowedSourceSlots, allowedSinkSlots;

    private static final int[] SLOTS = IntStream.range(0, 9).toArray();

    public MEItemIO(TileInterface iface) throws GridAccessException {
        this.energyGrid = iface.getProxy().getGrid().getCache(IEnergyGrid.class);
        this.storage = iface.getProxy().getGrid().<IStorageGrid>getCache(IStorageGrid.class).getItemInventory();

        this.duality = iface.getInterfaceDuality();
    }

    public MEItemIO(PartInterface iface) throws GridAccessException {
        this.energyGrid = iface.getProxy().getGrid().getCache(IEnergyGrid.class);
        this.storage = iface.getProxy().getGrid().<IStorageGrid>getCache(IStorageGrid.class).getItemInventory();

        this.duality = iface.getInterfaceDuality();
    }

    @Override
    public @NotNull InventoryIterator sourceIterator() {
        return getInventoryIterator(allowedSourceSlots);
    }

    @Override
    public @NotNull InventoryIterator sinkIterator() {
        return getInventoryIterator(allowedSinkSlots);
    }

    @Override
    public void setAllowedSourceSlots(int[] allowedSourceSlots) {
        this.allowedSourceSlots = allowedSourceSlots;
    }

    @Override
    public void setAllowedSinkSlots(int @Nullable [] slots) {
        this.allowedSinkSlots = slots;
    }

    @Override
    public @Nullable ItemStack pull(@Nullable ItemStackPredicate filter, @Nullable ItemStack2IntFunction amount) {
        InventoryIterator iter = sourceIterator();

        while (iter.hasNext()) {
            ImmutableItemStack stack = iter.next();

            if (stack == null || stack.isEmpty()) continue;

            if (filter == null || filter.test(stack)) {
                int toExtract = amount == null ? stack.getStackSize() : amount.apply(stack);

                return iter.extract(toExtract, false);
            }
        }

        return null;
    }

    @Override
    public int store(ImmutableItemStack stack) {
        if (stack == null || stack.isEmpty()) return 0;

        if (!duality.getProxy().isActive()) return stack.getStackSize();

        int[] slots = allowedSinkSlots == null ? SLOTS : AbstractInventoryIterator.intersect(SLOTS, allowedSinkSlots);

        if (isFiltered() && !matchesFilter(stack, slots)) return stack.getStackSize();

        IAEItemStack rejected = storage
                .injectItems(AEItemStack.create(stack.toStack()), Actionable.MODULATE, duality.getActionSource());

        return rejected == null ? 0 : Platform.longToInt(rejected.getStackSize());
    }

    private boolean isFiltered() {
        AppEngInternalAEInventory configInv = duality.getConfig();

        boolean isFiltered = false;

        for (ItemStack config : configInv) {
            if (config != null) {
                isFiltered = true;
                break;
            }
        }
        return isFiltered;
    }

    private boolean matchesFilter(ImmutableItemStack stack, int[] slots) {
        AppEngInternalAEInventory configInv = duality.getConfig();

        for (int slot : slots) {
            ItemStack config = configInv.getStackInSlot(slot);

            if (config == null) continue;

            if (stack.matches(config)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public OptionalInt getStoredItemsInSink(@Nullable ItemStackPredicate filter) {
        if (!duality.getProxy().isActive()) return OptionalInt.empty();

        if (filter == null) {
            long sum = 0;

            for (var s : storage.getStorageList()) {
                sum += s.getStackSize();
            }

            return sum == 0 ? ZERO : OptionalInt.of(Platform.longToInt(sum));
        }

        Collection<ItemStack> stacks = filter.getStacks();

        long sum = 0;

        ImmutableAEItemStackWrapper wrapper = new ImmutableAEItemStackWrapper();

        if (stacks != null) {
            for (ItemStack stack : stacks) {
                IAEItemStack available = storage
                        .getAvailableItem(AEItemStack.create(stack), IterationCounter.fetchNewId());

                if (filter.test(wrapper.set(available))) {
                    sum += available.getStackSize();
                }
            }
        } else {
            for (IAEItemStack stack : storage.getStorageList()) {
                if (filter.test(wrapper.set(stack))) {
                    sum += stack.getStackSize();
                }
            }
        }

        return sum == 0 ? ZERO : OptionalInt.of(Platform.longToInt(sum));
    }

    private @NotNull InventoryIterator getInventoryIterator(int[] allowedSlots) {
        if (!duality.getProxy().isActive()) return InventoryIterator.EMPTY;

        if (isFiltered()) {
            return new FilteredInventoryIterator(allowedSlots);
        } else {
            // Limit the polled stacks to 64 temporarily to avoid performance issues. This should be fine for now.
            ObjectList<IAEItemStack> contents = ObjectIterators.pour(storage.getStorageList().iterator(), 64);

            // Add 64 so that there are empty fake 'slots' to insert into. Otherwise [InventoryIterator.hasNext()] will
            // always return false. This could be higher, but 64 is a reasonable default for anything that will try to
            // insert via the iterator.
            int[] slots = new int[contents.size() + 64];

            for (int i = 0; i < slots.length; i++) slots[i] = i;

            return new UnfilteredInventoryIterator(slots, contents);
        }
    }

    private class FilteredInventoryIterator extends AbstractInventoryIterator {

        public FilteredInventoryIterator(int[] allowedSlots) {
            super(MEItemIO.SLOTS, allowedSlots);
        }

        @Override
        protected ItemStack getStackInSlot(int slot) {
            IAEItemStack config = duality.getConfig().getAEStackInSlot(slot);

            if (config == null) return null;

            ItemStack stored = duality.getStorage().getStackInSlot(slot);

            IAEItemStack inMESystem = storage.getAvailableItem(config, IterationCounter.fetchNewId());

            long total = 0;

            ItemStack out = null;

            if (stored != null) {
                out = stored.splitStack(0);
                total += stored.stackSize;
            }

            if (inMESystem != null) {
                if (out == null) out = inMESystem.getItemStack();
                total += inMESystem.getStackSize();
            }

            if (out != null) {
                out.stackSize = Platform.longToInt(total);
            }

            return out;
        }

        @Override
        public ItemStack extract(int amount, boolean force) {
            IAEItemStack config = duality.getConfig().getAEStackInSlot(getCurrentSlot());

            if (config == null) return null;

            ItemStack result = config.getItemStack();
            result.stackSize = 0;

            IAEItemStack extracted = Platform.poweredExtraction(
                    energyGrid,
                    storage,
                    config.empty().setStackSize(amount),
                    duality.getActionSource());

            if (extracted != null) {
                result.stackSize += Platform.longToInt(extracted.getStackSize());
                amount -= Platform.longToInt(extracted.getStackSize());
            }

            ItemStack stored = duality.getStorage().getStackInSlot(getCurrentSlot());

            // Only extract from the slot if there wasn't enough in the network because interface ticks are
            // comparatively expensive
            if (amount > 0 && !ItemUtil.isStackEmpty(stored)) {
                ItemStack fromSlot = duality.getStorage()
                        .decrStackSize(getCurrentSlot(), Math.min(amount, stored.stackSize));

                result.stackSize += fromSlot.stackSize;
            }

            return result;
        }

        @Override
        public int insert(ImmutableItemStack stack, boolean force) {
            if (!matchesFilter(stack, new int[] { getCurrentSlot() })) return stack.getStackSize();

            IAEItemStack rejected = storage
                    .injectItems(AEItemStack.create(stack.toStack()), Actionable.MODULATE, duality.getActionSource());

            return rejected == null ? 0 : Platform.longToInt(rejected.getStackSize());
        }
    }

    private class UnfilteredInventoryIterator extends AbstractInventoryIterator {

        private final ObjectList<IAEItemStack> contents;

        public UnfilteredInventoryIterator(int[] slots, ObjectList<IAEItemStack> contents) {
            super(slots);

            this.contents = contents;
        }

        @Override
        protected ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= contents.size()) return null;

            IAEItemStack stack = contents.get(slot);

            return stack == null ? null : stack.getItemStack();
        }

        @Override
        public ItemStack extract(int amount, boolean force) {
            int slot = getCurrentSlot();

            if (slot < 0 || slot >= contents.size()) return null;

            IAEItemStack current = contents.get(slot);

            if (current == null) return null;

            IAEItemStack extracted = Platform.poweredExtraction(
                    energyGrid,
                    storage,
                    current.empty().setStackSize(amount),
                    duality.getActionSource());

            return extracted == null ? null : extracted.getItemStack();
        }

        @Override
        public int insert(ImmutableItemStack stack, boolean force) {
            IAEItemStack rejected = storage
                    .injectItems(AEItemStack.create(stack.toStack()), Actionable.MODULATE, duality.getActionSource());

            return rejected == null ? 0 : Platform.longToInt(rejected.getStackSize());
        }
    }
}
