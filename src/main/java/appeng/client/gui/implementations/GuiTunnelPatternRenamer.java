package appeng.client.gui.implementations;

import java.io.IOException;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.container.TunnelPatternRenamerHost;
import appeng.container.implementations.ContainerTunnelPatternRenamer;
import appeng.core.AELog;
import appeng.core.sync.network.NetworkHandler;
import appeng.core.sync.packets.PacketValueConfig;
import appeng.helpers.TunnelPatternRenaming;

public class GuiTunnelPatternRenamer extends GuiPatternItemRenamer {

    public GuiTunnelPatternRenamer(final InventoryPlayer ip, final TunnelPatternRenamerHost host) {
        super(new ContainerTunnelPatternRenamer(ip, host));
        this.textField.setMaxStringLength(TunnelPatternRenaming.MAX_NAME_LENGTH);
    }

    @Override
    protected void submitName(final String name) {
        try {
            NetworkHandler.instance.sendToServer(new PacketValueConfig("TunnelPattern.Rename", name));
        } catch (IOException e) {
            AELog.debug(e);
        }
    }
}
