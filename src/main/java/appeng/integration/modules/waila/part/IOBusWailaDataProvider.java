package appeng.integration.modules.waila.part;

import java.text.NumberFormat;
import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import appeng.api.parts.IPart;
import appeng.core.localization.WailaText;
import appeng.parts.automation.PartSharedItemBus;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;

public class IOBusWailaDataProvider extends BasePartWailaDataProvider {

    @Override
    public List<String> getWailaBody(IPart part, List<String> currentToolTip, IWailaDataAccessor accessor,
            IWailaConfigHandler config) {
        if (accessor.getNBTData().hasKey("BusSpeed")) {
            int amount = accessor.getNBTData().getInteger("BusSpeed");
            String unit = accessor.getNBTData().getString("Unit");
            currentToolTip.add(
                    StatCollector.translateToLocalFormatted(
                            WailaText.IOBusSpeed.getLocal(
                                    EnumChatFormatting.GRAY,
                                    NumberFormat.getInstance().format(amount),
                                    unit)));
        }
        return currentToolTip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, IPart part, TileEntity te, NBTTagCompound tag, World world,
            int x, int y, int z) {
        if (part instanceof PartSharedItemBus<?>bus) {
            tag.setInteger("BusSpeed", bus.calculateAmountToSend() / 5);
            tag.setString("Unit", bus.getStackType().getDisplayUnit());
        }
        return tag;
    }
}
