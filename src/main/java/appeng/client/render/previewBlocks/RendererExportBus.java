package appeng.client.render.previewBlocks;

import java.util.List;

import com.glodblock.github.common.parts.PartFluidExportBus;

import appeng.parts.automation.PartExportBus;

public class RendererExportBus extends AbstractRendererPreview implements IRenderPreview {

    @Override
    public void renderPreview() {
        renderCommonPreview(() -> {
            renderBase(4.0, 4.0, 12.0, 12.0, 12.0, 14.0);
            renderBase(5.0, 5.0, 14.0, 11.0, 11.0, 15.0);
            renderBase(6.0, 6.0, 15.0, 10.0, 10.0, 16.0);
            renderBase(6.0, 6.0, 11.0, 10.0, 10.0, 12.0);
        });
    }

    @Override
    public List<Class<?>> validItemClass() {
        return ViewHelper.getValidClasses(PartExportBus.class, PartFluidExportBus.class);
    }
}
