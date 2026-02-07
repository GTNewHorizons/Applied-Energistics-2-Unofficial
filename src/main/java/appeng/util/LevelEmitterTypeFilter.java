package appeng.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import org.jetbrains.annotations.NotNull;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import appeng.container.guisync.IGuiPacketWritable;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;
import it.unimi.dsi.fastutil.objects.Reference2BooleanOpenHashMap;

public class LevelEmitterTypeFilter implements IGuiPacketWritable {

    public static final String NBT_FILTERS = "typeFilters";
    private static final String NBT_TYPE_ID = "typeId";
    private static final String NBT_VALUE = "value";

    @NotNull
    private final Reference2BooleanMap<IAEStackType<?>> filters;

    public LevelEmitterTypeFilter() {
        this.filters = createDefaultMap();
    }

    public LevelEmitterTypeFilter(@NotNull final LevelEmitterTypeFilter other) {
        this.filters = new Reference2BooleanOpenHashMap<>();
        for (Reference2BooleanMap.Entry<IAEStackType<?>> entry : other.getFilters().reference2BooleanEntrySet()) {
            this.filters.put(entry.getKey(), entry.getBooleanValue());
        }
    }

    // For IGuiPacketWritable
    public LevelEmitterTypeFilter(final ByteBuf buf) {
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
    public static Reference2BooleanMap<IAEStackType<?>> createDefaultMap() {
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

        final NBTTagList list = tag.getTagList(NBT_FILTERS, 10);
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

    @NotNull
    public Reference2BooleanMap<IAEStackType<?>> getFilters() {
        return this.filters;
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
        if (!(obj instanceof LevelEmitterTypeFilter other)) {
            return false;
        }
        return this.filters.equals(other.filters);
    }

    @Override
    public int hashCode() {
        return this.filters.hashCode();
    }
}
