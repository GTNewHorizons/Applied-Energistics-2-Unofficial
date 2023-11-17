package appeng.api.implementations;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public interface HasServerSideToolLogic {

    boolean serverSideToolLogic(final ItemStack is, final EntityPlayer p, final World w, final int x, final int y,
            final int z, final int side, final float hitX, final float hitY, final float hitZ);

}
