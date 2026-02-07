package appeng.client.gui.implementations;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.client.gui.AEBaseGui;
import appeng.container.implementations.ContainerSpatialLinkChamber;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.tile.spatial.TileSpatialLinkChamber;

public class GuiSpatialLinkChamber extends AEBaseGui {

    public GuiSpatialLinkChamber(final InventoryPlayer inventoryPlayer, final TileSpatialLinkChamber te) {
        super(new ContainerSpatialLinkChamber(inventoryPlayer, te));
        this.ySize = 166;
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.SpatialChamber.getLocal()),
                8,
                6,
                GuiColors.SpatialIOTitle.getColor());
        this.fontRendererObj.drawString(
                GuiText.inventory.getLocal(),
                8,
                this.ySize - 96 + 3,
                GuiColors.SpatialIOInventory.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/spatialchamber.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }
}
