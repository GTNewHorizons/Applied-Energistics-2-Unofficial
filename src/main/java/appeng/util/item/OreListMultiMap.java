package appeng.util.item;

import appeng.api.storage.data.IAEItemStack;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableListMultimap.Builder;
import java.util.Collection;

public class OreListMultiMap<T> {
    private ImmutableListMultimap<IAEItemStack, T> map;
    private ImmutableListMultimap.Builder<IAEItemStack, T> builder;

    private static Collection<IAEItemStack> getAEEquivalents(IAEItemStack stack) {
        AEItemStack s;
        if (!(stack instanceof AEItemStack)) {
            s = AEItemStack.create(stack.getItemStack());
        } else {
            s = (AEItemStack) stack;
        }
        return s.getDefinition().getIsOre().getAEEquivalents();
    }

    public void put(IAEItemStack key, T val) {
        for (IAEItemStack realKey : getAEEquivalents(key)) {
            builder.put(realKey, val);
        }
    }

    public void freeze() {
        map = builder.build();
        builder = null;
    }

    public ImmutableList<T> get(IAEItemStack key) {
        return map.get(key);
    }

    public void clear() {
        map = null;
        builder = new Builder<>();
    }
}
