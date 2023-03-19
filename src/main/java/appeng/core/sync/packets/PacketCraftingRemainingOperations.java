package appeng.core.sync.packets;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import appeng.api.networking.IGridHost;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftingStatus;
import appeng.container.implementations.CraftingCPUStatus;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.core.sync.network.NetworkHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketCraftingRemainingOperations extends AppEngPacket {

    private int remainingOperations;

    public PacketCraftingRemainingOperations(final ByteBuf stream) throws IOException {
        this.remainingOperations = stream.readInt();
    }

    public PacketCraftingRemainingOperations(int remainingOperations) throws IOException {
        this.remainingOperations = remainingOperations;
        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(remainingOperations);
        this.configureWrite(data);
    }

    public PacketCraftingRemainingOperations() throws IOException {
        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        data.writeInt(0);
        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerCraftingStatus) {
            final ContainerCraftingStatus cpv = (ContainerCraftingStatus) player.openContainer;
            final Object target = cpv.getTarget();
            if (!(target instanceof IGridHost)) {
                return;
            }
            final ContainerOpenContext context = cpv.getOpenContext();
            if (context == null) {
                return;
            }
            final CraftingCPUStatus selectedCpu = cpv.getCPUTable().getSelectedCPU();
            if (selectedCpu == null) {
                return;
            }
            final ICraftingCPU cpu = selectedCpu.getServerCluster();
            if (cpu instanceof CraftingCPUCluster) {
                try {
                    NetworkHandler.instance.sendTo(
                            new PacketCraftingRemainingOperations(((CraftingCPUCluster) cpu).getRemainingOperations()),
                            (EntityPlayerMP) player);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;

        if (gs instanceof GuiCraftingCPU) {
            ((GuiCraftingCPU) gs).postUpdate(this.remainingOperations);
        }
    }
}
