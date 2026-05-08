package appeng.util;

import static thaumicenergistics.common.storage.AEEssentiaStackType.ESSENTIA_STACK_TYPE;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.postea.api.ItemStackReplacementManager;
import com.gtnewhorizons.postea.api.TileEntityReplacementManager;
import com.gtnewhorizons.postea.utility.BlockInfo;

import appeng.api.AEApi;
import appeng.api.config.RedstoneMode;
import appeng.tile.inventory.IAEStackInventory;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.api.ThEApi;
import thaumicenergistics.common.storage.AEEssentiaStack;

public class ThEConvertor implements Runnable {

    private final static String thePart = "thaumicenergistics:part.base";
    private final static int terminal = 4;
    private final static int mon = 7;
    private final static int conMon = 4;

    private static final String NBT_KEY_ASPECT_FILTER = "aspect";
    private static final String NBT_KEY_REDSTONE_MODE = "mode";
    private static final String NBT_KEY_WANTED_AMOUNT = "wantedAmount";
    private static final String NBT_KEY_LOCKED = "Locked", NBT_KEY_TRACKED_ASPECT = "TrackedAspect";

    @Override
    public void run() {
        TileEntityReplacementManager.tileEntityTransformer(
                "BlockCableBus",
                (tagCompound, world, chunk) -> new BlockInfo(
                        AEApi.instance().definitions().blocks().multiPart().maybeBlock().get(),
                        0,
                        tag -> {
                            final ItemStack emitter = AEApi.instance().definitions().parts().levelEmitter()
                                    .maybeStack(1).get();
                            final ItemStack mon = AEApi.instance().definitions().parts().storageMonitor().maybeStack(1)
                                    .get();
                            final ItemStack conMon = AEApi.instance().definitions().parts().conversionMonitor()
                                    .maybeStack(1).get();

                            final ItemStack theEmitter = ThEApi.instance().parts().Essentia_LevelEmitter.getStack();
                            final ItemStack theMon = ThEApi.instance().parts().Essentia_StorageMonitor.getStack();
                            final ItemStack theConMon = ThEApi.instance().parts().Essentia_ConversionMonitor.getStack();

                            for (int x = 0; x < 7; x++) {
                                final ForgeDirection side = ForgeDirection.getOrientation(x);
                                final NBTTagCompound def = tag.getCompoundTag("def:" + side.ordinal());
                                final NBTTagCompound extra = tag.getCompoundTag("extra:" + side.ordinal());
                                if (!def.hasNoTags() && !extra.hasNoTags()) {
                                    final ItemStack is = ItemStack.loadItemStackFromNBT(def);

                                    if (is == null) continue;
                                    if (Platform.isSameItem(is, theEmitter)) {
                                        final IAEStackInventory config = new IAEStackInventory(null, 1);

                                        if (extra.hasKey(NBT_KEY_ASPECT_FILTER)) {
                                            config.putAEStackInSlot(
                                                    0,
                                                    new AEEssentiaStack(
                                                            Aspect.aspects
                                                                    .get(extra.getString(NBT_KEY_ASPECT_FILTER))));
                                        } else {
                                            final AEStackTypeFilter typeFilters = new AEStackTypeFilter();
                                            typeFilters.setOnlyEnabled(ESSENTIA_STACK_TYPE);
                                            typeFilters.writeToNBT(extra);
                                        }

                                        if (extra.hasKey(NBT_KEY_REDSTONE_MODE)) {
                                            extra.setString(
                                                    "REDSTONE_EMITTER",
                                                    extra.getInteger(NBT_KEY_REDSTONE_MODE) == 1
                                                            ? RedstoneMode.LOW_SIGNAL.name()
                                                            : RedstoneMode.HIGH_SIGNAL.name());
                                        }

                                        if (extra.hasKey(NBT_KEY_WANTED_AMOUNT))
                                            extra.setLong("reportingValue", extra.getLong(NBT_KEY_WANTED_AMOUNT));

                                        config.writeToNBT(extra, "config");
                                        emitter.writeToNBT(def);
                                    } else if (Platform.isSameItem(is, theMon)) {
                                        this.monitorConvert(extra);
                                        mon.writeToNBT(def);
                                    } else if (Platform.isSameItem(is, theConMon)) {
                                        this.monitorConvert(extra);
                                        conMon.writeToNBT(def);
                                    }
                                }
                            }
                            return tag;
                        }));

        ItemStackReplacementManager.addSimpleReplacement(
                thePart,
                terminal,
                AEApi.instance().definitions().parts().terminal().maybeStack(1).get(),
                true);
    }

    private void monitorConvert(final NBTTagCompound extra) {
        if (extra.hasKey(NBT_KEY_TRACKED_ASPECT)) {
            Aspect trackedAspect = Aspect.getAspect(extra.getString(NBT_KEY_TRACKED_ASPECT));
            extra.setTag("configuredItem", (new AEEssentiaStack(trackedAspect)).toNBTGeneric());
        }
        if (extra.hasKey(NBT_KEY_LOCKED)) {
            extra.setBoolean("isLocked", extra.getBoolean(NBT_KEY_LOCKED));
        }
    }
}
