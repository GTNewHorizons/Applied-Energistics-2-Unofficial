package appeng.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants.NBT;

import org.jetbrains.annotations.NotNull;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.container.guisync.IGuiPacketWritable;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMaps;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;

public class AEStackTypeFilter implements IGuiPacketWritable {

    public static final String NBT_FILTERS = "typeFilters";
    private static final String NBT_TYPE_ID = "typeId";
    private static final String NBT_VALUE = "value";

    @NotNull
    private final Reference2BooleanMap<IAEStackType<?>> filters;

    public AEStackTypeFilter() {
        this.filters = createDefaultMap();
    }

    public AEStackTypeFilter(@NotNull final AEStackTypeFilter other) {
        this.filters = new Reference2BooleanOpenHashMap<>();
        for (Reference2BooleanMap.Entry<IAEStackType<?>> entry : other.filters.reference2BooleanEntrySet()) {
            this.filters.put(entry.getKey(), entry.getBooleanValue());
        }
    }

    // For IGuiPacketWritable
    public AEStackTypeFilter(final ByteBuf buf) {
        this.filters = createDefaultMap();

        final int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            final String typeId = ByteBufUtils.readUTF8String(buf);
            final boolean value = buf.readBoolean();
            final IAEStackType<?> type = AEStackTypeRegistry.getType(typeId);
            if (type != null) {
                this.filters.put(type, value);
            }
        }
    }

    @NotNull
    private static Reference2BooleanMap<IAEStackType<?>> createDefaultMap() {
        final Reference2BooleanMap<IAEStackType<?>> map = new Reference2BooleanOpenHashMap<>();
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            map.put(type, true);
        }
        return map;
    }

    public void readFromNBT(final NBTTagCompound tag) {
        if (!tag.hasKey(NBT_FILTERS)) {
            return;
        }

        this.filters.clear();
        this.filters.putAll(createDefaultMap());

        final NBTTagList list = tag.getTagList(NBT_FILTERS, NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            final NBTTagCompound entryTag = list.getCompoundTagAt(i);
            final String typeId = entryTag.getString(NBT_TYPE_ID);
            final boolean value = entryTag.getBoolean(NBT_VALUE);
            final IAEStackType<?> type = AEStackTypeRegistry.getType(typeId);
            if (type != null) {
                this.filters.put(type, value);
            }
        }
    }

    public void writeToNBT(final NBTTagCompound tag) {
        final NBTTagList list = new NBTTagList();
        for (Reference2BooleanMap.Entry<IAEStackType<?>> entry : this.filters.reference2BooleanEntrySet()) {
            final boolean value = entry.getBooleanValue();
            if (value) {
                continue; // Skip default
            }

            final NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setString(NBT_TYPE_ID, entry.getKey().getId());
            entryTag.setBoolean(NBT_VALUE, value);
            list.appendTag(entryTag);
        }

        tag.setTag(NBT_FILTERS, list);
    }

    public boolean isEnabled(@NotNull final IAEStackType<?> type) {
        return this.filters.getBoolean(type);
    }

    public void copyFrom(@NotNull final AEStackTypeFilter other) {
        this.filters.clear();
        this.filters.putAll(createDefaultMap());
        for (Reference2BooleanMap.Entry<IAEStackType<?>> entry : other.filters.reference2BooleanEntrySet()) {
            this.filters.put(entry.getKey(), entry.getBooleanValue());
        }
    }

    @NotNull
    public Reference2BooleanMap<IAEStackType<?>> getImmutableFilters() {
        return Reference2BooleanMaps.unmodifiable(new Reference2BooleanOpenHashMap<>(this.filters));
    }

    public void setEnabled(@NotNull final IAEStackType<?> type, final boolean enabled) {
        this.filters.put(type, enabled);
    }

    public boolean toggle(@NotNull final IAEStackType<?> type) {
        final boolean next = !this.filters.getBoolean(type);
        this.filters.put(type, next);
        return next;
    }

    public void setOnlyEnabled(@NotNull final IAEStackType<?> targetType) {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.filters.put(type, type == targetType);
        }
    }

    public void setAllEnabled(final boolean enabled) {
        for (IAEStackType<?> type : AEStackTypeRegistry.getAllTypes()) {
            this.filters.put(type, enabled);
        }
    }

    @NotNull
    public Iterable<IAEStackType<?>> getEnabledTypes() {
        final List<IAEStackType<?>> enabledTypes = new ArrayList<>();
        for (Reference2BooleanMap.Entry<IAEStackType<?>> entry : this.filters.reference2BooleanEntrySet()) {
            if (entry.getBooleanValue()) {
                enabledTypes.add(entry.getKey());
            }
        }
        return enabledTypes;
    }

    @Override
    public void writeToPacket(final ByteBuf buf) {
        buf.writeInt(this.filters.size());
        for (Reference2BooleanMap.Entry<IAEStackType<?>> entry : this.filters.reference2BooleanEntrySet()) {
            ByteBufUtils.writeUTF8String(buf, entry.getKey().getId());
            buf.writeBoolean(entry.getBooleanValue());
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AEStackTypeFilter other)) {
            return false;
        }
        return this.filters.equals(other.filters);
    }

    @Override
    public int hashCode() {
        return this.filters.hashCode();
    }
}
