package appeng.core.sync.packets;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.networking.IGridHost;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.container.implementations.ContainerCraftingStatus;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.core.sync.network.NetworkHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketCraftingItemInterface extends AppEngPacket {

    private IAEStack<?> is;
    NBTTagCompound nbt;

    public PacketCraftingItemInterface(final ByteBuf stream) throws IOException {
        if (stream.readBoolean()) {
            this.is = Platform.readStackByte(stream);
        }
        if (stream.readBoolean()) {
            this.nbt = ByteBufUtils.readTag(stream);
        }
    }

    // Client -> Server
    public PacketCraftingItemInterface(IAEStack<?> is) throws IOException {
        this.is = is;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeBoolean(true);
        Platform.writeStackByte(is, data);
        data.writeBoolean(false);

        this.configureWrite(data);
    }

    // Server -> Client
    public PacketCraftingItemInterface(NBTTagCompound nbt) throws IOException {
        this.nbt = nbt;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeBoolean(false);
        data.writeBoolean(true);
        ByteBufUtils.writeTag(data, this.nbt);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerCraftingCPU ccpu) {
            final Object target = ccpu.getTarget();
            if (target instanceof IGridHost) {
                final ContainerOpenContext context = ccpu.getOpenContext();
                if (context != null) {
                    ICraftingCPU cpu;
                    if (player.openContainer instanceof ContainerCraftingStatus ccs) {
                        cpu = ccs.getCPUTable().getSelectedCPU().getServerCluster();
                    } else {
                        cpu = ccpu.getMonitor();
                    }

                    if (cpu instanceof CraftingCPUCluster cpuc) {
                        NBTTagCompound data = new NBTTagCompound();
                        NamedDimensionalCoord.writeListToNBTNamed(data, cpuc.getProviders(this.is));
                        data.setInteger("ScheduledReason", cpuc.getScheduledReason(is).ordinal());
                        try {
                            NetworkHandler.instance
                                    .sendTo(new PacketCraftingItemInterface(data), (EntityPlayerMP) player);
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;

        if (gs instanceof GuiCraftingCPU guiCraftingCPU) {
            guiCraftingCPU.postUpdateTooltip(this.nbt);
        }
    }
}
