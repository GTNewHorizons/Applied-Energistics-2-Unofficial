package appeng.core;

import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraftforge.common.DimensionManager;

import org.apache.commons.io.FileUtils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

public class AEJSONConfig {

    public static AEJSONConfig instance;
    // spotless:off
    /**
     * Parameters:
     * item:  TYPE: STRING  DEFAULT VALUE:  NONE. PARAMETER IS REQUIRED  DESCRIPTION: The name of the item as used by the /give command or oreDictionaryName such as ore#nuggetCopper  EXAMPLE: "minecraft:dirt"
     * meta_data:  TYPE: NON-NEGATIVE INTEGER  DEFAULT VALUE:  0  DESCRIPTION: Metadata value for the item, the 3rd parameter in the /give command  EXAMPLE: 3
     * min_value:  TYPE: NON-NEGATIVE INTEGER  DEFAULT VALUE:  0  DESCRIPTION: The minimum amount of that item that is inserted when that item is selected (SETTING TO 1 DOES NOT GUARANTEE THAT ITEM IN EVERY CHEST)  EXAMPLE: 4
     * max_value:  TYPE: NON-NEGATIVE INTEGER  DEFAULT VALUE:  1  DESCRIPTION: The maximum amount of that item that is inserted when that item is selected  EXAMPLE: 72
     * weight:  TYPE: NON-NEGATIVE INTEGER  DEFAULT VALUE:  1  DESCRIPTION:  The weighted chance the item is chosen. (Weights are compared between entries with the same exclusive group)  EXAMPLE: 40
     * exclusiveGroupID:  TYPE: INTEGER  DEFAULT VALUE:  -1  DESCRIPTION:  exclusiveGroupID lets you group entries so only one from the group can appear, chosen based on weight. If multiple entries share the same group ID, only one will be picked. If an entry is the only one in its group, it's guaranteed to appear. Setting this parameter to -1 will cause it to ignore exclusivity  EXAMPLE: 154
     *
     * Notes:
     * The Key Value:  For the dimension_loot_tables a key value is required, this value represents the dimensionID for the loot tables. For example, -29 is Galacticraft Mars, the loot tables under that id will only spawn in that dimension (if meteorites spawn there)
     * The list of loot tables:  The chance of a loot table within a dimension being selected is weighted by the total weight of every entry in that loot table against every other loot tables' total weights
     * Format:  {
     *   "dimension_loot_tables": {
     *     "0": [   This is the start of a list of loot tables
     *       [    this is the start of a loot table
     *         {
     *             ENTRY
     *         },
     *         {
     *             ANOTHER ENTRY
     *         }
     *       ],   this is the end of a loot table
     *       [    this is the start of another separate loot table
     *         {
     *             ENTRY
     *         },
     *         {
     *             ANOTHER ENTRY
     *         }
     *       ]    this is the end of a loot table
     *    ], this is the end of the list of loot tables
     *    "-29": [   this is the start of a list of loot tables for another dimension, and so on
     *       [
     *         {
     * }
     */
    //spotless:on
    @SerializedName("Info and Tips:")
    private final String[] notes = { "Parameters:",
            "item:  TYPE: STRING  DEFAULT VALUE:  NONE. PARAMETER IS REQUIRED  DESCRIPTION: The name of the item as used by the /give command  EXAMPLE: \"minecraft:dirt\"",
            "meta_data:  TYPE: NON-NEGATIVE INTEGER  DEFAULT VALUE:  0  DESCRIPTION: Metadata value for the item, the 3rd parameter in the /give command  EXAMPLE: 3",
            "min_value:  TYPE: NON-NEGATIVE INTEGER  DEFAULT VALUE:  0  DESCRIPTION: The minimum amount of that item that is inserted when that item is selected (SETTING TO 1 DOES NOT GUARANTEE THAT ITEM IN EVERY CHEST)  EXAMPLE: 4",
            "max_value:  TYPE: NON-NEGATIVE INTEGER  DEFAULT VALUE:  1  DESCRIPTION: The maximum amount of that item that is inserted when that item is selected  EXAMPLE: 72",
            "weight:  TYPE: NON-NEGATIVE INTEGER  DEFAULT VALUE:  1  DESCRIPTION:  The weighted chance the item is chosen. (Weights are compared between entries with the same exclusive group)  EXAMPLE: 40",
            "exclusiveGroupID:  TYPE: INTEGER  DEFAULT VALUE:  -1  DESCRIPTION:  exclusiveGroupID lets you group entries so only one from the group can appear, chosen based on weight. If multiple entries share the same group ID, only one will be picked. If an entry is the only one in its group, its guaranteed to appear. Setting this parameter to -1 will cause it to ignore exclusivity  EXAMPLE: 154",
            "", "Notes:",
            "The Key Value:  For the dimension_loot_tables a key value is required, this value represents the dimensionID for the loot tables. For example, -29 is Galacticraft Mars, the loot tables under that id will only spawn in that dimension (if meteorites spawn there)",
            "The list of loot tables:  The chance of a loot table within a dimension being selected is weighted by the total weight of every entry in that loot table against every other loot tables total weights",
            "Format:  {", "      \"dimension_loot_tables\": {",
            "   \"0\": [   This is the start of a list of loot tables",
            "       [    this is the start of a loot table,", "         {", "             ENTRY", "         },",
            "         {", "             ANOTHER ENTRY", "         }", "       ],   this is the end of a loot table",
            "       [    this is the start of another separate loot table", "         {", "             ENTRY",
            "         },", "         {", "             ANOTHER ENTRY", "         }",
            "       ]    this is the end of a loot table", "    ], this is the end of the list of loot tables",
            "    \"-29\": [   this is the start of a list of loot tables for another dimension, and so on", "       [",
            "         {", " }" };
    @SerializedName("dimension_loot_tables")
    private Map<String, ArrayList<ArrayList<AEJSONEntry>>> dimensionLootTables = new HashMap<>();

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public AEJSONConfig() {}

    public void toFile(File file) {
        try {
            FileUtils.writeStringToFile(file, GSON.toJson(instance), Charset.defaultCharset());
        } catch (Exception e) {
            AELog.error(
                    e,
                    "AE2: Could not write json config " + file.getAbsolutePath() + " | Error: Could not create JSON");
        }
    }

    public AEJSONConfig fromFile(File file) {
        if (!file.exists()) {
            AEJSONConfig defaultConfig = createDefaultConfig();
            AEJSONConfig.instance = defaultConfig;
            defaultConfig.toFile(file);
            return defaultConfig;
        }
        try {
            AEJSONConfig loaded = GSON
                    .fromJson(FileUtils.readFileToString(file, Charset.defaultCharset()), AEJSONConfig.class);
            AEJSONConfig.instance = loaded;
            return loaded;
        } catch (Exception e) {
            AELog.error(
                    e,
                    "AE2: Could not read json config " + file.getAbsolutePath()
                            + " | Error: Could not pull JSON from file");
            return new AEJSONConfig();
        }
    }

    public static AEJSONConfig createDefaultConfig() {
        AEJSONConfig config = new AEJSONConfig();
        AEJSONEntry calcProcessorPress = new AEJSONEntry(
                "appliedenergistics2:item.ItemMultiMaterial",
                13,
                1,
                null,
                3,
                1);
        AEJSONEntry engProcessorPress = new AEJSONEntry(
                "appliedenergistics2:item.ItemMultiMaterial",
                14,
                1,
                null,
                3,
                1);
        AEJSONEntry logicProcessorPress = new AEJSONEntry(
                "appliedenergistics2:item.ItemMultiMaterial",
                15,
                1,
                null,
                3,
                1);
        AEJSONEntry siliconPress = new AEJSONEntry("appliedenergistics2:item.ItemMultiMaterial", 19, 1, null, 3, 1);

        AEJSONEntry IronNugget = new AEJSONEntry("ore#nuggetIron", null, 1, 12, 3, null);
        AEJSONEntry CopperNugget = new AEJSONEntry("ore#nuggetCopper", null, 1, 12, 3, 3);
        AEJSONEntry TinNugget = new AEJSONEntry("ore#nuggetTin", null, 1, 12, 3, 4);
        AEJSONEntry SilverNugget = new AEJSONEntry("ore#nuggetSilver", null, 1, 12, 3, 5);
        AEJSONEntry LeadNugget = new AEJSONEntry("ore#nuggetLead", null, 1, 12, 3, 2);
        AEJSONEntry PlatinumNugget = new AEJSONEntry("ore#nuggetPlatinum", null, 1, 12, 3, 3);
        AEJSONEntry NickelNugget = new AEJSONEntry("ore#nuggetNickel", null, 1, 12, 3, 4);
        AEJSONEntry AluminiumNugget = new AEJSONEntry("ore#nuggetAluminium", null, 1, 12, 3, 5);
        AEJSONEntry ElectrumNugget = new AEJSONEntry("ore#nuggetElectrum", null, 1, 12, 3, 2);
        AEJSONEntry GoldNugget = new AEJSONEntry("minecraft:gold_nugget", null, 1, 12, 3, null);
        AEJSONEntry Skystone = new AEJSONEntry("appliedenergistics2:tile.BlockSkyStone", null, null, 12, null, null);
        AEJSONEntry Diamond = new AEJSONEntry("minecraft:diamond", null, 2, 4, null, null);
        config.dimensionLootTables.put(
                "0",
                new ArrayList<>(
                        Arrays.asList(
                                new ArrayList<>(
                                        Arrays.asList(
                                                calcProcessorPress,
                                                CopperNugget,
                                                PlatinumNugget,
                                                IronNugget,
                                                GoldNugget,
                                                Skystone)),
                                new ArrayList<>(
                                        Arrays.asList(
                                                engProcessorPress,
                                                TinNugget,
                                                NickelNugget,
                                                IronNugget,
                                                GoldNugget,
                                                Skystone)),
                                new ArrayList<>(
                                        Arrays.asList(
                                                logicProcessorPress,
                                                SilverNugget,
                                                AluminiumNugget,
                                                IronNugget,
                                                GoldNugget,
                                                Skystone)),
                                new ArrayList<>(
                                        Arrays.asList(
                                                siliconPress,
                                                LeadNugget,
                                                ElectrumNugget,
                                                IronNugget,
                                                GoldNugget,
                                                Skystone)))));
        config.dimensionLootTables.put(
                "-29",
                new ArrayList<>(
                        Arrays.asList(
                                new ArrayList<>(
                                        Arrays.asList(
                                                calcProcessorPress,
                                                CopperNugget,
                                                PlatinumNugget,
                                                IronNugget,
                                                GoldNugget,
                                                Skystone,
                                                Diamond)),
                                new ArrayList<>(
                                        Arrays.asList(
                                                engProcessorPress,
                                                TinNugget,
                                                NickelNugget,
                                                IronNugget,
                                                GoldNugget,
                                                Skystone,
                                                Diamond)),
                                new ArrayList<>(
                                        Arrays.asList(
                                                logicProcessorPress,
                                                SilverNugget,
                                                AluminiumNugget,
                                                IronNugget,
                                                GoldNugget,
                                                Skystone,
                                                Diamond)),
                                new ArrayList<>(
                                        Arrays.asList(
                                                siliconPress,
                                                LeadNugget,
                                                ElectrumNugget,
                                                IronNugget,
                                                GoldNugget,
                                                Skystone,
                                                Diamond)))));
        return config;
    }

    private ArrayList<ArrayList<AEJSONEntry>> getTablesForDimension(int dimensionID) {
        if (DimensionManager.isDimensionRegistered(dimensionID)) {
            if (dimensionLootTables.containsKey(dimensionID + "")) {
                return dimensionLootTables.get(dimensionID + "");
            } else if (dimensionLootTables.containsKey("0")) {
                return dimensionLootTables.get("0");
            } else {
                AELog.error(
                        "AE2: Configs for overworld and dimension: " + dimensionID
                                + " are missing! | Error: Getting loot tables for dimension");
                return createDefaultConfig().getTablesForDimension(0);
            }
        } else {
            AELog.error(
                    "AE2: Failure While Getting Loot Tables for Dimension: " + dimensionID
                            + ". Using overworld configs. | Error: Dimension is not registered");
            return dimensionLootTables.get("0");
        }
    }

    public ArrayList<AEJSONEntry> getWeightedLootTable(int dimID, Random rand) {
        ArrayList<ArrayList<AEJSONEntry>> loot_tables = instance.getTablesForDimension(dimID);
        if (loot_tables == null || loot_tables.isEmpty()) {
            AELog.error(
                    "AE2: No loot tables found for dimension, will use default loot table" + dimID
                            + " | Error: Empty or missing loot tables.");
            loot_tables = createDefaultConfig().getTablesForDimension(0);
        }
        int[] totalWeights = new int[loot_tables.size()];
        int totalWeight = 0;
        for (int i = 0; i < loot_tables.size(); i++) {
            for (AEJSONEntry entry : loot_tables.get(i)) {
                totalWeights[i] += entry.weight;
                totalWeight += entry.weight;
            }
        }

        int randomWeight = rand.nextInt(totalWeight);
        int cumulitive = 0;
        for (int i = 0; i < totalWeights.length; i++) {
            cumulitive += totalWeights[i];
            if (randomWeight <= cumulitive) {
                return loot_tables.get(i);
            }
        }
        AELog.error(
                "AE2: Failed to pull a weighted random loot_table for dimension: " + dimID
                        + ", pulling unweighted random loot_table. | Error: Weighted random failed. THIS IS LIKELY A BUG.");
        return loot_tables.get(rand.nextInt(loot_tables.size()));

    }
}
