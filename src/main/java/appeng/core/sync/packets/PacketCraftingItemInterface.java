package appeng.core.sync.packets;

import java.io.IOException;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.AEApi;
import appeng.api.networking.IGridHost;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.util.DimensionalCoord;
import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerCraftingStatus;
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

    private IAEItemStack is;

    public PacketCraftingItemInterface(final ByteBuf stream) throws IOException {
        this.is = AEApi.instance().storage().readItemFromPacket(stream);
    }

    public PacketCraftingItemInterface(IAEItemStack is) throws IOException {
        this.is = is;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        is.writeToPacket(data);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(INetworkInfo manager, AppEngPacket packet, EntityPlayer player) {
        if (player.openContainer instanceof ContainerCraftingStatus cpv) {
            final Object target = cpv.getTarget();
            if (target instanceof IGridHost) {
                final ContainerOpenContext context = cpv.getOpenContext();
                if (context != null) {
                    ICraftingCPU cpu = cpv.getCPUTable().getSelectedCPU().getServerCluster();
                    if (cpu instanceof CraftingCPUCluster) {
                        ItemStack itemStack = is.getItemStack();
                        NBTTagCompound data = Platform.openNbtData(itemStack);
                        DimensionalCoord.writeListToNBT(data, ((CraftingCPUCluster) cpu).getProviders(is));
                        try {
                            NetworkHandler.instance.sendTo(
                                    new PacketCraftingItemInterface(
                                            AEApi.instance().storage().createItemStack(itemStack)),
                                    (EntityPlayerMP) player);
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

        if (gs instanceof GuiCraftingCPU) {
            ((GuiCraftingCPU) gs).postUpdate(this.is);
        }
    }
}
