package appeng.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.postea.api.ItemStackReplacementManager;
import com.gtnewhorizons.postea.api.TileEntityReplacementManager;
import com.gtnewhorizons.postea.utility.BlockInfo;

import appeng.api.AEApi;
import appeng.util.item.AEFluidStackType;

public final class ae2fcConvertor {

    private final static String ae2fcPatternEx = "ae2fc:part_fluid_pattern_terminal_ex",
            ae2fcFluidEmitter = "ae2fc:part_fluid_level_emitter",
            ae2fcWirelessFluidTerminal = "ae2fc:wireless_fluid_terminal",
            ae2fcPattern = "ae2fc:part_fluid_pattern_terminal", ae2fcConvMon = "ae2fc:part_fluid_conversion_monitor",
            ae2fcMon = "ae2fc:part_fluid_storage_monitor", ae2fcTerm = "ae2fc:part_fluid_terminal",
            ae2fcFluidPacket = "ae2fc:fluid_packet";

    private static int ae2fcEmitterID, ae2fcMonID, ae2fcConMonID, ae2fcPacketID;

    public static void postLoad() {
        ItemStackReplacementManager.registerIDResolver(ae2fcFluidEmitter, i -> ae2fcEmitterID = i);
        ItemStackReplacementManager.registerIDResolver(ae2fcMon, i -> ae2fcMonID = i);
        ItemStackReplacementManager.registerIDResolver(ae2fcConvMon, i -> ae2fcConMonID = i);
        ItemStackReplacementManager.registerIDResolver(ae2fcFluidPacket, i -> ae2fcPacketID = i);

        TileEntityReplacementManager.tileEntityTransformer(
                "BlockCableBus",
                ((tagCompound, world, chunk) -> new BlockInfo(
                        AEApi.instance().definitions().blocks().multiPart().maybeBlock().get(),
                        0,
                        tag -> {
                            for (int x = 0; x < 7; x++) {
                                final ForgeDirection side = ForgeDirection.getOrientation(x);
                                final NBTTagCompound def = tag.getCompoundTag("def:" + side.ordinal());
                                final NBTTagCompound extra = tag.getCompoundTag("extra:" + side.ordinal());

                                if (!def.hasNoTags() && !extra.hasNoTags()) {
                                    final int currentPartID = def.getInteger("id");

                                    if (currentPartID == ae2fcEmitterID) {
                                        final NBTTagCompound config = extra.getCompoundTag("config");
                                        final NBTTagCompound item = config.getCompoundTag("#0");
                                        final int itemID = item.getInteger("id");

                                        if (itemID == ae2fcPacketID) {
                                            final NBTTagCompound packetNBT = item.getCompoundTag("tag");
                                            final NBTTagCompound fluidStackNBT = packetNBT.getCompoundTag("FluidStack");
                                            item.setString("FluidName", fluidStackNBT.getString("FluidName"));
                                            item.setString("StackType", AEFluidStackType.FLUID_STACK_TYPE.getId());
                                        }

                                        if (!extra.hasKey(AEStackTypeFilter.NBT_FILTERS)) {
                                            final AEStackTypeFilter typeFilters = new AEStackTypeFilter();
                                            typeFilters.setOnlyEnabled(AEFluidStackType.FLUID_STACK_TYPE);
                                            typeFilters.writeToNBT(extra);
                                        }
                                    } else if ((currentPartID == ae2fcMonID || currentPartID == ae2fcConMonID)
                                            && extra.hasKey("configuredItem")) {
                                                final NBTTagCompound myItem = extra.getCompoundTag("configuredItem");
                                                if (myItem.hasKey("FluidName")) myItem.setString(
                                                        "StackType",
                                                        AEFluidStackType.FLUID_STACK_TYPE.getId());
                                            }
                                }
                            }
                            return tag;
                        })));

        // items
        ItemStackReplacementManager.addSimpleReplacement(
                ae2fcPatternEx,
                AEApi.instance().definitions().parts().patternTerminalEx().maybeStack(1).get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2fcFluidEmitter,
                AEApi.instance().definitions().parts().levelEmitter().maybeStack(1).get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2fcWirelessFluidTerminal,
                AEApi.instance().definitions().items().wirelessTerminal().maybeStack(1).get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2fcPattern,
                AEApi.instance().definitions().parts().patternTerminal().maybeStack(1).get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2fcConvMon,
                AEApi.instance().definitions().parts().conversionMonitor().maybeStack(1).get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2fcMon,
                AEApi.instance().definitions().parts().storageMonitor().maybeStack(1).get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2fcTerm,
                AEApi.instance().definitions().parts().terminal().maybeStack(1).get(),
                true);
    }
}
