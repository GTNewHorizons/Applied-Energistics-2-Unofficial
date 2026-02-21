package appeng.items.misc;

import java.util.UUID;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

public class ItemTunnelPattern extends ItemEncodedUltimatePattern {

    public static final String TAG_TUNNEL = "tunnel";
    public static final String TAG_TUNNEL_UUID = "tunnelUuid";

    public static boolean isTunnelPattern(final ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemTunnelPattern;
    }

    public static UUID getTunnelUuid(final ItemStack stack) {
        if (!isTunnelPattern(stack)) {
            return null;
        }
        final NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) {
            return null;
        }
        if (!tag.getBoolean(TAG_TUNNEL)) {
            return null;
        }
        final String rawUuid = tag.getString(TAG_TUNNEL_UUID);
        if (rawUuid == null || rawUuid.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(rawUuid);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static void writeTunnelUuid(final NBTTagCompound tag, final UUID uuid) {
        tag.setBoolean(TAG_TUNNEL, true);
        tag.setString(TAG_TUNNEL_UUID, uuid.toString());
    }
}
