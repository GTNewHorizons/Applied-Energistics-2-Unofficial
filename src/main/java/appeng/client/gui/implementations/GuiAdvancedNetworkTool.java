package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.implementations.guiobjects.INetworkTool;
import appeng.client.gui.AEBaseGui;
import appeng.client.gui.widgets.GuiToggleButton;
import appeng.container.implementations.ContainerAdvancedNetworkTool;
import appeng.core.AELog;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;

public class GuiAdvancedNetworkTool extends AEBaseGui {

    private GuiToggleButton tFacades;

    public GuiAdvancedNetworkTool(final InventoryPlayer inventoryPlayer, final INetworkTool te) {
        super(new ContainerAdvancedNetworkTool(inventoryPlayer, te));
        this.ySize = 202;
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        try {
            if (btn == this.tFacades) {
                NetworkHandler.instance.sendToServer(new PacketValueConfig("AdvancedNetworkTool", "Toggle"));
            }
        } catch (final IOException e) {
            AELog.debug(e);
        }
    }

    @Override
    public void initGui() {
        super.initGui();

        this.tFacades = new GuiToggleButton(
                this.guiLeft - 18,
                this.guiTop + 8,
                23,
                22,
                GuiText.TransparentFacades.getLocal(),
                GuiText.TransparentFacadesHint.getLocal());

        this.buttonList.add(this.tFacades);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        if (this.tFacades != null) {
            this.tFacades.setState(((ContainerAdvancedNetworkTool) this.inventorySlots).isFacadeMode());
        }

        this.fontRendererObj.drawString(
                this.getGuiDisplayName(GuiText.AdvancedNetworkTool.getLocal()),
                8,
                6,
                GuiColors.AdvancedNetworkToolTitle.getColor());
        this.fontRendererObj.drawString(
                GuiText.inventory.getLocal(),
                8,
                this.ySize - 96 + 3,
                GuiColors.AdvancedNetworkToolInventory.getColor());
    }

    @Override
    public void drawBG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.bindTexture("guis/advancedtoolbox.png");
        this.drawTexturedModalRect(offsetX, offsetY, 0, 0, this.xSize, this.ySize);
    }
}
