package appeng.core.sync.packets;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.parts.IPartHost;
import appeng.api.parts.SelectedPart;
import appeng.core.CommonHelper;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.network.INetworkInfo;
import appeng.parts.PartPlacement;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PacketPartInteraction extends AppEngPacket {

    private int x;
    private int y;
    private int z;
    private ForgeDirection face;
    private boolean isPart; // either part or facade
    private boolean isWrench;
    private Vec3 hitVec;

    // automatic.
    public PacketPartInteraction(final ByteBuf stream) {
        this.x = stream.readInt();
        this.y = stream.readInt();
        this.z = stream.readInt();
        this.face = ForgeDirection.getOrientation(stream.readByte());
        this.isPart = stream.readBoolean();
        this.isWrench = stream.readBoolean();
        this.hitVec = Vec3.createVectorHelper(stream.readDouble(), stream.readDouble(), stream.readDouble());
    }

    // api
    public PacketPartInteraction(final int x, final int y, final int z, final ForgeDirection face, final boolean isPart,
            final boolean isWrench, Vec3 hitVec) {
        final ByteBuf data = Unpooled.buffer();

        data.writeInt(this.getPacketID());
        data.writeInt(x);
        data.writeInt(y);
        data.writeInt(z);
        data.writeByte(face.ordinal());
        data.writeBoolean(isPart);
        data.writeBoolean(isWrench);
        data.writeDouble(hitVec.xCoord);
        data.writeDouble(hitVec.yCoord);
        data.writeDouble(hitVec.zCoord);

        this.configureWrite(data);
    }

    @Override
    public void serverPacketData(final INetworkInfo manager, final AppEngPacket packet, final EntityPlayer player) {
        CommonHelper.proxy.updateRenderMode(player);
        process(player);
        CommonHelper.proxy.updateRenderMode(null);
    }

    private void process(final EntityPlayer player) {
        World world = player.worldObj;
        TileEntity tile = world.getTileEntity(x, y, z);
        IPartHost host = PartPlacement.getExistingHost(tile);
        if (host == null && !isWrench) {
            host = PartPlacement.getOrCreateHost(tile, player, face.ordinal());
        }
        if (host == null) {
            resync(player);
            return;
        }
        SelectedPart spart;
        if (isPart) {
            spart = new SelectedPart(host.getPart(face), face);
        } else {
            spart = new SelectedPart(host.getFacadeContainer().getFacade(face), face);
        }
        if (isWrench) {
            if (!PartPlacement.wrenchLogic(player, player.worldObj, x, y, z, host, spart)) {
                resync(player);
            }
        } else {
            if (player.isSneaking()) {
                if (!spart.part.onShiftActivate(player, hitVec)) {
                    resync(player);
                }
            } else {
                if (!spart.part.onActivate(player, hitVec)) {
                    resync(player);
                }
            }
        }
    }

    private void resync(final EntityPlayer player) {
        player.worldObj.markBlockForUpdate(x, y, z);
    }
}
