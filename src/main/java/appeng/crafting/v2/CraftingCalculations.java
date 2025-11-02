package appeng.crafting.v2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.logging.log4j.Level;

import appeng.core.AELog;
import appeng.crafting.v2.resolvers.CraftableItemResolver;
import appeng.crafting.v2.resolvers.CraftingRequestResolver;
import appeng.crafting.v2.resolvers.CraftingTask;
import appeng.crafting.v2.resolvers.EmitableItemResolver;
import appeng.crafting.v2.resolvers.ExtractItemResolver;
import appeng.crafting.v2.resolvers.IgnoreMissingItemResolver;
import appeng.crafting.v2.resolvers.SimulateMissingItemResolver;
import appeng.util.Platform;

/**
 * You can register additional crafting handlers here
 */
public class CraftingCalculations {

    private static final List<CraftingRequestResolver> providers = new ArrayList<>();

    /**
     * @param provider A custom resolver that can provide potential solutions ({@link CraftingTask}) to crafting
     *                 requests ({@link CraftingRequest})
     */
    public static void registerProvider(CraftingRequestResolver provider) {
        providers.add(provider);
    }

    public static List<CraftingTask> tryResolveCraftingRequest(CraftingRequest request, CraftingContext context) {
        final ArrayList<CraftingTask> allTasks = new ArrayList<>(4);

        for (CraftingRequestResolver resolver : providers) {
            try {
                // Safety: Filtered by type using isAssignableFrom on the keys
                final List<CraftingTask> tasks = resolver.provideCraftingRequestResolvers(request, context);
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

    public static long adjustByteCost(CraftingRequest request, long byteCost) {
        return Platform.ceilDiv(byteCost, request.stack.getAmountPerUnit());
    }

    static {
        registerProvider(new ExtractItemResolver());
        registerProvider(new SimulateMissingItemResolver());
        registerProvider(new EmitableItemResolver());
        registerProvider(new CraftableItemResolver());
        registerProvider(new IgnoreMissingItemResolver());
    }
}
