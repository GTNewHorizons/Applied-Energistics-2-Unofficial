package appeng.client.render.previewBlocks;

import java.util.List;

import com.glodblock.github.common.parts.PartFluidInterface;
import com.glodblock.github.common.parts.PartFluidPatternTerminal;
import com.glodblock.github.common.parts.PartFluidPatternTerminalEx;
import com.glodblock.github.common.parts.PartFluidStorageBus;
import com.glodblock.github.common.parts.PartFluidTerminal;
import com.glodblock.github.common.parts.PartLevelTerminal;

import appeng.parts.misc.PartInterface;
import appeng.parts.misc.PartStorageBus;
import appeng.parts.p2p.PartP2PTunnel;
import appeng.parts.reporting.AbstractPartDisplay;
import appeng.parts.reporting.PartDarkPanel;
import appeng.parts.reporting.PartPanel;
import appeng.parts.reporting.PartSemiDarkPanel;

public class RendererTerminal extends AbstractRendererPreview implements IRenderPreview {

    @Override
    public void renderPreview() {
        renderCommonPreview(() -> {
            renderBase(2.0, 2.0, 14.0, 14.0, 14.0, 16.0);
            renderBase(4.0, 4.0, 13.0, 12.0, 12.0, 14.0);
        });
    }

    @Override
    public List<Class<?>> validItemClass() {
        return ViewHelper.getValidClasses(
                AbstractPartDisplay.class,
                PartFluidTerminal.class,
                PartP2PTunnel.class,
                PartPanel.class,
                PartSemiDarkPanel.class,
                PartDarkPanel.class,
                PartStorageBus.class,
                PartFluidStorageBus.class,
                PartInterface.class,
                PartFluidInterface.class,
                PartFluidPatternTerminal.class,
                PartFluidPatternTerminalEx.class,
                PartLevelTerminal.class);
    }
}
