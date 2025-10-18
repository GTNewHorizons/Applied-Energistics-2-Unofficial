package appeng.parts.p2p;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.parts.IPart;
import appeng.api.parts.PartItemStack;
import appeng.util.Platform;

/**
 * Static P2P tunnels cannot be attuned to. They can only be bound to each other.
 */
public abstract class PartP2PTunnelStatic<T extends PartP2PTunnelStatic> extends PartP2PTunnel<T> {

    public PartP2PTunnelStatic(ItemStack is) {
        super(is);
    }

    @Override
    public boolean onPartActivate(EntityPlayer player, Vec3 pos) {
        final ItemStack is = player.inventory.getCurrentItem();

        if (is != null && is.getItem() instanceof IMemoryCard mc) {
            if (ForgeEventFactory.onItemUseStart(player, is, 1) <= 0) return false;
            return applyMemoryCard(player, mc, is) != null;
        } else if (!player.isSneaking() && Platform.isServer()
                && Platform.isWrench(player, is, (int) pos.xCoord, (int) pos.yCoord, (int) pos.zCoord)) {
                    printConnectionInfo(player);
                }
        return false;
    }

    @Override
    public ItemStack getItemStack(PartItemStack type) {
        if (type == PartItemStack.World || type == PartItemStack.Network || type == PartItemStack.Wrench) {
            return super.getItemStack(type);
        }
        return super.getItemStack(PartItemStack.Pick);
    }

    @Override
    protected boolean checkIfCompatibleType(final IPart testPart) {
        return this.getClass().isInstance(testPart);
    }
}
