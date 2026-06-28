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

    private static final String NBT = "terminalSettings";
    private static final String NBT_UUID_MOST = "uuid_m";
    private static final String NBT_UUID_LEAST = "uuid_l";

    public void readFromNBT(@Nullable NBTTagCompound tag) {
        this.perPlayer.clear();

        if (tag == null || !tag.hasKey(NBT, Constants.NBT.TAG_LIST)) {
            return;
        }

        final NBTTagList players = tag.getTagList(NBT, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < players.tagCount(); i++) {
            final NBTTagCompound playerTag = players.getCompoundTagAt(i);

            final UUID id;
            if (playerTag.hasKey(NBT_UUID_MOST)) {
                id = new UUID(playerTag.getLong(NBT_UUID_MOST), playerTag.getLong(NBT_UUID_LEAST));
            } else continue;

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
            playerTag.setLong(NBT_UUID_MOST, id.getMostSignificantBits());
            playerTag.setLong(NBT_UUID_LEAST, id.getLeastSignificantBits());

            settings.writeToNBT(playerTag);
            players.appendTag(playerTag);
        }

        if (players.tagCount() == 0) {
            tag.removeTag(NBT);
        } else {
            tag.setTag(NBT, players);
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
