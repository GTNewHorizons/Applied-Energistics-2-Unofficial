package appeng.client.render.previewBlocks;

import java.util.List;

public interface IRenderPreview {

    void renderPreview();

    List<Class<?>> validItemClass();
}
