package appeng.core.sync.packets;

import appeng.api.networking.IGridHost;
import appeng.container.ContainerOpenContext;
import appeng.container.implementations.ContainerPatternTerm;
import appeng.container.implementations.ContainerPatternTermEx;
import appeng.container.implementations.ContainerPatternValueAmount;
import appeng.core.sync.AppEngPacket;
import appeng.core.sync.GuiBridge;
import appeng.core.sync.network.INetworkInfo;
import appeng.util.Platform;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

public class PacketPatternValueSet extends AppEngPacket
{

    private final GuiBridge originGui;
    private final int amount;
    private final int valueIndex;

    public PacketPatternValueSet( final ByteBuf stream )
    {
        this.originGui = GuiBridge.values()[stream.readInt()];
        this.amount = stream.readInt();
        this.valueIndex = stream.readInt();
    }

    public PacketPatternValueSet( int originalGui, int amount, int valueIndex )
    {
        this.originGui = GuiBridge.values()[originalGui];
        this.amount = amount;
        this.valueIndex = valueIndex;

        final ByteBuf data = Unpooled.buffer();

        data.writeInt( this.getPacketID() );
        data.writeInt( originalGui );
        data.writeInt( this.amount );
        data.writeInt( this.valueIndex );

        this.configureWrite( data );

    }

    @Override
    public void serverPacketData( INetworkInfo manager, AppEngPacket packet, EntityPlayer player )
    {
        if( player.openContainer instanceof ContainerPatternValueAmount )
        {
            ContainerPatternValueAmount cpv = (ContainerPatternValueAmount) player.openContainer;
            final Object target = cpv.getTarget();
            if( target instanceof IGridHost )
            {
                final ContainerOpenContext context = cpv.getOpenContext();
                if( context != null )
                {
                    final TileEntity te = context.getTile();
                    Platform.openGUI( player, te, cpv.getOpenContext().getSide(), originGui );
                    if( player.openContainer instanceof ContainerPatternTerm || player.openContainer instanceof ContainerPatternTermEx )
                    {
                        Slot slot = player.openContainer.getSlot( valueIndex );
                        if( slot != null && slot.getHasStack() )
                        {
                            ItemStack nextStack = slot.getStack().copy();
                            nextStack.stackSize = amount;
                            slot.putStack(nextStack);
                        }
                    }
                }
            }
        }
    }
}
