package appeng.integration.modules;

import appeng.api.AEApi;
import appeng.api.definitions.IBlocks;
import appeng.helpers.Reflected;
import appeng.integration.IIntegrationModule;
import cpw.mods.fml.common.event.FMLInterModComms;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

public class Chisel implements IIntegrationModule {

    @Reflected
    public static Chisel instance;
    @Reflected
    public Chisel(){}

    private static final String chiselModID = "chisel";
    private static final String addVariation = "variation:add";
    private static final String groupOre = "group:ore";

    static void registerBlock(final Block block, final int meta, final String blockGroupName) {
        FMLInterModComms.sendMessage(chiselModID, addVariation, String.join("|",
                blockGroupName,
                GameRegistry.findUniqueIdentifierFor(block).toString(),
                Integer.toString(meta)));
    }
    static void registerOre(final String groupOreName, final String blockGroupName) {
        FMLInterModComms.sendMessage(chiselModID, groupOre, String.join("|",
                blockGroupName,
                groupOreName));
    }

    @Override
    public void init() {
        final IBlocks blocks = AEApi.instance().definitions().blocks();

        if (blocks.quartz().maybeBlock().isPresent())
            registerBlock(blocks.quartz().maybeBlock().get(), 0, "AECertusQuartz");
        if (blocks.quartzPillar().maybeBlock().isPresent())
            registerBlock(blocks.quartzPillar().maybeBlock().get(), 0, "AECertusQuartz");
        if (blocks.quartzChiseled().maybeBlock().isPresent())
            registerBlock(blocks.quartzChiseled().maybeBlock().get(), 0, "AECertusQuartz");
        registerOre("AECertusQuartz", "AECertusQuartz");

        if (blocks.skyStone().maybeBlock().isPresent()) {
            registerBlock(blocks.skyStone().maybeBlock().get(), 1, "AESkyStone");
            registerBlock(blocks.skyStone().maybeBlock().get(), 2, "AESkyStone");
            registerBlock(blocks.skyStone().maybeBlock().get(), 3, "AESkyStone");
            registerOre("AESkyStone", "AESkyStone");
        }
    }

    @Override
    public void postInit() {

    }
}
