package appeng.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;

import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;

public class TerminalPlayerSettings {

    private static final String NBT_MAP = "map";
    private static final String NBT_TYPE_ID = "typeId";
    private static final String NBT_VALUE = "value";
    private static final String NBT_SAVED_STRING = "savedString";

    private final AEStackTypeFilter typeFilters = new AEStackTypeFilter();
    private String savedString = "";

    public void readFromNBT(NBTTagCompound tag) {
        final NBTTagList list = tag.getTagList(NBT_MAP, Constants.NBT.TAG_COMPOUND);
        for (int j = 0; j < list.tagCount(); j++) {
            final NBTTagCompound entryTag = list.getCompoundTagAt(j);
            final String typeId = entryTag.getString(NBT_TYPE_ID);
            final boolean value = entryTag.getBoolean(NBT_VALUE);
            final IAEStackType<?> type = AEStackTypeRegistry.getType(typeId);
            if (type != null) typeFilters.setEnabled(type, value);
        }
        this.savedString = tag.getString(NBT_SAVED_STRING);
    }

    public void writeToNBT(@NotNull ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        this.writeToNBT(stack.getTagCompound());
        if (stack.getTagCompound().hasNoTags()) {
            stack.setTagCompound(null);
        }
    }

    public void writeToNBT(NBTTagCompound tag) {
        final NBTTagList list = new NBTTagList();
        for (Reference2BooleanMap.Entry<IAEStackType<?>> filterEntry : this.typeFilters.getImmutableFilters()
                .reference2BooleanEntrySet()) {
            final boolean value = filterEntry.getBooleanValue();
            if (value) continue; // Skip as default if true

            final NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setString(NBT_TYPE_ID, filterEntry.getKey().getId());
            entryTag.setBoolean(NBT_VALUE, false);
            list.appendTag(entryTag);
        }

        tag.setTag(NBT_MAP, list);
        tag.setString(NBT_SAVED_STRING, this.savedString);
    }

    public AEStackTypeFilter getTypeFilters() {
        return this.typeFilters;
    }

    public void setSavedSearchString(String savedSearchString) {
        this.savedString = savedSearchString;
    }

    public String getSavedString() {
        return this.savedString;
    }
}
