package appeng.client.gui;

import java.util.List;

import appeng.core.sync.packets.PacketIfaceTermUpdate.PacketEntry;

public interface IInterfaceTerminalPostUpdate {

    /**
     * Interface to handle updates inside interface terminal
     *
     * @param updates     List of updates
     * @param statusFlags status bitflags
     */
    void postUpdate(List<PacketEntry> updates, int statusFlags);
}
