package appeng.util.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableListMultimap.Builder;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEItemStack;

public class OreListMultiMap<T> {

    private ImmutableListMultimap<Integer, T> map;
    private final Map<Integer, Collection<ICraftingPatternDetails>> patternHashMap = new HashMap<>();
    private ImmutableListMultimap<Integer, T> patternMap;
    private ImmutableListMultimap.Builder<Integer, T> builder = new Builder<>();

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

    public boolean isPopulated() {
        return patternMap != null;
    }

    public void put(IAEItemStack key, ICraftingPatternDetails val) {
        if (((AEItemStack) key).getDefinition() != null) {
            Collection<ICraftingPatternDetails> tmp = patternHashMap
                    .getOrDefault(((AEItemStack) key).getDefinition().getMyHash(), null);
            if (tmp == null) {
                ArrayList<ICraftingPatternDetails> list = new ArrayList<>();
                list.add(val);
                patternHashMap.put(((AEItemStack) key).getDefinition().getMyHash(), list);
            } else {
                tmp.add(val);
            }
        }

        for (Integer realKey : getAEEquivalents(key)) {
            builder.put(realKey, (T) val);
        }
    }

    public void freeze() {
        map = builder.build();
        builder = new Builder<>();
        for (Entry<Integer, Collection<ICraftingPatternDetails>> collection : this.patternHashMap.entrySet()) {
            for (ICraftingPatternDetails details : collection.getValue()) {
                builder.put(collection.getKey(), (T) details);
            }
        }
        patternMap = builder.build();
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

    public ImmutableList<ICraftingPatternDetails> getBeSubstitutePattern(IAEItemStack ias) {
        if (((AEItemStack) ias).getDefinition() == null) return ImmutableList.of();
        return (ImmutableList<ICraftingPatternDetails>) this.patternMap
                .get(((AEItemStack) ias).getDefinition().getMyHash());
    }

    public void clear() {
        map = null;
        patternMap = null;
        patternHashMap.clear();
        builder = new Builder<>();
    }
}
