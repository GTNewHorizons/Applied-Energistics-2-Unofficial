package appeng.test.tile.misc;

import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import org.junit.jupiter.api.Test;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IBaseMonitor;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.tile.misc.TileSuperMEReplenisher;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;
import appeng.util.item.IAEStackList;

public class TileSuperMEReplenisherFunctionalTest {

    private static final Field STORAGE;
    private static final Method INJECT_ITEMS;
    private static final Method EXTRACT_ITEMS;

    static {
        try {
            STORAGE = TileSuperMEReplenisher.class.getDeclaredField("storage");
            INJECT_ITEMS = TileSuperMEReplenisher.class.getDeclaredMethod(
                    "injectItems",
                    IAEStack.class,
                    Actionable.class,
                    IAEStackList.class,
                    BaseActionSource.class);
            EXTRACT_ITEMS = TileSuperMEReplenisher.class.getDeclaredMethod(
                    "extractItems",
                    IAEStack.class,
                    Actionable.class,
                    IAEStackList.class,
                    BaseActionSource.class);
            STORAGE.setAccessible(true);
            INJECT_ITEMS.setAccessible(true);
            EXTRACT_ITEMS.setAccessible(true);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @Test
    public void publishesVisibleChangesExactlyOnce() throws ReflectiveOperationException {
        final TileSuperMEReplenisher tile = replenisherWithCells(1);
        final IMEMonitor<IAEItemStack> monitor = itemMonitor(tile);
        final RecordingListener listener = new RecordingListener();
        final BaseActionSource source = new BaseActionSource();
        final IAEItemStack cobblestone = itemStack(64);

        monitor.addListener(listener, listener);
        assertSame(monitor, tile.getMonitor(ITEM_STACK_TYPE));
        assertEquals(0, storedAmount(monitor, cobblestone));

        assertNull(inject(tile, cobblestone, Actionable.MODULATE, storage(tile), source));
        listener.assertSingleChange(64, source);
        assertEquals(64, storedAmount(monitor, cobblestone));

        listener.clear();
        assertNull(inject(tile, itemStack(8), Actionable.SIMULATE, storage(tile), source));
        assertEquals(8, monitor.extractItems(itemStack(8), Actionable.SIMULATE, source).getStackSize());
        assertEquals(0, listener.changes.size());
        assertEquals(64, storedAmount(monitor, cobblestone));

        assertNull(tile.injectItems(itemStack(8), Actionable.MODULATE, source));
        assertEquals(0, listener.changes.size());
        assertEquals(64, storedAmount(monitor, cobblestone));

        assertEquals(16, monitor.extractItems(itemStack(16), Actionable.MODULATE, source).getStackSize());
        listener.assertSingleChange(-16, source);
        assertEquals(48, storedAmount(monitor, cobblestone));

        listener.clear();
        assertEquals(8, extract(tile, itemStack(8), Actionable.MODULATE, storage(tile), source).getStackSize());
        listener.assertSingleChange(-8, source);
        assertEquals(40, storedAmount(monitor, cobblestone));
    }

    @Test
    public void recountsMultipleHugeStacksWithoutOverflow() {
        final IAEStackList storage = new IAEStackList(true);
        storage.add(fluidStack(FluidRegistry.WATER, 1L << 62));
        storage.add(fluidStack(FluidRegistry.LAVA, 1L << 62));

        final NBTTagCompound data = new NBTTagCompound();
        data.setTag("storage", Platform.writeAEStackListNBT(storage));

        final TileSuperMEReplenisher tile = new TileSuperMEReplenisher();
        tile.readFromNBTEvent(data);

        assertEquals(1L << 52, tile.getUsedBytes());
    }

    @Test
    public void rejectsInsertionWhileOverCapacity() throws ReflectiveOperationException {
        final TileSuperMEReplenisher tile = replenisherWithCells(2);
        final IAEStackList storage = storage(tile);
        final BaseActionSource source = new BaseActionSource();

        assertNull(inject(tile, itemStack(16_384), Actionable.MODULATE, storage, source));
        tile.getCellInventory().setInventorySlotContents(1, null);

        assertEquals(1_024, tile.getTotalBytes());
        assertEquals(2_048, tile.getUsedBytes());

        final IAEItemStack simulated = itemStack(1);
        assertSame(simulated, inject(tile, simulated, Actionable.SIMULATE, storage, source));
        final IAEItemStack modulated = itemStack(1);
        assertSame(modulated, inject(tile, modulated, Actionable.MODULATE, storage, source));
        assertEquals(16_384, storage.findPrecise(itemStack(1)).getStackSize());
        assertEquals(2_048, tile.getUsedBytes());
    }

    private static TileSuperMEReplenisher replenisherWithCells(final int count) {
        final TileSuperMEReplenisher tile = new TileSuperMEReplenisher();
        for (int i = 0; i < count; i++) {
            tile.getCellInventory()
                    .setInventorySlotContents(i, AEApi.instance().definitions().items().cell1k().maybeStack(1).get());
        }
        return tile;
    }

    @SuppressWarnings("unchecked")
    private static IMEMonitor<IAEItemStack> itemMonitor(final TileSuperMEReplenisher tile) {
        return (IMEMonitor<IAEItemStack>) tile.getMonitor(ITEM_STACK_TYPE);
    }

    private static IAEStackList storage(final TileSuperMEReplenisher tile) throws IllegalAccessException {
        return (IAEStackList) STORAGE.get(tile);
    }

    private static IAEStack<?> inject(final TileSuperMEReplenisher tile, final IAEStack<?> stack, final Actionable mode,
            final IAEStackList target, final BaseActionSource source) throws ReflectiveOperationException {
        return (IAEStack<?>) INJECT_ITEMS.invoke(tile, stack, mode, target, source);
    }

    private static IAEStack<?> extract(final TileSuperMEReplenisher tile, final IAEStack<?> stack,
            final Actionable mode, final IAEStackList target, final BaseActionSource source)
            throws ReflectiveOperationException {
        return (IAEStack<?>) EXTRACT_ITEMS.invoke(tile, stack, mode, target, source);
    }

    private static IAEItemStack itemStack(final long amount) {
        return AEItemStack.create(new ItemStack(Blocks.cobblestone)).setStackSize(amount);
    }

    private static IAEFluidStack fluidStack(final Fluid fluid, final long amount) {
        return AEFluidStack.create(new FluidStack(fluid, 1)).setStackSize(amount);
    }

    private static long storedAmount(final IMEMonitor<IAEItemStack> monitor, final IAEItemStack template) {
        final IAEItemStack stored = monitor.getStorageList().findPrecise(template);
        return stored == null ? 0 : stored.getStackSize();
    }

    private static final class RecordingListener implements IMEMonitorHandlerReceiver<IAEItemStack> {

        private final List<Long> changes = new ArrayList<>();
        private final List<BaseActionSource> sources = new ArrayList<>();

        @Override
        public boolean isValid(final Object verificationToken) {
            return verificationToken == this;
        }

        @Override
        public void postChange(final IBaseMonitor<IAEItemStack> monitor, final Iterable<IAEItemStack> changes,
                final BaseActionSource source) {
            for (final IAEItemStack change : changes) {
                this.changes.add(change.getStackSize());
                this.sources.add(source);
            }
        }

        @Override
        public void onListUpdate() {}

        private void assertSingleChange(final long amount, final BaseActionSource source) {
            assertEquals(1, this.changes.size());
            assertEquals(amount, this.changes.get(0).longValue());
            assertEquals(1, this.sources.size());
            assertSame(source, this.sources.get(0));
        }

        private void clear() {
            this.changes.clear();
            this.sources.clear();
        }
    }
}
