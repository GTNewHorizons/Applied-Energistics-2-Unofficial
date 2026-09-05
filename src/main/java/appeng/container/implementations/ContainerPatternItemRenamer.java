package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;

import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEItemStack;
import appeng.items.misc.ItemTunnelPattern;

public class ContainerPatternItemRenamer extends ContainerPatternValueAmount {

    public static final int NETWORK_PATTERN_SLOT = -1;

    public ContainerPatternItemRenamer(final InventoryPlayer ip, final Object te) {
        super(ip, te);
    }

    public boolean isTunnelPatternRename() {
        return this.getInvName() == StorageName.NONE && this.getAEStack() instanceof IAEItemStack item
                && ItemTunnelPattern.getTunnelUuid(item.getItemStack()) != null;
    }
}
