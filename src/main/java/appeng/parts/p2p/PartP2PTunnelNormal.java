package appeng.parts.p2p;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;

import appeng.api.AEApi;
import appeng.api.config.TunnelType;
import appeng.api.definitions.IParts;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.me.GridAccessException;
import appeng.me.cache.P2PCache;
import appeng.util.Platform;

/**
 * Normal P2P Tunnels can be attuned between each other, but cannot be attuned to Static P2P tunnels.
 */
public class PartP2PTunnelNormal<T extends PartP2PTunnelNormal> extends PartP2PTunnel<T> {

    public PartP2PTunnelNormal(ItemStack is) {
        super(is);
    }

    @Override
    public boolean onPartActivate(final EntityPlayer player, final Vec3 pos) {
        final ItemStack is = player.inventory.getCurrentItem();

        // UniqueIdentifier id = GameRegistry.findUniqueIdentifierFor( is.getItem() );
        // AELog.info( "ID:" + id.toString() + " : " + is.getItemDamage() );

        final TunnelType tt = AEApi.instance().registries().p2pTunnel().getTunnelTypeByItem(is);
        if (is != null && is.getItem() instanceof IMemoryCard mc) {
            if (ForgeEventFactory.onItemUseStart(player, is, 1) <= 0) return false;

            final NBTTagCompound data = mc.getData(is);

            final ItemStack newType = ItemStack.loadItemStackFromNBT(data);
            final long freq = data.getLong("freq");

            if (newType != null) {
                if (newType.getItem() instanceof IPartItem) {
                    final IPart testPart = ((IPartItem) newType.getItem()).createPartFromItemStack(newType);
                    if (testPart instanceof PartP2PTunnelNormal<?>) {
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
        } else if (!player.isSneaking()
                && Platform.isWrench(player, is, (int) pos.xCoord, (int) pos.yCoord, (int) pos.zCoord)
                && !Platform.isClient()) {
                    printConnectionInfo(player);
                    // spotless:off
        } else if (tt != null) { // attunement
            ItemStack newType = null;

            final IParts parts = AEApi.instance().definitions().parts();

            switch (tt) {
                case LIGHT -> {
                    for (final ItemStack stack : parts.p2PTunnelLight().maybeStack(1).asSet()) {
                        newType = stack;
                    }
                }
                case RF_POWER -> {
                    for (final ItemStack stack : parts.p2PTunnelRF().maybeStack(1).asSet()) {
                        newType = stack;
                    }
                }
                case FLUID -> {
                    for (final ItemStack stack : parts.p2PTunnelLiquids().maybeStack(1).asSet()) {
                        newType = stack;
                    }
                }
                case IC2_POWER -> {
                    for (final ItemStack stack : parts.p2PTunnelEU().maybeStack(1).asSet()) {
                        newType = stack;
                    }
                }
                case ITEM -> {
                    for (final ItemStack stack : parts.p2PTunnelItems().maybeStack(1).asSet()) {
                        newType = stack;
                    }
                }
                case ME -> {
                    for (final ItemStack stack : parts.p2PTunnelME().maybeStack(1).asSet()) {
                        newType = stack;
                    }
                }
                case REDSTONE -> {
                    for (final ItemStack stack : parts.p2PTunnelRedstone().maybeStack(1).asSet()) {
                        newType = stack;
                    }
                }
                case COMPUTER_MESSAGE -> {
                    for (final ItemStack stack : parts.p2PTunnelOpenComputers().maybeStack(1).asSet()) {
                        newType = stack;
                    }
                }
                case PRESSURE -> {
                    for (final ItemStack stack : parts.p2PTunnelPneumaticCraft().maybeStack(1).asSet()) {
                        newType = stack;
                    }
                }
                case GT_POWER -> {
                    for (final ItemStack stack : parts.p2PTunnelGregtech().maybeStack(1).asSet()) {
                        newType = stack;
                    }
                }
                default -> {}
            }

            if (newType != null && !Platform.isSameItem(newType, this.getItemStack())) {
                if (new Throwable().getStackTrace()[2].getMethodName().equals("place")) return true;
                final boolean oldOutput = this.isOutput();
                final long myFreq = this.getFrequency();

                this.getHost().removePart(this.getSide(), false);
                final ForgeDirection dir = this.getHost().addPart(newType, this.getSide(), player);
                final IPart newBus = this.getHost().getPart(dir);

                if (newBus instanceof PartP2PTunnel<?>newTunnel) {
                    newTunnel.setOutput(oldOutput);
                    newTunnel.onTunnelNetworkChange();

                    try {
                        final P2PCache p2p = newTunnel.getProxy().getP2P();
                        p2p.updateFreq(newTunnel, myFreq);
                    } catch (final GridAccessException e) {
                        // :P
                    }
                }

                Platform.notifyBlocksOfNeighbors(
                        this.getTile().getWorldObj(),
                        this.getTile().xCoord,
                        this.getTile().yCoord,
                        this.getTile().zCoord);
                return true;
            }
        }
        // spotless:on

        return false;
    }

}
