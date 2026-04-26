package appeng.items.tools;

import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.implementations.guiobjects.IGuiItem;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.items.AEBaseItem;
import appeng.items.contents.ColorizerObj;
import appeng.util.Platform;

public class ToolColorizer extends AEBaseItem implements IGuiItem {

    public ToolColorizer() {
        this.setFeature(EnumSet.of(AEFeature.Colorizer));
        this.setMaxStackSize(1);
    }

    @Override
    public IGuiItemObject getGuiObject(final ItemStack is, final World world, final EntityPlayer player, final int x,
            final int y, final int z) {
        return new ColorizerObj(is);
    }

    @Override
    public ItemStack onItemRightClick(final ItemStack is, final World world, final EntityPlayer player) {
        if (Platform.isServer()) {
            Platform.openGUI(player, null, ForgeDirection.UNKNOWN, GuiBridge.GUI_COLORIZER);
        }

        return is;
    }
}
