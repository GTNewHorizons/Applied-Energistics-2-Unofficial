package appeng.client.render;

import java.util.ArrayList;
import java.util.List;

import appeng.api.util.DimensionalCoord;

// taken from McJty's McJtyLib
public class BlockPosHighlighter {

    private static final List<DimensionalCoord> highlightedBlocks = new ArrayList<>();
    private static long expireHighlight;
    private static final int min = 3000;
    private static final int max = min * 10;

    public static void highlightBlock(DimensionalCoord c, long expireHighlight) {
        highlightBlock(c, expireHighlight, true);
    }

    public static void highlightBlock(DimensionalCoord c, long expireHighlight, boolean clear) {
        if (clear) clear();
        highlightedBlocks.add(c);
        BlockPosHighlighter.expireHighlight = Math.max(
                BlockPosHighlighter.expireHighlight,
                Math.min(
                        System.currentTimeMillis() + max,
                        Math.max(expireHighlight, System.currentTimeMillis() + min)));
    }

    public static List<DimensionalCoord> getHighlightedBlocks() {
        return highlightedBlocks;
    }

    public static DimensionalCoord getHighlightedBlock() {
        return highlightedBlocks.isEmpty() ? null : highlightedBlocks.get(0);
    }

    public static void clear() {
        highlightedBlocks.clear();
        expireHighlight = -1;
    }

    public static void remove(DimensionalCoord c) {
        highlightedBlocks.remove(c);
    }

    public static long getExpireHighlight() {
        return expireHighlight;
    }
}
