package appeng.crafting.fast;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.function.Function;

import javax.annotation.ParametersAreNonnullByDefault;

import org.jetbrains.annotations.Nullable;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEStack;
import gregtech.api.util.FieldsAreNonnullByDefault;
import gregtech.api.util.MethodsReturnNonnullByDefault;
import it.unimi.dsi.fastutil.longs.LongObjectPair;
import it.unimi.dsi.fastutil.objects.AbstractObject2LongMap;
import it.unimi.dsi.fastutil.objects.AbstractObject2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;

/**
 * Implementation of Tarjan's algorithm.
 */
@FieldsAreNonnullByDefault
@MethodsReturnNonnullByDefault
@ParametersAreNonnullByDefault
public final class SccResolver {

    public static final class Result {

        final AbstractObject2ObjectMap<IAEStack<?>, LongObjectPair<ICraftingPatternDetails>> patterns = new Object2ObjectOpenHashMap<>();
        final AbstractObject2LongMap<IAEStack<?>> inDegree = new Object2LongOpenHashMap<>();
        final Set<IAEStack<?>> loopingPatterns = new HashSet<>();
    }

    private static final class NodeInfo {

        final IAEStack<?> stack;
        final long discovered;
        long sccGroup;
        boolean inStack;

        private NodeInfo(IAEStack<?> stack, long discovered) {
            this.stack = stack;
            this.discovered = discovered;
            this.sccGroup = discovered;
        }
    }

    private final Function<IAEStack<?>, @Nullable LongObjectPair<ICraftingPatternDetails>> resolver;
    private final Result result = new Result();

    private long timer = 0;
    private final AbstractObject2ObjectMap<IAEStack<?>, NodeInfo> nodeInfo = new Object2ObjectOpenHashMap<>();
    private final Stack<NodeInfo> stack = new Stack<>();

    private SccResolver(Function<IAEStack<?>, LongObjectPair<ICraftingPatternDetails>> resolver) {
        this.resolver = resolver;
    }

    public static Result compute(IAEStack<?> target,
            Function<IAEStack<?>, @Nullable LongObjectPair<ICraftingPatternDetails>> resolver) {
        SccResolver sccResolver = new SccResolver(resolver);
        sccResolver.computeImpl(target);
        return sccResolver.result;
    }

    /**
     * target is always undiscovered
     */
    private NodeInfo computeImpl(IAEStack<?> current) {
        final NodeInfo currentInfo = new NodeInfo(current, ++timer);
        nodeInfo.put(current, currentInfo);
        LongObjectPair<ICraftingPatternDetails> pattern = resolver.apply(current);
        stack.push(currentInfo);
        currentInfo.inStack = true;
        if (pattern != null) {
            result.patterns.put(current, pattern);
            for (IAEStack<?> child : pattern.right().getCondensedAEInputs()) {
                final NodeInfo childInfo = nodeInfo.get(child);
                if (childInfo == null) {
                    currentInfo.sccGroup = Math.min(currentInfo.sccGroup, computeImpl(child).sccGroup);
                } else if (childInfo.inStack) {
                    currentInfo.sccGroup = Math.min(currentInfo.sccGroup, childInfo.discovered);
                }
                result.inDegree.put(child, result.inDegree.getLong(child) + 1);
            }
        }
        if (currentInfo.sccGroup == currentInfo.discovered) {
            if (stack.peek() == currentInfo) {
                // self edge is fine for our calculator, ignore such edge
                stack.pop();
                currentInfo.inStack = false;
            } else {
                // Part of a loop
                while (true) {
                    NodeInfo tmp = stack.pop();
                    tmp.inStack = false;
                    result.loopingPatterns.add(tmp.stack);
                    if (tmp == currentInfo) break;
                }
            }
        }
        return currentInfo;
    }
}
