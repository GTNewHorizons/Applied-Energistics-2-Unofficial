package appeng.util;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.postea.api.ItemStackReplacementManager;
import com.gtnewhorizons.postea.api.TileEntityReplacementManager;
import com.gtnewhorizons.postea.utility.BlockInfo;

import appeng.api.AEApi;
import appeng.api.config.RedstoneMode;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.tile.inventory.IAEStackInventory;

public class ThEConvertor implements Runnable {

    private final static String thePart = "thaumicenergistics:part.base";
    private final static String wireless = "thaumicenergistics:wireless.essentia.terminal";
    private final static int emitter = 1;
    private final static int terminal = 4;
    private final static int mon = 7;
    private final static int conMon = 8;

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
                            final ItemStack lEmitter = AEApi.instance().definitions().parts().levelEmitter()
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
                                    if (extra.hasKey(NBT_KEY_REDSTONE_MODE)) {
                                        extra.setString(
                                                "REDSTONE_EMITTER",
                                                extra.getInteger(NBT_KEY_REDSTONE_MODE) == 1
                                                        ? RedstoneMode.LOW_SIGNAL.name()
                                                        : RedstoneMode.HIGH_SIGNAL.name());

                                        if (extra.hasKey(NBT_KEY_ASPECT_FILTER)) {
                                            final IAEStackInventory config = new IAEStackInventory(null, 1);
                                            final NBTTagCompound aspectNbt = new NBTTagCompound();

                                            aspectNbt.setString("AspectTag", extra.getString(NBT_KEY_ASPECT_FILTER));

                                            final IAEStackType<?> type = AEStackTypeRegistry.getType("essentia");
                                            final IAEStack<?> eas = type.loadStackFromNBT(aspectNbt);

                                            config.putAEStackInSlot(0, eas);
                                            config.writeToNBT(extra, "config");

                                        }

                                        if (!extra.hasKey(AEStackTypeFilter.NBT_FILTERS)) {
                                            final AEStackTypeFilter typeFilters = new AEStackTypeFilter();
                                            final IAEStackType<?> type = AEStackTypeRegistry.getType("essentia");
                                            typeFilters.setOnlyEnabled(type);
                                            typeFilters.writeToNBT(extra);
                                        }

                                        if (extra.hasKey(NBT_KEY_WANTED_AMOUNT))
                                            extra.setLong("reportingValue", extra.getLong(NBT_KEY_WANTED_AMOUNT));

                                    } else if (Platform.isSameItem(is, mon) || Platform.isSameItem(is, conMon))
                                        this.monitorConvert(extra);
                                }
                            }
                            return tag;
                        }));

        ItemStackReplacementManager.addSimpleReplacement(
                thePart,
                terminal,
                AEApi.instance().definitions().parts().terminal().maybeStack(1).get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                thePart,
                emitter,
                AEApi.instance().definitions().parts().levelEmitter().maybeStack(1).get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                thePart,
                mon,
                AEApi.instance().definitions().parts().storageMonitor().maybeStack(1).get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                thePart,
                conMon,
                AEApi.instance().definitions().parts().conversionMonitor().maybeStack(1).get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                wireless,
                AEApi.instance().definitions().items().wirelessTerminal().maybeStack(1).get(),
                true);
    }

    private void monitorConvert(final NBTTagCompound extra) {
        if (extra.hasKey(NBT_KEY_TRACKED_ASPECT)) {
            final NBTTagCompound aspectNbt = new NBTTagCompound();

            aspectNbt.setString("AspectTag", extra.getString(NBT_KEY_TRACKED_ASPECT));

            final IAEStackType<?> type = AEStackTypeRegistry.getType("essentia");
            final IAEStack<?> eas = type.loadStackFromNBT(aspectNbt);

            extra.setTag("configuredItem", eas.toNBTGeneric());
        }
        if (extra.hasKey(NBT_KEY_LOCKED)) {
            extra.setBoolean("isLocked", extra.getBoolean(NBT_KEY_LOCKED));
        }
    }
}
