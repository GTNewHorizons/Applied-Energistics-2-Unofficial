package appeng.gametests.crafting;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.common.collect.ImmutableMap;
import com.gtnewhorizons.horizonqa.api.GameTestHelper;
import com.gtnewhorizons.horizonqa.api.annotation.GameTest;
import com.gtnewhorizons.horizonqa.api.annotation.GameTestHolder;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingGrid;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.storage.IStorageGrid;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.core.AppEng;
import appeng.crafting.MECraftingInventory;
import appeng.crafting.v2.CraftingContext;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

@GameTestHolder(AppEng.MOD_ID)
public class CraftingInventorySnapshotTests {

    @GameTest
    public static void storageSnapshotCopiesEachEntryOnlyOnce(GameTestHelper helper) {
        final CountingStorage storage = new CountingStorage();
        final IStorageGrid storageGrid = storage.grid().getCache(IStorageGrid.class);
        new MECraftingInventory(storageGrid, false, false, false);

        helper.assertEquals(2L, storage.stackCopies, "The item and fluid entries should each be copied once");
        helper.succeed();
    }

    @GameTest
    public static void planningReadsStorageOnceAndCopiesEachEntryTwice(GameTestHelper helper) {
        final CountingStorage storage = new CountingStorage();
        new CraftingContext(helper.getWorld(), storage.grid(), new BaseActionSource());

        final int stackTypes = AEStackTypeRegistry.getAllTypes().size();
        helper.assertEquals(stackTypes, storage.monitorReads, "Planning should read each storage monitor once");
        helper.assertEquals(stackTypes, storage.listTraversals, "Planning should traverse each monitor list once");
        helper.assertEquals(4L, storage.stackCopies, "The item and fluid entries should each be copied twice");
        helper.succeed();
    }

    @GameTest
    public static void availabilitySnapshotIsIndependentOfStorageAndPlanning(GameTestHelper helper) {
        final CountingStorage storage = new CountingStorage();
        final CraftingContext context = new CraftingContext(helper.getWorld(), storage.grid(), new BaseActionSource());

        context.itemModel.extractItems(storage.itemRequest(4), Actionable.MODULATE);
        context.itemModel.extractItems(storage.fluidRequest(250), Actionable.MODULATE);
        storage.item.setStackSize(1);
        storage.fluid.setStackSize(1);

        helper.assertEquals(
                10L,
                context.availableCache.extractItems(storage.itemRequest(100), Actionable.SIMULATE).getStackSize(),
                "Availability should retain the initial item count");
        helper.assertEquals(
                1_000L,
                context.availableCache.extractItems(storage.fluidRequest(2_000), Actionable.SIMULATE).getStackSize(),
                "Availability should retain the initial fluid count");
        helper.assertEquals(
                6L,
                context.itemModel.extractItems(storage.itemRequest(100), Actionable.SIMULATE).getStackSize(),
                "Live storage changes should not alter the working inventory");
        helper.assertEquals(
                750L,
                context.itemModel.extractItems(storage.fluidRequest(2_000), Actionable.SIMULATE).getStackSize(),
                "Live storage changes should not alter the working fluid inventory");
        helper.succeed();
    }

    @GameTest
    public static void snapshotHasNoTransactionLogsAndWorkingInventoryStillCommits(GameTestHelper helper) {
        final CountingStorage storage = new CountingStorage();
        final CraftingContext context = new CraftingContext(helper.getWorld(), storage.grid(), new BaseActionSource());

        context.availableCache.extractItems(storage.itemRequest(3), Actionable.MODULATE);
        helper.assertTrue(context.availableCache.commit(new BaseActionSource()), "A detached snapshot should commit");
        helper.assertEquals(0L, storage.extractions, "Committing availability must not extract network items");

        context.itemModel.extractItems(storage.itemRequest(4), Actionable.MODULATE);
        helper.assertTrue(context.itemModel.commit(new BaseActionSource()), "The working inventory should commit");
        helper.assertEquals(1L, storage.extractions, "Working-inventory extraction logs must remain enabled");
        helper.assertEquals(4L, storage.extractedAmount, "Only the working inventory should request network items");
        helper.succeed();
    }

    private static final class CountingStorage {

        private IAEItemStack item = AEItemStack.create(new ItemStack(Items.iron_ingot, 10));
        private IAEFluidStack fluid = AEFluidStack.create(new FluidStack(FluidRegistry.WATER, 1_000));
        private final Map<IAEStackType<?>, IMEMonitor<?>> monitors = new IdentityHashMap<>();
        private long monitorReads;
        private long listTraversals;
        private long stackCopies;
        private long extractions;
        private long extractedAmount;

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private CountingStorage() {
            for (final IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
                final IItemList list = type.createList();
                if (type == item.getStackType()) {
                    list.add(item);
                    item = (IAEItemStack) list.getFirstItem();
                }
                if (type == fluid.getStackType()) {
                    list.add(fluid);
                    fluid = (IAEFluidStack) list.getFirstItem();
                }
                final IItemList countedList = proxy(IItemList.class, (ignored, method, args) -> {
                    if (method.getName().equals("iterator")) {
                        listTraversals++;
                        final Iterator<IAEStack<?>> iterator = list.iterator();
                        return new Iterator<IAEStack<?>>() {

                            @Override
                            public boolean hasNext() {
                                return iterator.hasNext();
                            }

                            @Override
                            public IAEStack<?> next() {
                                return track(iterator.next());
                            }
                        };
                    }
                    return invoke(method, list, args);
                });
                monitors.put(type, proxy(IMEMonitor.class, (ignored, method, args) -> {
                    switch (method.getName()) {
                        case "getStorageList":
                            monitorReads++;
                            return countedList;
                        case "extractItems":
                            extractions++;
                            extractedAmount += ((IAEStack<?>) args[0]).getStackSize();
                            return ((IAEStack<?>) args[0]).copy();
                        default:
                            throw new UnsupportedOperationException(method.getName());
                    }
                }));
            }
        }

        private IGrid grid() {
            final IStorageGrid storage = proxy(IStorageGrid.class, (ignored, method, args) -> {
                if (method.getName().equals("getMEMonitor")) return monitors.get(args[0]);
                throw new UnsupportedOperationException(method.getName());
            });
            final ICraftingGrid crafting = proxy(ICraftingGrid.class, (ignored, method, args) -> {
                if (method.getName().equals("getCraftingMultiPatterns")) return ImmutableMap.of();
                throw new UnsupportedOperationException(method.getName());
            });
            return proxy(IGrid.class, (ignored, method, args) -> {
                if (method.getName().equals("getCache")) {
                    if (args[0] == IStorageGrid.class) return storage;
                    if (args[0] == ICraftingGrid.class) return crafting;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        }

        private IAEItemStack itemRequest(long amount) {
            return (IAEItemStack) track(item.copy().setStackSize(amount));
        }

        private IAEFluidStack fluidRequest(long amount) {
            return (IAEFluidStack) track(fluid.copy().setStackSize(amount));
        }

        private IAEStack<?> track(IAEStack<?> stack) {
            final Class<?> type = stack instanceof IAEItemStack ? IAEItemStack.class : IAEFluidStack.class;
            return (IAEStack<?>) proxy(type, new CountingStack(stack));
        }

        private final class CountingStack implements InvocationHandler {

            private final IAEStack<?> stack;

            private CountingStack(IAEStack<?> stack) {
                this.stack = stack;
            }

            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("copy")) {
                    stackCopies++;
                    return track(stack.copy());
                }
                if (args != null) {
                    for (int i = 0; i < args.length; i++) {
                        if (args[i] != null && Proxy.isProxyClass(args[i].getClass())
                                && Proxy.getInvocationHandler(args[i]) instanceof CountingStack counted) {
                            args[i] = counted.stack;
                        }
                    }
                }
                final Object result = CraftingInventorySnapshotTests.invoke(method, stack, args);
                return result == stack ? proxy : result;
            }
        }
    }

    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler));
    }

    private static Object invoke(Method method, Object target, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
