package appeng.crafting.v2.resolvers;

import java.util.List;

import javax.annotation.Nonnull;

import appeng.crafting.v2.CraftingContext;
import appeng.crafting.v2.CraftingRequest;

/**
 * See {@link CraftingRequestResolver#provideCraftingRequestResolvers(CraftingRequest, CraftingContext)}
 */
@FunctionalInterface
public interface CraftingRequestResolver {

    /**
     * Provides a list of potential solutions for doing one crafting step, the calculator will try the solutions ordered
     * by priority in turn. For example, given a plank item, it can give tasks for log->plank crafting Higher priority =
     * will be used first, Integer.MAX_VALUE priority is used for items already in the system.
     * <p>
     * <p>
     * It should reduce the {@link CraftingRequest#remainingToProcess} value by the number of items immediately
     * available.
     *
     * @param request The request being resolved
     * @param context The ME system, job queue and pattern caches used to calculate potential resolutions
     * @return The list of potential solutions - return an empty list if none are available
     */
    @Nonnull
    List<CraftingTask> provideCraftingRequestResolvers(@Nonnull CraftingRequest request,
            @Nonnull CraftingContext context);
}
