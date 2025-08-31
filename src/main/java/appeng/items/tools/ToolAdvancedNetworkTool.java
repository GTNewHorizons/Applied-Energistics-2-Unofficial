package appeng.items.tools;

import java.util.EnumSet;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.core.features.AEFeature;
import appeng.core.sync.GuiBridge;
import appeng.integration.IntegrationType;
import appeng.transformer.annotations.Integration.Interface;
import appeng.transformer.annotations.Integration.InterfaceList;
import appeng.util.Platform;

@InterfaceList(
        value = { @Interface(iface = "cofh.api.item.IToolHammer", iname = IntegrationType.CoFHWrench),
                @Interface(iface = "buildcraft.api.tools.IToolWrench", iname = IntegrationType.BuildCraftCore) })
public class ToolAdvancedNetworkTool extends ToolNetworkTool {

    public ToolAdvancedNetworkTool() {
        super();
        this.setFeature(EnumSet.of(AEFeature.AdvancedNetworkTool));
    }

    @Override
    protected void openToolGui(EntityPlayer p) {
        Platform.openGUI(p, null, ForgeDirection.UNKNOWN, GuiBridge.GUI_ADVANCED_NETWORK_TOOL);
    }

    @Override
    public int getInventorySize() {
        return 5;
    }
}
