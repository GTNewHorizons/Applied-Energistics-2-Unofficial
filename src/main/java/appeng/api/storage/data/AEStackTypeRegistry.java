package appeng.api.storage.data;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;

import it.unimi.dsi.fastutil.bytes.Byte2ReferenceMap;
import it.unimi.dsi.fastutil.bytes.Byte2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ByteMap;
import it.unimi.dsi.fastutil.objects.Reference2ByteOpenHashMap;

public class AEStackTypeRegistry {

    public static final byte NULL_NETWORK_ID = 0;
    private static final int MINIMUM_NETWORK_ID = 1;
    private static final Map<String, IAEStackType<?>> registry = new HashMap<>();
    private static final Reference2ByteMap<IAEStackType<?>> typeToNetworkIdMap = new Reference2ByteOpenHashMap<>();
    private static final Byte2ReferenceMap<IAEStackType<?>> networkIdToTypeMap = new Byte2ReferenceOpenHashMap<>();

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

    @ApiStatus.Internal
    public static void initNetworkIds() {
        byte id = MINIMUM_NETWORK_ID;
        for (IAEStackType<?> type : getSortedTypes()) {
            typeToNetworkIdMap.put(type, id);
            networkIdToTypeMap.put(id, type);
            id++;
        }
    }

    public static byte getNetworkId(IAEStackType<?> type) {
        byte id = typeToNetworkIdMap.getByte(type);
        if (id < MINIMUM_NETWORK_ID) {
            throw new IllegalStateException(
                    "Cannot get network id for stack type " + type.getId()
                            + " because it is not registered or not initialized yet.");
        }
        return id;
    }

    public static IAEStackType<?> getType(String id) {
        return registry.get(id);
    }

    public static IAEStackType<?> getTypeFromNetworkId(byte id) {
        return networkIdToTypeMap.get(id);
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
