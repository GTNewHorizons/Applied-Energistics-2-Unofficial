package appeng.util.item;

import appeng.api.storage.data.IAEItemStack;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableListMultimap.Builder;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class OreListMultiMap<T> {
    private ImmutableListMultimap<Integer, T> map;
    private ImmutableListMultimap.Builder<Integer, T> builder;

    private static Collection<Integer> getAEEquivalents(IAEItemStack stack) {
        AEItemStack s;
        if (!(stack instanceof AEItemStack)) {
            s = AEItemStack.create(stack.getItemStack());
        } else {
            s = (AEItemStack) stack;
        }
        OreReference ore = s.getDefinition().getIsOre();
        if (ore == null) return Collections.emptyList();
        Set<Integer> ids = ore.getOres();
        if (ids == null) return Collections.emptyList();
        return ids;
    }

    public void put(IAEItemStack key, T val) {
        for (Integer realKey : getAEEquivalents(key)) {
            builder.put(realKey, val);
        }
    }

    public void freeze() {
        map = builder.build();
        builder = null;
    }

    public ImmutableList<T> get(IAEItemStack key) {
        Collection<Integer> ids = getAEEquivalents(key);
        if (ids.isEmpty()) return ImmutableList.of();
        else if (ids.size() == 1) {
            return map.get(ids.iterator().next());
        } else {
            ImmutableList.Builder<T> b = ImmutableList.builder();
            for (Integer id : ids) {
                b.addAll(map.get(id));
            }
            return b.build();
        }
    }

    public void clear() {
        map = null;
        builder = new Builder<>();
    }
}
