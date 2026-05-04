package appeng.capabilities;

import java.util.Collection;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import net.minecraft.inventory.IInventory;
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
import appeng.tile.misc.TileInterface;
import appeng.util.Platform;
import appeng.util.item.AEItemStack;
import appeng.util.item.ImmutableAEItemStackWrapper;

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

        ItemStack toInsert = stack.toStack();

        // Try to first insert into the ME system directly
        IAEItemStack rejected = storage
                .injectItems(AEItemStack.create(toInsert), Actionable.MODULATE, duality.getActionSource());

        toInsert.stackSize = rejected == null ? 0 : (int) rejected.getStackSize();

        // If the ME system is full for whatever reason, insert into any free slots
        if (toInsert.stackSize > 0) {
            for (int slot = 0; slot < 9 && toInsert.stackSize > 0; slot++) {
                insertItemIntoInv(slot, toInsert);
            }
        }

        return toInsert.stackSize;
    }

    private void insertItemIntoInv(int slot, ItemStack toInsert) {
        IInventory inv = duality.getInternalInventory();
        ItemStack config = duality.getConfig().getStackInSlot(slot);

        if (config != null && !ItemUtil.areStacksEqual(config, toInsert)) return;

        ItemStack inInv = inv.getStackInSlot(slot);

        int maxStack = Math.min(inv.getInventoryStackLimit(), toInsert.getMaxStackSize());
        int stored = inInv == null ? 0 : inInv.stackSize;
        int remaining = maxStack - stored;
        int transferable = Math.min(remaining, toInsert.stackSize);

        if (transferable > 0) {
            if (inInv == null) {
                inInv = ItemUtil.copyAmount(0, toInsert);
            }

            inInv.stackSize += transferable;
            toInsert.stackSize -= transferable;

            inv.setInventorySlotContents(slot, inInv);
            inv.markDirty();
        }
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
                final IAEItemStack blindCheck = AEItemStack.create(stack).setStackSize(Integer.MAX_VALUE);
                IAEItemStack available = storage
                        .extractItems(blindCheck, Actionable.SIMULATE, duality.getActionSource());

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

        return new MEInventoryIterator(allowedSlots);
    }

    private class MEInventoryIterator extends AbstractInventoryIterator {

        public MEInventoryIterator(int[] allowedSlots) {
            super(MEItemIO.SLOTS, allowedSlots);
        }

        @Override
        protected ItemStack getStackInSlot(int slot) {
            IAEItemStack config = duality.getConfig().getAEStackInSlot(slot);

            if (config == null) return null;

            ItemStack stored = duality.getStorage().getStackInSlot(slot);

            final IAEItemStack blindCheck = config.copy().setStackSize(Integer.MAX_VALUE);
            IAEItemStack inMESystem = storage.extractItems(blindCheck, Actionable.SIMULATE, duality.getActionSource());

            long total = 0;

            ItemStack out = null;

            if (stored != null) {
                out = ItemUtil.copyAmount(0, stored);
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
            IAEItemStack rejected = storage
                    .injectItems(AEItemStack.create(stack.toStack()), Actionable.MODULATE, duality.getActionSource());

            int rejectedAmount = rejected == null ? 0 : (int) rejected.getStackSize();

            if (rejectedAmount == 0) return 0;

            ItemStack toInsert = stack.toStack(rejectedAmount);

            insertItemIntoInv(getCurrentSlot(), toInsert);

            return toInsert.stackSize;
        }
    }
}
