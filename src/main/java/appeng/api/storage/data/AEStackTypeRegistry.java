package appeng.api.storage.data;

import java.util.HashMap;
import java.util.Map;

public class AEStackTypeRegistry {

    private static final Map<String, IAEStackType<?>> registry = new HashMap<>();

    public static void register(IAEStackType<?> type) {
        registry.put(type.getId(), type);
    }

    public static IAEStackType<?> getType(String id) {
        return registry.get(id);
    }
}
