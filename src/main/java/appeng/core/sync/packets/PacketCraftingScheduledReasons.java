package appeng.core.sync.packets;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;

import appeng.client.gui.implementations.GuiCraftingCPU;
import appeng.core.AELog;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.ScheduledReason;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/**
 * Sends a batch of scheduled reasons for active crafting items.
 * Format: Map<itemDefinition, scheduledReasonOrdinal>
 */
public class PacketCraftingScheduledReasons extends AppEngPacket {

    private NBTTagCompound reasons;

    public PacketCraftingScheduledReasons(final ByteBuf stream) throws IOException {
        this.reasons = ByteBufUtils.readTag(stream);
    }

    // Server -> Client
    public PacketCraftingScheduledReasons(final NBTTagCompound reasons) throws IOException {
        super();
        this.reasons = reasons;

        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());
        ByteBufUtils.writeTag(data, this.reasons);

        this.configureWrite(data);
    }

    /**
     * Create a batch update packet from an NBT compound of itemDefinition -> scheduledReasonOrdinal
     */
    public static PacketCraftingScheduledReasons create(NBTTagCompound nbt) throws IOException {
        return new PacketCraftingScheduledReasons(nbt);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void clientPacketData(final INetworkInfo network, final AppEngPacket packet, final EntityPlayer player) {
        final GuiScreen gs = Minecraft.getMinecraft().currentScreen;

        if (gs instanceof GuiCraftingCPU guiCraftingCPU) {
            for (String itemKey : this.reasons.func_150296_c()) {
                int ordinal = this.reasons.getInteger(itemKey);
                String reasonName = ordinal >= 0 && ordinal < ScheduledReason.VALUES.length
                        ? ScheduledReason.VALUES[ordinal].name()
                        : "INVALID_ORDINAL_" + ordinal;
                AELog.info(
                        "Crafting scheduled reason received: itemKey=[%s], scheduledReason=[%s], ordinal=[%s]",
                        itemKey,
                        reasonName,
                        ordinal);
            }
            guiCraftingCPU.postUpdateBatchReasons(this.reasons);
        }
    }
}
