package appeng.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizons.postea.api.ItemStackReplacementManager;
import com.gtnewhorizons.postea.api.TileEntityReplacementManager;
import com.gtnewhorizons.postea.utility.BlockInfo;

import appeng.api.AEApi;
import appeng.api.config.RedstoneMode;
import appeng.api.storage.data.AEStackTypeRegistry;
import appeng.api.storage.data.IAEStackType;

public final class ThEConvertor {

    private final static String thePart = "thaumicenergistics:part.base",
            wireless = "thaumicenergistics:wireless.essentia.terminal",
            cellBench = "thaumicenergistics:thaumicenergistics.block.essentia.cell.workbench";

    private final static int emitter = 1, terminal = 4, mon = 7, conMon = 8;
    private static int thePartID;

    private static final String NBT_KEY_ASPECT_FILTER = "aspect", NBT_KEY_REDSTONE_MODE = "mode",
            NBT_KEY_WANTED_AMOUNT = "wantedAmount", NBT_KEY_LOCKED = "Locked", NBT_KEY_TRACKED_ASPECT = "TrackedAspect",
            NBT_KEY_OWNER = "Owner", OLD_NBT_KEY_CELL = "EssentiaCell";

    public static void postLoad() {
        ItemStackReplacementManager.registerIDResolver(thePart, i -> thePartID = i);

        TileEntityReplacementManager.tileEntityTransformer("BlockCableBus", (tag, world, chunk) -> {
            for (int x = 0; x < 7; x++) {
                final ForgeDirection side = ForgeDirection.getOrientation(x);
                final NBTTagCompound def = tag.getCompoundTag("def:" + side.ordinal());
                final NBTTagCompound extra = tag.getCompoundTag("extra:" + side.ordinal());
                if (!def.hasNoTags() && !extra.hasNoTags()) {
                    if (!extra.hasKey(NBT_KEY_OWNER)) continue;

                    final int currentPartID = def.getInteger("id");
                    final int currentPartDamage = def.getInteger("Damage");

                    if (currentPartID != thePartID) continue;

                    if (currentPartDamage == emitter) {
                        extra.setString(
                                "REDSTONE_EMITTER",
                                extra.getInteger(NBT_KEY_REDSTONE_MODE) == 1 ? RedstoneMode.LOW_SIGNAL.name()
                                        : RedstoneMode.HIGH_SIGNAL.name());

                        if (extra.hasKey(NBT_KEY_ASPECT_FILTER)) {
                            final NBTTagCompound config = extra.getCompoundTag("config");
                            final NBTTagCompound aspectNbt = new NBTTagCompound();

                            aspectNbt.setString("AspectTag", extra.getString(NBT_KEY_ASPECT_FILTER));
                            aspectNbt.setString("StackType", "essentia");
                            config.setTag("#0", aspectNbt);
                            extra.setTag("config", config);
                        }

                        if (!extra.hasKey(AEStackTypeFilter.NBT_FILTERS)) {
                            final AEStackTypeFilter typeFilters = new AEStackTypeFilter();
                            final IAEStackType<?> type = AEStackTypeRegistry.getType("essentia");
                            typeFilters.setOnlyEnabled(type);
                            typeFilters.writeToNBT(extra);
                        }

                        if (extra.hasKey(NBT_KEY_WANTED_AMOUNT))
                            extra.setLong("reportingValue", extra.getLong(NBT_KEY_WANTED_AMOUNT));

                    } else if (currentPartDamage == mon || currentPartDamage == conMon) {
                        if (extra.hasKey(NBT_KEY_TRACKED_ASPECT)) {
                            final NBTTagCompound aspectNbt = new NBTTagCompound();

                            aspectNbt.setString("AspectTag", extra.getString(NBT_KEY_TRACKED_ASPECT));
                            aspectNbt.setString("StackType", "essentia");
                            extra.setTag("configuredItem", aspectNbt);
                        }

                        if (extra.hasKey(NBT_KEY_LOCKED)) {
                            extra.setBoolean("isLocked", extra.getBoolean(NBT_KEY_LOCKED));
                        }
                    }
                }
            }

            return null;
        });

        TileEntityReplacementManager.tileEntityTransformer(
                "thaumicenergistics.TileEssentiaCellWorkbench",
                (tag, world, chunk) -> new BlockInfo(
                        AEApi.instance().definitions().blocks().cellWorkbench().maybeBlock().get(),
                        0,
                        NbtTag -> {
                            if (NbtTag.hasKey(OLD_NBT_KEY_CELL)) {
                                final NBTTagCompound cellInventoryTag = new NBTTagCompound();
                                final NBTTagCompound cellTag = NbtTag.getCompoundTag(OLD_NBT_KEY_CELL);
                                cellInventoryTag.setTag("#0", cellTag);
                                NbtTag.setTag("cell", cellInventoryTag);
                            }

                            NbtTag.setString("id", "BlockCellWorkbench");
                            NbtTag.setString("orientation_up", "UP");
                            NbtTag.setString("orientation_forward", "NORTH");

                            return NbtTag;
                        }));

        ItemStackReplacementManager.addSimpleReplacement(
                cellBench,
                AEApi.instance().definitions().blocks().cellWorkbench().maybeStack(1).get(),
                true);

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
}
