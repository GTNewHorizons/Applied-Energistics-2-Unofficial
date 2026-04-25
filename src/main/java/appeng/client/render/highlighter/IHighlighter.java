package appeng.client.render.highlighter;

import net.minecraftforge.client.event.RenderWorldLastEvent;

public interface IHighlighter {

    boolean noWork();

    void renderHighlightedBlocks(RenderWorldLastEvent event);

    void clear();

    long getExpireTime();
}
