package appeng.parts.p2p;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;

import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartItemStack;
import appeng.me.GridAccessException;
import appeng.me.cache.P2PCache;
import appeng.util.Platform;
import buildcraft.api.tools.IToolWrench;

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

            final NBTTagCompound data = mc.getData(is);

            final ItemStack newType = ItemStack.loadItemStackFromNBT(data);
            final long freq = data.getLong("freq");

            if (newType != null) {
                if (newType.getItem() instanceof IPartItem partItem) {
                    final IPart testPart = partItem.createPartFromItemStack(newType);
                    if (this.getClass().isInstance(testPart)) {
                        this.getHost().removePart(this.getSide(), true);
                        final ForgeDirection dir = this.getHost().addPart(newType, this.getSide(), player);
                        final IPart newBus = this.getHost().getPart(dir);

                        if (newBus instanceof PartP2PTunnel<?>newTunnel) {
                            newTunnel.setOutput(true);

                            try {
                                final P2PCache p2p = newTunnel.getProxy().getP2P();
                                p2p.updateFreq(newTunnel, freq);
                                PartP2PTunnel input = p2p.getInput(freq);
                                if (input != null) newTunnel.setCustomNameInternal(input.getCustomName());
                            } catch (final GridAccessException e) {
                                // :P
                            }

                            newTunnel.onTunnelNetworkChange();
                        }

                        mc.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
                        return true;
                    }
                }
            }
            mc.notifyUser(player, MemoryCardMessages.INVALID_MACHINE);
        } else if (!player.isSneaking() && is != null && is.getItem() instanceof IToolWrench && !Platform.isClient()) {
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
}
