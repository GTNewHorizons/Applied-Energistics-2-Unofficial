package appeng.capabilities;

import java.util.OptionalInt;
import java.util.stream.IntStream;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.gtnewhorizon.gtnhlib.capability.item.AbstractInventorySourceIterator;
import com.gtnewhorizon.gtnhlib.capability.item.IItemIO;
import com.gtnewhorizon.gtnhlib.capability.item.InventoryItemSource;
import com.gtnewhorizon.gtnhlib.capability.item.InventorySourceIterator;
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
import gregtech.api.util.GTUtility;

public class MEItemIO implements IItemIO {

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
    public void setAllowedSourceSlots(int[] allowedSourceSlots) {
        this.allowedSourceSlots = allowedSourceSlots;
    }

    @Override
    public void setAllowedSinkSlots(@Nullable int[] slots) {
        allowedSinkSlots = slots;
    }

    @Override
    public ItemStack store(ItemStack stack) {
        if (!duality.getProxy().isActive()) return stack;

        boolean matches = false, isFiltered = false;

        int[] effectiveSlots = allowedSinkSlots != null ? InventoryItemSource.intersect(SLOTS, allowedSinkSlots)
                : SLOTS;

        AppEngInternalAEInventory configInv = duality.getConfig();

        for (ItemStack config : configInv) {
            if (config != null) {
                isFiltered = true;
                break;
            }
        }

        for (int slot : effectiveSlots) {
            ItemStack config = configInv.getStackInSlot(slot);

            if (config == null) continue;

            if (ItemUtil.areStacksEqual(stack, config)) {
                matches = true;
                break;
            }
        }

        if (isFiltered && !matches) return stack;

        IAEItemStack aeStack = AEItemStack.create(stack);

        IAEItemStack rejected = Platform.poweredInsert(energyGrid, storage, aeStack, duality.getActionSource());

        return rejected == null ? null : rejected.getItemStack();
    }

    @Override
    public OptionalInt getStoredAmount(ItemStack stack) {
        if (!duality.getProxy().isActive()) return OptionalInt.empty();

        if (stack == null) {
            long sum = 0;

            for (var s : storage.getStorageList()) {
                sum += s.getStackSize();
            }

            return OptionalInt.of(longToInt(sum));
        } else {
            IAEItemStack available = storage.getAvailableItem(AEItemStack.create(stack), IterationCounter.fetchNewId());

            return available == null ? ZERO : OptionalInt.of(longToInt(available.getStackSize()));
        }
    }

    @Override
    public @Nonnull InventorySourceIterator iterator() {
        if (!duality.getProxy().isActive()) return InventorySourceIterator.EMPTY;

        int[] effectiveSlots = allowedSourceSlots != null ? InventoryItemSource.intersect(SLOTS, allowedSourceSlots)
                : SLOTS;

        return new AbstractInventorySourceIterator(effectiveSlots) {

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
                    out.stackSize = longToInt(total);
                }

                return out;
            }

            @Override
            protected void setInventorySlotContents(int slot, ItemStack stack) {
                throw new UnsupportedOperationException("this should never be called since extract() was overridden");
            }

            @Override
            public ItemStack extract(int amount) {
                IAEItemStack config = duality.getConfig().getAEStackInSlot(getCurrentSlot());

                if (config == null) return null;

                ItemStack result = config.getItemStack();
                result.stackSize = 0;

                IAEItemStack extracted = Platform.poweredExtraction(
                        energyGrid,
                        storage,
                        config.copy().setStackSize(amount),
                        duality.getActionSource());

                if (extracted != null) {
                    result.stackSize += (int) extracted.getStackSize();
                    amount -= (int) extracted.getStackSize();
                }

                ItemStack stored = duality.getStorage().getStackInSlot(getCurrentSlot());

                // Only extract from the slot if there wasn't enough in the network because interface ticks are
                // comparatively expensive
                if (amount > 0 && GTUtility.isStackValid(stored)) {
                    int toExtract = Math.min(amount, stored.stackSize);

                    result.stackSize += toExtract;
                    stored.stackSize -= toExtract;
                }

                return result;
            }

            @Override
            public void insert(ItemStack stack) {
                storage.injectItems(AEItemStack.create(stack), Actionable.MODULATE, duality.getActionSource());
            }
        };
    }

    public static int longToInt(long number) {
        return (int) Math.min(Integer.MAX_VALUE, number);
    }
}
