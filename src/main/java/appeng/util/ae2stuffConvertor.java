package appeng.util;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import com.gtnewhorizons.postea.api.ItemStackReplacementManager;
import com.gtnewhorizons.postea.api.TileEntityReplacementManager;
import com.gtnewhorizons.postea.utility.BlockInfo;

import appeng.api.AEApi;

public class ae2stuffConvertor implements Runnable {

    public final static String ae2stuffGrower = "ae2stuff.Grower";
    public final static String ae2stuffWireless = "ae2stuff.Wireless";

    public final static String ae2stuffGrowerItem = "ae2stuff:Grower";
    public final static String ae2stuffWirelessItem = "ae2stuff:Wireless";
    public final static String ae2stuffWirelessKit = "ae2stuff:WirelessKit";
    public final static String ae2stuffAdvWirelessKit = "ae2stuff:AdvWirelessKit";
    public final static String ae2stuffVisualizer = "ae2stuff:Visualiser";

    @Override
    public void run() {
        // items
        ItemStackReplacementManager.addSimpleReplacement(
                ae2stuffWirelessKit,
                AEApi.instance().definitions().items().toolSimpleWirelessKit().maybeItem().get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2stuffAdvWirelessKit,
                AEApi.instance().definitions().items().toolAdvancedWirelessKit().maybeItem().get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2stuffVisualizer,
                AEApi.instance().definitions().items().toolNetworkVisualiser().maybeItem().get(),
                true);

        ItemStackReplacementManager.addSimpleReplacement(
                ae2stuffGrowerItem,
                AEApi.instance().definitions().blocks().crystalGrowthChamber().maybeItem().get(),
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

        final Block connectorBlock = AEApi.instance().definitions().blocks().wirelessConnector().maybeBlock().get();
        final Block hubBlock = AEApi.instance().definitions().blocks().wirelessHub().maybeBlock().get();
        TileEntityReplacementManager.tileEntityTransformer(ae2stuffWireless, ((tag, world, chunk) -> {
            final NBTTagCompound newNbt = new NBTTagCompound();
            final boolean isHub = tag.getBoolean("isHub");

            final int color;
            if (tag.hasKey("Color")) {
                color = tag.getInteger("Color");
            } else color = -1;

            if (isHub) {
                newNbt.setString("id", "BlockWirelessHub");
            } else {
                newNbt.setString("id", "BlockWirelessConnector");

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

            newNbt.setShort("Color", (short) color);
            newNbt.setTag("proxy", tag.getCompoundTag("ae_node"));
            newNbt.setInteger("x", tag.getInteger("x"));
            newNbt.setInteger("y", tag.getInteger("y"));
            newNbt.setInteger("z", tag.getInteger("z"));

            return new BlockInfo(isHub ? hubBlock : connectorBlock, color + 1, nbtTagCompound -> newNbt);
        }));
    }

    private NBTTagCompound writeInv(final String prefix, final NBTTagList list) {
        final NBTTagCompound newTag = new NBTTagCompound();
        for (int i = 0; i < list.tagCount(); i++) newTag.setTag(prefix + i, list.getCompoundTagAt(i));
        return newTag;
    }
}
