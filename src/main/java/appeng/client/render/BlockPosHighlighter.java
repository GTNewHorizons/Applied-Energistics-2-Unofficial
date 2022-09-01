package appeng.client.render;

import appeng.api.util.DimensionalCoord;

// taken from McJty's McJtyLib
public class BlockPosHighlighter {
    private static DimensionalCoord highlightedBlock;
    private static long expireHighlight;

    public static void highlightBlock(DimensionalCoord c, long expireHighlight) {
        highlightedBlock = c;
        BlockPosHighlighter.expireHighlight = expireHighlight;
    }

    public static DimensionalCoord getHighlightedBlock() {
        return highlightedBlock;
    }

    public static long getExpireHighlight() {
        return expireHighlight;
    }
}
