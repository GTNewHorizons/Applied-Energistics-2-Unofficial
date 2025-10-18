package appeng.client.gui.implementations;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.inventory.Container;

import appeng.client.gui.AEBaseMEGui;
import appeng.client.gui.IGuiSub;
import appeng.client.gui.widgets.GuiTabButton;
import appeng.container.ContainerSubGui;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketSwitchGuis;

public abstract class GuiSub extends AEBaseMEGui implements IGuiSub {

    protected final ContainerSubGui container;
    protected GuiTabButton originalGuiBtn;

    public GuiSub(Container container) {
        super(container);
        this.container = (ContainerSubGui) container;
        this.container.setGuiLink(this);
    }

    @Override
    public void initGui() {
        super.initGui();
        if (container.getPrimaryGuiIcon() != null) initPrimaryGuiButton();
    }

    public void initPrimaryGuiButton() {
        this.buttonList.add(
                this.originalGuiBtn = new GuiTabButton(
                        this.guiLeft + this.xSize - 22,
                        this.guiTop,
                        container.getPrimaryGuiIcon(),
                        container.getPrimaryGuiIcon().getDisplayName(),
                        itemRender));
    }

    @Override
    protected void actionPerformed(final GuiButton btn) {
        super.actionPerformed(btn);
        if (btn == this.originalGuiBtn) {
            NetworkHandler.instance.sendToServer(new PacketSwitchGuis());
        }
    }
}
