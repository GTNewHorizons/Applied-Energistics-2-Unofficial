package appeng.client.gui.implementations;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.storage.ITerminalHost;
import appeng.container.implementations.ContainerTunnelPatternRenamer;
import appeng.helpers.TunnelPatternRenaming;

public class GuiTunnelPatternRenamer extends GuiPatternItemRenamer {

    public GuiTunnelPatternRenamer(final InventoryPlayer ip, final ITerminalHost host) {
        super(new ContainerTunnelPatternRenamer(ip, host));
        this.textField.setMaxStringLength(TunnelPatternRenaming.MAX_NAME_LENGTH);
    }

    @Override
    protected void submitName(final String name) {
        ((ContainerTunnelPatternRenamer) this.inventorySlots).renameAction.send(name);
    }
}
