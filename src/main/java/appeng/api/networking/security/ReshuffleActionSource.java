
package appeng.api.networking.security;

import net.minecraft.entity.player.EntityPlayer;

public class ReshuffleActionSource extends MachineSource {

    public final EntityPlayer player;

    public ReshuffleActionSource(final EntityPlayer player, final IActionHost via) {
        super(via);
        this.player = player;
    }

    @Override
    public String toString() {
        return "ReshuffleActionSource[player=" + (player != null ? player.getCommandSenderName() : "null")
                + ", via="
                + (via != null ? via.getClass().getSimpleName() : "null")
                + "]";
    }
}
