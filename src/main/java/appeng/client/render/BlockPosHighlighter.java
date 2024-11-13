package appeng.client.render;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;

import appeng.api.util.DimensionalCoord;
import appeng.api.util.WorldCoord;
import appeng.core.localization.PlayerMessages;

// taken from McJty's McJtyLib
public class BlockPosHighlighter {

    private static final List<DimensionalCoord> highlightedBlocks = new ArrayList<>();
    private static long expireHighlightTime;
    private static final int MIN_TIME = 3000;
    private static final int MAX_TIME = MIN_TIME * 10;

    public static void highlightBlocks(EntityPlayer player, List<DimensionalCoord> interfaces) {
        clear();
        int highlightDuration = MIN_TIME;
        for (DimensionalCoord coord : interfaces) {
            if (player.worldObj.provider.dimensionId != coord.getDimension()) {
                player.addChatMessage(
                        new ChatComponentTranslation(
                                PlayerMessages.InterfaceInOtherDim.getName(),
                                coord.getDimension()));
            } else {
                player.addChatMessage(
                        new ChatComponentTranslation(
                                PlayerMessages.InterfaceHighlighted.getName(),
                                coord.x,
                                coord.y,
                                coord.z));
            }
            highlightedBlocks.add(coord);
            highlightDuration = Math.max(
                    highlightDuration,
                    MathHelper.clamp_int(500 * WorldCoord.getTaxicabDistance(coord, player), MIN_TIME, MAX_TIME));
        }
        expireHighlightTime = System.currentTimeMillis() + highlightDuration;
    }

    public static List<DimensionalCoord> getHighlightedBlocks() {
        return highlightedBlocks;
    }

    public static void clear() {
        highlightedBlocks.clear();
        expireHighlightTime = -1;
    }

    public static long getExpireHighlightTime() {
        return expireHighlightTime;
    }
}
