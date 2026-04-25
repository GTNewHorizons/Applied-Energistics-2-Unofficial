package appeng.client.render.previewBlocks;

import java.util.List;

import appeng.parts.networking.PartQuartzFiber;

public class RendererQuartzFiber extends AbstractRendererPreview implements IRenderPreview {

    @Override
    public void renderPreview() {
        renderCommonPreview(() -> { renderBase(6.0, 6.0, 10.0, 10.0, 10.0, 16.0); });
    }

    @Override
    public List<Class<?>> validItemClass() {
        return ViewHelper.getValidClasses(PartQuartzFiber.class);
    }
}
