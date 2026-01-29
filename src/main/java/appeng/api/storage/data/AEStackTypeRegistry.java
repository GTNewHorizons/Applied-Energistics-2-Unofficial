package appeng.api.storage.data;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AEStackTypeRegistry {

    private static final Map<String, IAEStackType<?>> registry = new HashMap<>();

    static {
        register(ITEM_STACK_TYPE);
        register(FLUID_STACK_TYPE);
    }

    /**
     * Registers a new stack type with the registry.
     * <p>
     * This method must be called during the preInit phase of mod initialization. Registering stack types after preInit
     * may cause issues with network serialization and type resolution.
     *
     * @param type The stack type to register
     */
    public static void register(IAEStackType<?> type) {
        registry.put(type.getId(), type);
    }

    public static IAEStackType<?> getType(String id) {
        return registry.get(id);
    }

    public static Collection<IAEStackType<?>> getAllTypes() {
        return registry.values();
    }

    public static List<IAEStackType<?>> getSortedTypes() {
        List<IAEStackType<?>> result = new ArrayList<>();
        List<IAEStackType<?>> others = new ArrayList<>();

        for (IAEStackType<?> type : registry.values()) {
            if (type == ITEM_STACK_TYPE || type == FLUID_STACK_TYPE) {
                continue;
            }
            others.add(type);
        }

        others.sort(Comparator.comparing(IAEStackType::getId));

        result.add(ITEM_STACK_TYPE);
        result.add(FLUID_STACK_TYPE);
        result.addAll(others);

        return result;
    }
}
