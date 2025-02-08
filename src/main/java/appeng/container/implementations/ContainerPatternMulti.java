package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.world.World;

import appeng.api.config.SecurityPermissions;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.BaseActionSourceV2;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.PlayerSourceV2;
import appeng.api.storage.ITerminalHost;
import appeng.container.AEBaseContainer;

public class ContainerPatternMulti extends AEBaseContainer {

    public ContainerPatternMulti(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        this.verifyPermissions(SecurityPermissions.CRAFT, false);
    }

    public IGrid getGrid() {
        final IActionHost h = ((IActionHost) this.getTarget());
        return h.getActionableNode().getGrid();
    }

    public World getWorld() {
        return this.getPlayerInv().player.worldObj;
    }

    public BaseActionSourceV2 getActionSrc() {
        return new PlayerSourceV2(this.getPlayerInv().player, (IActionHost) this.getTarget());
    }
}
