package appeng.core.sync.packets;

import java.io.IOException;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

import appeng.api.networking.IGridHost;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.storage.data.IAEStack;
import appeng.api.util.NamedDimensionalCoord;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftingCPU;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.core.sync.network.NetworkHandler;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.util.Platform;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketCraftingItemInterface extends AppEngPacket {

    private IAEStack<?> is;
    private List<NamedDimensionalCoord> interfaceLocations;

    public PacketCraftingItemInterface(final ByteBuf stream) throws IOException {
        if (stream.readBoolean()) {
            this.is = Platform.readStackByte(stream);
        }
        if (stream.readBoolean()) {
            this.interfaceLocations = NamedDimensionalCoord.readAsListFromPacket(stream);
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
    public PacketCraftingItemInterface(final List<NamedDimensionalCoord> interfaceLocations) throws IOException {
        this.interfaceLocations = interfaceLocations;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeBoolean(false);
        data.writeBoolean(true);
        NamedDimensionalCoord.writeListToPacket(data, this.interfaceLocations);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerCraftingCPU ccpu) {
            this.sendInterfaceLocations(player, ccpu.getTarget(), ccpu.getOpenContext(), ccpu.getCpu());
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;

        if (gs instanceof GuiCraftingCPU guiCraftingCPU) {
            guiCraftingCPU.postInterfaceLocationsUpdate(this.interfaceLocations);
        }
    }

    private void sendInterfaceLocations(final EntityPlayer player, final Object target,
            final ContainerOpenContext context, final ICraftingCPU cpu) {
        if (!(target instanceof IGridHost) || context == null || !(cpu instanceof CraftingCPUCluster cpuc)) {
            return;
        }

        try {
            NetworkHandler.instance
                    .sendTo(new PacketCraftingItemInterface(cpuc.getProviders(this.is)), (EntityPlayerMP) player);
        } catch (Exception ignored) {}
    }
}
