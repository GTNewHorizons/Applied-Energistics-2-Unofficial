package appeng.client.render.previewBlocks;

import java.util.List;

import com.glodblock.github.common.parts.PartFluidLevelEmitter;

import appeng.parts.automation.PartLevelEmitter;
import appeng.util.Platform;

public class RendererLevelEmitter extends AbstractRendererPreview implements IRenderPreview {

    @Override
    public void renderPreview() {
        renderCommonPreview(() -> { renderBase(7.0, 7.0, 11.0, 9.0, 9.0, 16.0); });
    }

    @Override
    public List<Class<?>> validItemClass() {
        if (Platform.isAE2FCLoaded)
            return ViewHelper.getValidClasses(PartLevelEmitter.class, PartFluidLevelEmitter.class);
        else return ViewHelper.getValidClasses(PartLevelEmitter.class);
    }
}
