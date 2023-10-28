package appeng.crafting.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.ToLongBiFunction;

import org.apache.logging.log4j.Level;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;

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

/**
 * You can register additional crafting handlers here
 */
public class CraftingCalculations {

    private static final ListMultimap<Class<? extends IAEStack<?>>, CraftingRequestResolver<?>> providers = ArrayListMultimap
            .create(2, 8);
    private static final ListMultimap<Class<? extends IAEStack<?>>, ToLongBiFunction<CraftingRequest<?>, Long>> byteAmountAdjusters = ArrayListMultimap
            .create(2, 8);

    /**
     * @param provider       A custom resolver that can provide potential solutions ({@link CraftingTask}) to crafting
     *                       requests ({@link CraftingRequest})
     * @param stackTypeClass {@link IAEItemStack} or {@link IAEFluidStack}
     * @param <StackType>    {@link IAEItemStack} or {@link IAEFluidStack}
     */
    public static <StackType extends IAEStack<StackType>> void registerProvider(
            CraftingRequestResolver<StackType> provider, Class<StackType> stackTypeClass) {
        providers.put(stackTypeClass, provider);
    }

    /**
     * @param adjuster       A function that will be called when (re)-calculating the total byte cost of a request,
     *                       takes in the request and the computed total bytes, and should return the new byte count
     * @param stackTypeClass {@link IAEItemStack} or {@link IAEFluidStack}
     * @param <StackType>    {@link IAEItemStack} or {@link IAEFluidStack}
     */
    public static <StackType extends IAEStack<StackType>> void registerByteAmountAdjuster(
            ToLongBiFunction<CraftingRequest<StackType>, Long> adjuster, Class<StackType> stackTypeClass) {
        byteAmountAdjusters.put(stackTypeClass, (ToLongBiFunction<CraftingRequest<?>, Long>) (Object) adjuster);
    }

    @SuppressWarnings("unchecked")
    public static <StackType extends IAEStack<StackType>> List<CraftingTask> tryResolveCraftingRequest(
            CraftingRequest<StackType> request, CraftingContext context) {
        final ArrayList<CraftingTask> allTasks = new ArrayList<>(4);
        for (final CraftingRequestResolver<?> unsafeProvider : Multimaps
                .filterKeys(providers, key -> key.isAssignableFrom(request.stackTypeClass)).values()) {
            try {
                // Safety: Filtered by type using Multimaps.filterKeys
                final CraftingRequestResolver<StackType> provider = (CraftingRequestResolver<StackType>) unsafeProvider;
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
        allTasks.sort(CraftingTask.PRIORITY_COMPARATOR);
        return Collections.unmodifiableList(allTasks);
    }

    public static <StackType extends IAEStack<StackType>> long adjustByteCost(CraftingRequest<StackType> request,
            long byteCost) {
        for (final ToLongBiFunction<CraftingRequest<?>, Long> unsafeAdjuster : Multimaps
                .filterKeys(byteAmountAdjusters, key -> key.isAssignableFrom(request.stackTypeClass)).values()) {
            final ToLongBiFunction<CraftingRequest<StackType>, Long> adjuster = (ToLongBiFunction<CraftingRequest<StackType>, Long>) (Object) unsafeAdjuster;
            byteCost = adjuster.applyAsLong(request, byteCost);
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
}
