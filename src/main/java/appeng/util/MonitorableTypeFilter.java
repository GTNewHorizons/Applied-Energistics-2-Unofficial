package appeng.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.jetbrains.annotations.NotNull;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;

public class MonitorableTypeFilter {

    private static final String NBT_FILTERS = "typeFilters";
    private static final String NBT_UUID = "uuid";
    private static final String NBT_MAP = "map";
    private static final String NBT_TYPE_ID = "typeId";
    private static final String NBT_VALUE = "value";

    private final Map<UUID, Reference2BooleanMap<IAEStackType<?>>> perPlayer = new HashMap<>();

    public MonitorableTypeFilter() {}

    @NotNull
    public static Reference2BooleanMap<IAEStackType<?>> createDefaultMap() {
        final Reference2BooleanMap<IAEStackType<?>> map = new Reference2BooleanOpenHashMap<>();
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            map.put(type, true);
        }
        return map;
    }

    public void readFromNBT(NBTTagCompound tag) {
        this.perPlayer.clear();

        if (!tag.hasKey(NBT_FILTERS)) {
            return;
        }

        final NBTTagList players = tag.getTagList(NBT_FILTERS, 10);
        for (int i = 0; i < players.tagCount(); i++) {
            final NBTTagCompound playerTag = players.getCompoundTagAt(i);
            final String uuidString = playerTag.getString(NBT_UUID);
            if (uuidString == null || uuidString.isEmpty()) {
                continue;
            }

            final UUID id;
            try {
                id = UUID.fromString(uuidString);
            } catch (IllegalArgumentException e) {
                continue;
            }

            final Reference2BooleanMap<IAEStackType<?>> map = createDefaultMap();

            final NBTTagList list = playerTag.getTagList(NBT_MAP, 10);
            for (int j = 0; j < list.tagCount(); j++) {
                final NBTTagCompound entryTag = list.getCompoundTagAt(j);
                final String typeId = entryTag.getString(NBT_TYPE_ID);
                final boolean value = entryTag.getBoolean(NBT_VALUE);
                final IAEStackType<?> type = AEStackTypeRegistry.getType(typeId);
                if (type != null) {
                    map.put(type, value);
                }
            }

            this.perPlayer.put(id, map);
        }
    }

    public void writeToNBT(NBTTagCompound tag) {
        final NBTTagList players = new NBTTagList();

        for (Map.Entry<UUID, Reference2BooleanMap<IAEStackType<?>>> entry : this.perPlayer.entrySet()) {
            final UUID id = entry.getKey();
            final Reference2BooleanMap<IAEStackType<?>> map = entry.getValue();

            final NBTTagCompound playerTag = new NBTTagCompound();
            playerTag.setString(NBT_UUID, id.toString());

            final NBTTagList list = new NBTTagList();
            boolean foundNonDefault = false;
            for (Reference2BooleanMap.Entry<IAEStackType<?>> filterEntry : map.reference2BooleanEntrySet()) {
                final boolean value = filterEntry.getBooleanValue();
                if (value) {
                    continue; // Skip as default if true
                }
                foundNonDefault = true;

                final NBTTagCompound entryTag = new NBTTagCompound();
                entryTag.setString(NBT_TYPE_ID, filterEntry.getKey().getId());
                entryTag.setBoolean(NBT_VALUE, value);
                list.appendTag(entryTag);
            }

            if (!foundNonDefault) continue;

            playerTag.setTag(NBT_MAP, list);
            players.appendTag(playerTag);
        }

        tag.setTag(NBT_FILTERS, players);
    }

    @NotNull
    public Reference2BooleanMap<IAEStackType<?>> getFilters(EntityPlayer player) {
        if (player == null || player.getGameProfile() == null) {
            return createDefaultMap();
        }

        UUID id = player.getGameProfile().getId();
        if (id == null) {
            id = player.getUniqueID();
        }

        Reference2BooleanMap<IAEStackType<?>> playerMap = this.perPlayer.get(id);
        if (playerMap == null) {
            playerMap = createDefaultMap();
            this.perPlayer.put(id, playerMap);
        }

        return playerMap;
    }
}
