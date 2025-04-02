package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.world.World;

import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionHost;
import appeng.api.storage.ITerminalHost;
import appeng.container.AEBaseContainer;
import appeng.container.slot.SlotInaccessible;
import appeng.tile.inventory.AppEngInternalInventory;

public class ContainerPatternItemRenamer extends AEBaseContainer {

    private final Slot patternValue;

    public ContainerPatternItemRenamer(final InventoryPlayer ip, final ITerminalHost te) {
        super(ip, te);
        patternValue = new SlotInaccessible(new AppEngInternalInventory(null, 1), 0, 34, 53);
    }

    public IGrid getGrid() {
        final IActionHost h = ((IActionHost) getTarget());
        return h.getActionableNode().getGrid();
    }

    public World getWorld() {
        return getPlayerInv().player.worldObj;
    }

    public Slot getPatternValue() {
        return patternValue;
    }
}
