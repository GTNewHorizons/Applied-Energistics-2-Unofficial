package appeng.client.gui.implementations;

import appeng.client.gui.AEBaseGui;
import appeng.container.implementations.ContainerSpatialNetworkRelay;
import appeng.core.localization.GuiText;
import appeng.helpers.Reflected;
import appeng.tile.spatial.TileSpatialNetworkRelay;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

public class GuiSpatialNetworkRelay extends AEBaseGui {

    protected GuiButton tpButton;

    @Reflected
    public GuiSpatialNetworkRelay(final InventoryPlayer inventoryPlayer, final TileSpatialNetworkRelay te) {
        super(new ContainerSpatialNetworkRelay(inventoryPlayer, te));
        this.ySize = 166;
    }

    @Override
    public void initGui() {
        super.initGui();


        this.buttonList.add(
                this.tpButton = new GuiButton(0, this.guiLeft + 128, this.guiTop + 51, 38, 20, GuiText.TeleportInside.getLocal()));
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
//        this.fontRendererObj.drawString(
//                this.getGuiDisplayName(GuiText.SpatialChamber.getLocal()),
//                8,
//                6,
//                GuiColors.SpatialIOTitle.getColor());
//        this.fontRendererObj.drawString(
//                GuiText.inventory.getLocal(),
//                8,
//                this.ySize - 96 + 3,
//                GuiColors.SpatialIOInventory.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/spatialchamber.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }

}
