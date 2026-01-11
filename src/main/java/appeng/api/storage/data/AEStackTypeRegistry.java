package appeng.api.storage.data;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AEStackTypeRegistry {

    private static final Map<String, IAEStackType<?>> registry = new HashMap<>();

    static {
        register(ITEM_STACK_TYPE);
        register(FLUID_STACK_TYPE);
    }

    public static void register(IAEStackType<?> type) {
        registry.put(type.getId(), type);
    }

    public static IAEStackType<?> getType(String id) {
        return registry.get(id);
    }

    public static Collection<IAEStackType<?>> getAllTypes() {
        return registry.values();
    }
}
