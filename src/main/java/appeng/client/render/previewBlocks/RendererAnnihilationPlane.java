package appeng.client.render.previewBlocks;

import java.util.List;

import appeng.parts.automation.PartAnnihilationPlane;
import appeng.parts.automation.PartFormationPlane;

public class RendererAnnihilationPlane extends AbstractRendererPreview implements IRenderPreview {

    @Override
    public void renderPreview() {
        renderCommonPreview(() -> {
            renderBase(1.0, 1.0, 15.0, 15.0, 15.0, 16.0);
            renderBase(5.0, 5.0, 14.0, 11.0, 11.0, 15.0);
        });
    }

    @Override
    public List<Class<?>> validItemClass() {
        return ViewHelper.getValidClasses(PartAnnihilationPlane.class, PartFormationPlane.class);
    }
}
