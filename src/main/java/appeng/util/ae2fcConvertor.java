package appeng.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.glodblock.github.common.item.ItemFluidLevelEmitter;
import com.glodblock.github.common.item.ItemFluidPacket;
import com.gtnewhorizons.postea.api.ItemStackReplacementManager;
import com.gtnewhorizons.postea.api.TileEntityReplacementManager;
import com.gtnewhorizons.postea.utility.BlockInfo;

import appeng.api.AEApi;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEFluidStackType;

public class ae2fcConvertor implements Runnable {

    public final static String ae2fcPatternEx = "ae2fc:part_fluid_pattern_terminal_ex";
    public final static String ae2fcFluidEmitter = "ae2fc:part_fluid_level_emitter";
    public final static String ae2fcWirelessFluidTerminal = "ae2fc:wireless_fluid_terminal";
    public final static String ae2fcPattern = "ae2fc:part_fluid_pattern_terminal";
    public final static String ae2fcConvMon = "ae2fc:part_fluid_conversion_monitor";
    public final static String ae2fcMon = "ae2fc:part_fluid_storage_monitor";
    public final static String ae2fcTerm = "ae2fc:part_fluid_terminal";

    @Override
    public void run() {
        TileEntityReplacementManager.tileEntityTransformer(
                "BlockCableBus",
                ((tagCompound, world, chunk) -> new BlockInfo(
                        AEApi.instance().definitions().blocks().multiPart().maybeBlock().get(),
                        0,
                        tag -> {
                            final ItemStack emitter = AEApi.instance().definitions().parts().levelEmitter()
                                    .maybeStack(1).get();
                            final ItemStack mon = AEApi.instance().definitions().parts().storageMonitor().maybeStack(1)
                                    .get();
                            final ItemStack conMon = AEApi.instance().definitions().parts().conversionMonitor()
                                    .maybeStack(1).get();

                            for (int x = 0; x < 7; x++) {
                                final ForgeDirection side = ForgeDirection.getOrientation(x);
                                final NBTTagCompound def = tag.getCompoundTag("def:" + side.ordinal());
                                final NBTTagCompound extra = tag.getCompoundTag("extra:" + side.ordinal());
                                if (!def.hasNoTags() && !extra.hasNoTags()) {
                                    final ItemStack is = ItemStack.loadItemStackFromNBT(def);
                                    if (is == null) continue;
                                    if (is.getItem() instanceof ItemFluidLevelEmitter) {
                                        final IAEStackInventory config = new IAEStackInventory(null, 1);
                                        config.readFromNBT(extra, "config");
                                        final IAEStack<?> aes = config.getAEStackInSlot(0);

                                        if (aes instanceof IAEItemStack ais && ais.getItem() instanceof ItemFluidPacket)
                                            config.putAEStackInSlot(0, ItemFluidPacket.getFluidAEStack(ais));
                                        config.writeToNBT(extra, "config");

                                        if (!extra.hasKey(AEStackTypeFilter.NBT_FILTERS)) {
                                            final AEStackTypeFilter typeFilters = new AEStackTypeFilter();
                                            typeFilters.setOnlyEnabled(AEFluidStackType.FLUID_STACK_TYPE);
                                            typeFilters.writeToNBT(extra);
                                        }

                                        emitter.writeToNBT(def);
                                    } else if ((Platform.isSameItem(is, mon) || Platform.isSameItem(is, conMon))
                                            && extra.hasKey("configuredItem")) {
                                                final NBTTagCompound myItem = extra.getCompoundTag("configuredItem");
                                                final IAEFluidStack ifs = AEFluidStack.loadFluidStackFromNBT(myItem);
                                                if (ifs == null) continue;
                                                extra.setTag(
                                                        "configuredItem",
                                                        Platform.writeStackNBT(ifs, new NBTTagCompound(), true));
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
