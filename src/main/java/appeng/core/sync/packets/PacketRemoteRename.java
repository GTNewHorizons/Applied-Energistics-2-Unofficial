package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.container.AEBaseContainer;
import appeng.container.PrimaryGui;
import appeng.container.interfaces.IContainerSubGui;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.helpers.ICustomNameObject;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketRemoteRename extends AppEngPacket {

    int x, y, z, dim, side;

    public PacketRemoteRename(final ByteBuf stream) {
        this.x = stream.readInt();
        this.y = stream.readInt();
        this.z = stream.readInt();
        this.dim = stream.readInt();
        this.side = stream.readInt();
    }

    public PacketRemoteRename(int x, int y, int z, int dim, int side) {
        final ByteBuf data = Unpooled.buffer();
        data.writeInt(this.getPacketID());

        data.writeInt(x);
        data.writeInt(y);
        data.writeInt(z);
        data.writeInt(dim);
        data.writeInt(side);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        final Container c = player.openContainer;
        if (c instanceof AEBaseContainer bc && player.dimension == this.dim) {
            TileEntity te = player.worldObj.getTileEntity(this.x, this.y, this.z);

            if (te instanceof ICustomNameObject) {
                Platform.openGUI(
                        player,
                        te,
                        ForgeDirection.getOrientation(this.side),
                        GuiBridge.GUI_RENAMER,
                        bc.getTargetSlotIndex());
            }

            if (player.openContainer instanceof IContainerSubGui sg) {
                final PrimaryGui pGui = bc.createPrimaryGui();
                sg.setPrimaryGui(pGui);
            }
        }
    }
}
