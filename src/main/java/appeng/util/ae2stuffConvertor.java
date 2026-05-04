package appeng.util;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.postea.api.BlockReplacementManager;
import com.gtnewhorizons.postea.api.ItemStackReplacementManager;
import com.gtnewhorizons.postea.api.TileEntityReplacementManager;
import com.gtnewhorizons.postea.utility.BlockInfo;

import appeng.api.AEApi;
import appeng.tile.misc.TileAdvancedInscriber;

public class ae2stuffConvertor implements Runnable {

    public final static String ae2stuffGrower = "ae2stuff.Grower";
    public final static String ae2stuffInscriber = "ae2stuff.Inscriber";
    public final static String ae2stuffWireless = "ae2stuff.Wireless";

    public final static String ae2stuffGrowerItem = "ae2stuff:Grower";
    public final static String ae2stuffInscriberItem = "ae2stuff:Inscriber";
    public final static String ae2stuffWirelessItem = "ae2stuff:Wireless";
    public final static String ae2stuffWirelessKit = "ae2stuff:WirelessKit";
    public final static String ae2stuffAdvWirelessKit = "ae2stuff:AdvWirelessKit";
    public final static String ae2stuffVisualizer = "ae2stuff:Visualiser";

    @Override
    public void run() {
        this.ignoreMissingMappings();

        // items
        ItemStackReplacementManager.addSimpleReplacement(
                ae2stuffWirelessKit,
                AEApi.instance().definitions().items().toolWirelessKit().maybeItem().get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2stuffAdvWirelessKit,
                AEApi.instance().definitions().items().toolWirelessKit().maybeItem().get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2stuffVisualizer,
                AEApi.instance().definitions().items().toolNetworkVisualiser().maybeItem().get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2stuffGrowerItem,
                AEApi.instance().definitions().blocks().crystalGrowthChamber().maybeItem().get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2stuffInscriberItem,
                AEApi.instance().definitions().blocks().advancedInscriber().maybeItem().get(),
                true);

        final Item wireless = AEApi.instance().definitions().blocks().wirelessConnector().maybeItem().get();
        final Item hub = AEApi.instance().definitions().blocks().wirelessHub().maybeItem().get();
        for (int i = 0; i < 34; i++) {
            if (i > 16) {
                final int newMeta = i - 17;
                ItemStackReplacementManager.addSimpleReplacement(ae2stuffWirelessItem, i, hub, newMeta, true);
            } else {
                ItemStackReplacementManager.addSimpleReplacement(ae2stuffWirelessItem, i, wireless, i, true);
            }
        }

        // tiles
        final Block growerBlock = Block
                .getBlockFromItem(AEApi.instance().definitions().blocks().crystalGrowthChamber().maybeItem().get());
        TileEntityReplacementManager.tileEntityTransformer(
                ae2stuffGrower,
                ((tagCompound, world, chunk) -> new BlockInfo(growerBlock, 0, tag -> {
                    final NBTTagCompound newNbt = new NBTTagCompound();

                    newNbt.setTag("inv", this.writeInv("item", tag.getTagList("Items", Constants.NBT.TAG_COMPOUND)));
                    newNbt.setTag(
                            "upgrades",
                            this.writeInv("#", tag.getTagList("upgrades", Constants.NBT.TAG_COMPOUND)));

                    newNbt.setDouble("internalCurrentPower", tag.getDouble("power"));

                    newNbt.setString("id", "BlockCrystalGrowthChamber");
                    newNbt.setTag("proxy", tag.getCompoundTag("ae_node"));
                    newNbt.setInteger("x", tag.getInteger("x"));
                    newNbt.setInteger("y", tag.getInteger("y"));
                    newNbt.setInteger("z", tag.getInteger("z"));

                    return newNbt;
                })));

        final Block advancedInscriberBlock = AEApi.instance().definitions().blocks().advancedInscriber().maybeBlock()
                .get();
        TileEntityReplacementManager.tileEntityTransformer(
                ae2stuffInscriber,
                ((tagCompound, world, chunk) -> new BlockInfo(advancedInscriberBlock, 0, tag -> {
                    final NBTTagCompound newNbt = new NBTTagCompound();

                    newNbt.setTag(
                            TileAdvancedInscriber.NBT_INV,
                            this.writeInv("#", tag.getTagList("Items", Constants.NBT.TAG_COMPOUND)));
                    newNbt.setTag(
                            TileAdvancedInscriber.NBT_UPGRADES,
                            this.writeInv("#", tag.getTagList("upgrades", Constants.NBT.TAG_COMPOUND)));
                    newNbt.setInteger(TileAdvancedInscriber.NBT_PROGRESS, Math.round(tag.getFloat("progress") * 100));
                    newNbt.setBoolean(
                            TileAdvancedInscriber.NBT_TOP_LOCKED,
                            !tag.hasKey("topLocked") || tag.getBoolean("topLocked"));
                    newNbt.setBoolean(
                            TileAdvancedInscriber.NBT_BOTTOM_LOCKED,
                            !tag.hasKey("bottomLocked") || tag.getBoolean("bottomLocked"));

                    if (tag.hasKey("output", Constants.NBT.TAG_COMPOUND)) {
                        newNbt.setTag(TileAdvancedInscriber.NBT_PENDING_OUTPUT, tag.getCompoundTag("output").copy());
                    }

                    newNbt.setDouble("internalCurrentPower", tag.getDouble("power"));
                    newNbt.setString("id", "BlockAdvancedInscriber");
                    newNbt.setTag("proxy", tag.getCompoundTag("ae_node"));
                    newNbt.setInteger("x", tag.getInteger("x"));
                    newNbt.setInteger("y", tag.getInteger("y"));
                    newNbt.setInteger("z", tag.getInteger("z"));

                    return newNbt;
                })));

        final Block connectorBlock = AEApi.instance().definitions().blocks().wirelessConnector().maybeBlock().get();
        final Block hubBlock = AEApi.instance().definitions().blocks().wirelessHub().maybeBlock().get();
        TileEntityReplacementManager.tileEntityTransformer(ae2stuffWireless, ((tag, world, chunk) -> {
            final NBTTagCompound newNbt = new NBTTagCompound();
            final boolean isHub = tag.getBoolean("isHub");

            final int color = tag.getInteger("Color");
            if (isHub) {
                newNbt.setString("id", "BlockWirelessHub");
            } else {
                newNbt.setString("id", "BlockWirelessConnector");

                if (tag.hasKey("link")) {
                    final NBTTagCompound link = tag.getCompoundTag("link");
                    final NBTTagCompound target = new NBTTagCompound();
                    final NBTTagCompound targetPos = new NBTTagCompound();

                    targetPos.setInteger("dim", world.provider.dimensionId);
                    targetPos.setInteger("x", link.getInteger("x"));
                    targetPos.setInteger("y", link.getInteger("y"));
                    targetPos.setInteger("z", link.getInteger("z"));

                    target.setTag("pos#0", targetPos);
                    newNbt.setTag("connectedTargets", target);
                }
            }

            if (color != 16) newNbt.setShort("Color", (short) color);
            newNbt.setTag("proxy", tag.getCompoundTag("ae_node"));
            newNbt.setInteger("x", tag.getInteger("x"));
            newNbt.setInteger("y", tag.getInteger("y"));
            newNbt.setInteger("z", tag.getInteger("z"));

            return new BlockInfo(isHub ? hubBlock : connectorBlock, 0, nbtTagCompound -> newNbt);
        }));
    }

    private void ignoreMissingMappings() {
        ItemStackReplacementManager.ignoreMissingMapping(ae2stuffGrowerItem);
        ItemStackReplacementManager.ignoreMissingMapping(ae2stuffInscriberItem);
        ItemStackReplacementManager.ignoreMissingMapping(ae2stuffWirelessItem);
        ItemStackReplacementManager.ignoreMissingMapping(ae2stuffWirelessKit);
        ItemStackReplacementManager.ignoreMissingMapping(ae2stuffAdvWirelessKit);
        ItemStackReplacementManager.ignoreMissingMapping(ae2stuffVisualizer);

        BlockReplacementManager.ignoreMissingMapping(ae2stuffGrowerItem);
        BlockReplacementManager.ignoreMissingMapping(ae2stuffInscriberItem);
        BlockReplacementManager.ignoreMissingMapping(ae2stuffWirelessItem);
    }

    private NBTTagCompound writeInv(final String prefix, final NBTTagList list) {
        final NBTTagCompound newTag = new NBTTagCompound();
        for (int i = 0; i < list.tagCount(); i++) newTag.setTag(prefix + i, list.getCompoundTagAt(i));
        return newTag;
    }

}
