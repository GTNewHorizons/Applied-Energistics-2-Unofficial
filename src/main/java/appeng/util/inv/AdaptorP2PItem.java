package appeng.util.inv;

import net.minecraftforge.common.util.ForgeDirection;

import appeng.parts.p2p.PartP2PItems;

public class AdaptorP2PItem extends AdaptorIInventory {

    public AdaptorP2PItem(PartP2PItems p2p, ForgeDirection side) {
        super(new WrapperMCISidedInventory(p2p, side), p2p.getInventoryStackLimit());
    }
}
