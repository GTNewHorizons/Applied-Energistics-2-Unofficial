package appeng.client.render.highlighter;

import java.util.HashSet;
import java.util.Set;

import net.minecraftforge.client.event.RenderWorldLastEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public class HighlighterManager {

    public static final Set<IHighlighter> HIGHLIGHTERS = new HashSet<>();

    static long time = System.currentTimeMillis();

    static {
        registerHighlighter(StoragePosHighlighter.INSTANCE);
        registerHighlighter(BlockPosHighlighter.INSTANCE);
    }

    static void registerHighlighter(IHighlighter h) {
        HIGHLIGHTERS.add(h);
    }

    @SubscribeEvent
    public void renderHighlightedBlocks(RenderWorldLastEvent event) {
        time = System.currentTimeMillis();
        if (((time / 500) & 1) == 0) {
            // this does the blinking effect
            return;
        }
        for (IHighlighter h : HIGHLIGHTERS) {
            if (h.noWork()) continue;
            if (hasExpireHighlightTime(h)) {
                h.clear();
                continue;
            }
            h.renderHighlightedBlocks(event);
        }
    }

    private static boolean hasExpireHighlightTime(IHighlighter h) {
        return HighlighterManager.time > h.getExpireTime();
    }
}
