package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;

import org.lwjgl.input.Mouse;

import appeng.api.config.PriorityCardMode;
import appeng.api.config.Settings;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.implementations.ContainerPriorityCard;
import appeng.core.localization.GuiColors;
import appeng.core.localization.GuiText;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketConfigButton;
import appeng.items.contents.PriorityCardObject;

public class GuiPriorityCard extends GuiPriority {

    private final ContainerPriorityCard container;
    private GuiImgButton mode;

    public GuiPriorityCard(final InventoryPlayer inventoryPlayer, final PriorityCardObject host) {
        super(new ContainerPriorityCard(inventoryPlayer, host));
        this.container = (ContainerPriorityCard) this.inventorySlots;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.mode = new GuiImgButton(
                this.guiLeft - 18,
                this.guiTop + 8,
                Settings.PRIORITY_CARD_MODE,
                PriorityCardMode.EDIT);
        this.buttonList.add(this.mode);
    }

    @Override
    public void drawFG(final int offsetX, final int offsetY, final int mouseX, final int mouseY) {
        this.fontRendererObj.drawString(GuiText.PriorityCard.getLocal(), 8, 6, GuiColors.PriorityTitle.getColor());
        if (this.mode != null) {
            this.mode.set(this.container.getCardMode());
        }
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);

        final boolean backwards = Mouse.isButtonDown(1);

        if (btn == this.mode) {
            NetworkHandler.instance.sendToServer(new PacketConfigButton(Settings.PRIORITY_CARD_MODE, backwards));
        }
    }
}
