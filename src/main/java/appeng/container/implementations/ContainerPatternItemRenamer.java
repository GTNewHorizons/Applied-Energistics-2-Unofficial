package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.storage.ITerminalHost;

public class ContainerPatternItemRenamer extends ContainerPatternValueAmount {

    public ContainerPatternItemRenamer(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }
}
