package appeng.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class TerminalSettings {

    private final Map<UUID, TerminalPlayerSettings> perPlayer = new HashMap<>();
    private static final String NBT_FILTERS = "typeFilters";
    private static final String NBT_UUID = "uuid";

    public void readFromNBT(@Nullable NBTTagCompound tag) {
        this.perPlayer.clear();

        if (tag == null || !tag.hasKey(NBT_FILTERS, Constants.NBT.TAG_LIST)) {
            return;
        }

        final NBTTagList players = tag.getTagList(NBT_FILTERS, Constants.NBT.TAG_COMPOUND);
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

            final TerminalPlayerSettings settings = new TerminalPlayerSettings();
            settings.readFromNBT(playerTag);
            this.perPlayer.put(id, settings);
        }
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
        final NBTTagList players = new NBTTagList();

        for (Map.Entry<UUID, TerminalPlayerSettings> entry : this.perPlayer.entrySet()) {
            final UUID id = entry.getKey();
            final TerminalPlayerSettings settings = entry.getValue();

            final NBTTagCompound playerTag = new NBTTagCompound();
            playerTag.setString(NBT_UUID, id.toString());
            settings.writeToNBT(playerTag);
            players.appendTag(playerTag);
        }

        if (players.tagCount() == 0) {
            tag.removeTag(NBT_FILTERS);
        } else {
            tag.setTag(NBT_FILTERS, players);
        }
    }

    public TerminalPlayerSettings getSettings(EntityPlayer player) {
        if (player == null || player.getGameProfile() == null) {
            return new TerminalPlayerSettings();
        }

        UUID id = player.getGameProfile().getId();
        if (id == null) {
            id = player.getUniqueID();
        }

        final TerminalPlayerSettings settings = this.perPlayer.get(id);
        if (settings == null) this.perPlayer.put(id, new TerminalPlayerSettings());
        return this.perPlayer.get(id);
    }

    public AEStackTypeFilter getFilters(EntityPlayer player) {
        return this.getSettings(player).getTypeFilters();
    }

    public void setSavedSearchString(String searchString, EntityPlayer player) {
        this.getSettings(player).setSavedSearchString(searchString);
    }

    public String getSavedSearchString(EntityPlayer player) {
        return this.getSettings(player).getSavedString();
    }
}
