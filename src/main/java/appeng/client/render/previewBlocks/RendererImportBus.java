package appeng.client.render.previewBlocks;

import java.util.List;

import com.glodblock.github.common.parts.PartFluidImportBus;

import appeng.parts.automation.PartImportBus;
import appeng.util.Platform;

public class RendererImportBus extends AbstractRendererPreview implements IRenderPreview {

    @Override
    public void renderPreview() {
        renderCommonPreview(() -> {
            renderBase(4.0, 4.0, 14.0, 12.0, 12.0, 16.0);
            renderBase(5.0, 5.0, 13.0, 11.0, 11.0, 14.0);
            renderBase(6.0, 6.0, 11.0, 10.0, 10.0, 13.0);
        });
    }

    @Override
    public List<Class<?>> validItemClass() {
        if (Platform.isAE2FCLoaded) return ViewHelper.getValidClasses(PartImportBus.class, PartFluidImportBus.class);
        else return ViewHelper.getValidClasses(PartImportBus.class);
    }
}
