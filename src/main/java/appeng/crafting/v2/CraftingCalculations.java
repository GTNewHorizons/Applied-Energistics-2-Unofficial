package appeng.crafting.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToLongBiFunction;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.Level;

import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.core.AELog;
import appeng.crafting.v2.resolvers.CraftableItemResolver;
import appeng.crafting.v2.resolvers.CraftingRequestResolver;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.crafting.v2.resolvers.EmitableItemResolver;
import appeng.crafting.v2.resolvers.ExtractItemResolver;
import appeng.crafting.v2.resolvers.IgnoreMissingItemResolver;
import appeng.crafting.v2.resolvers.SimulateMissingItemResolver;
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;

/**
 * You can register additional crafting handlers here
 */
public class CraftingCalculations {

    private static final InternalMultiMap<Class<? extends IAEStack<?>>, CraftingRequestResolver<?>> providers = new InternalMultiMap<>();
    private static final InternalMultiMap<Class<? extends IAEStack<?>>, ToLongBiFunction<CraftingRequest<?>, Long>> byteAmountAdjusters = new InternalMultiMap<>();

    /**
     * @param provider       A custom resolver that can provide potential solutions ({@link CraftingTask}) to crafting
     *                       requests ({@link CraftingRequest})
     * @param stackTypeClass {@link IAEItemStack} or {@link IAEFluidStack}
     * @param <StackType>    {@link IAEItemStack} or {@link IAEFluidStack}
     */
    public static <StackType extends IAEStack<StackType>> void registerProvider(
            CraftingRequestResolver<StackType> provider, Class<StackType> stackTypeClass) {
        providers.compute(
                stackTypeClass,
                (k, v) -> ArrayUtils.add(v == null ? new CraftingRequestResolver[0] : v, provider));
    }

    /**
     * @param adjuster       A function that will be called when (re)-calculating the total byte cost of a request,
     *                       takes in the request and the computed total bytes, and should return the new byte count
     * @param stackTypeClass {@link IAEItemStack} or {@link IAEFluidStack}
     * @param <StackType>    {@link IAEItemStack} or {@link IAEFluidStack}
     */
    public static <StackType extends IAEStack<StackType>> void registerByteAmountAdjuster(
            ToLongBiFunction<CraftingRequest<StackType>, Long> adjuster, Class<StackType> stackTypeClass) {
        byteAmountAdjusters.compute(
                stackTypeClass,
                (k, v) -> ArrayUtils.add(
                        v == null ? new ToLongBiFunction[0] : v,
                        (ToLongBiFunction<CraftingRequest<?>, Long>) (Object) adjuster));
    }

    @SuppressWarnings("unchecked")
    public static <StackType extends IAEStack<StackType>> List<CraftingTask> tryResolveCraftingRequest(
            CraftingRequest<StackType> request, CraftingContext context) {
        final ArrayList<CraftingTask> allTasks = new ArrayList<>(4);

        final Object[] keyArray = providers.keysArray();
        final int size = keyArray.length;
        for (int i = 0; i < size; i++) {
            Class<? extends IAEStack<?>> aClass = (Class<? extends IAEStack<?>>) keyArray[i];
            if (aClass == null) {
                // We never remove from the providers map, therefore the key array will never have holes, allowing us
                // to exit early here
                break;
            }

            if (!aClass.isAssignableFrom(request.stackTypeClass)) {
                continue;
            }

            for (CraftingRequestResolver<?> resolver : providers.valuesArrayAt(i)) {
                try {
                    // Safety: Filtered by type using isAssignableFrom on the keys
                    final CraftingRequestResolver<StackType> provider = (CraftingRequestResolver<StackType>) resolver;
                    final List<CraftingTask> tasks = provider.provideCraftingRequestResolvers(request, context);
                    allTasks.addAll(tasks);
                } catch (Exception t) {
                    AELog.log(
                            Level.WARN,
                            t,
                            "Error encountered when trying to generate the list of CraftingTasks for crafting {}",
                            request.toString());
                }
            }
        }

        allTasks.sort(CraftingTask.PRIORITY_COMPARATOR);
        return Collections.unmodifiableList(allTasks);
    }

    public static <StackType extends IAEStack<StackType>> long adjustByteCost(CraftingRequest<StackType> request,
            long byteCost) {
        final Object[] keyArray = byteAmountAdjusters.keysArray();
        final int size = keyArray.length;
        for (int i = 0; i < size; i++) {
            Class<? extends IAEStack<?>> aClass = (Class<? extends IAEStack<?>>) keyArray[i];
            if (aClass == null) {
                // We never remove from the byteAmountAdjusters map, therefore the key array will never have holes,
                // allowing us
                // to exit early here
                break;
            }

            if (!aClass.isAssignableFrom(request.stackTypeClass)) {
                continue;
            }

            for (ToLongBiFunction<CraftingRequest<?>, Long> value : byteAmountAdjusters.valuesArrayAt(i)) {
                final ToLongBiFunction<CraftingRequest<StackType>, Long> adjuster = (ToLongBiFunction<CraftingRequest<StackType>, Long>) (Object) value;
                byteCost = adjuster.applyAsLong(request, byteCost);
            }
        }

        return byteCost;
    }

    static {
        registerProvider(new ExtractItemResolver(), IAEItemStack.class);
        registerProvider(new SimulateMissingItemResolver<>(), IAEItemStack.class);
        registerProvider(new SimulateMissingItemResolver<>(), IAEFluidStack.class);
        registerProvider(new EmitableItemResolver(), IAEItemStack.class);
        registerProvider(new CraftableItemResolver(), IAEItemStack.class);
        registerProvider(new IgnoreMissingItemResolver(), IAEItemStack.class);
    }

    private static final class InternalMultiMap<K, V> extends Object2ObjectArrayMap<K, V[]> {

        public Object[] keysArray() {
            return this.key;
        }

        public V[] valuesArrayAt(int index) {
            // NOTE: Returning a V[] is only safe because registerProvider and registerByteAmountAdjuster create typed
            // arrays when inserting values
            return (V[]) this.value[index];
        }

        @Override
        public boolean remove(Object key, Object value) {
            throw new UnsupportedOperationException();
        }

        @Override
        public V[] remove(Object k) {
            throw new UnsupportedOperationException();
        }
    }
}
