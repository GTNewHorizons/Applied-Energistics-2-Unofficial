package appeng.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import org.apache.commons.io.FileUtils;
import org.lwjgl.Sys;

import java.io.File;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AEJSONConfig{
    public static Map<String, List<AEJSONEntry>> dimension_loot_tables = new HashMap<>();

    //Required
    private static final String JSON_ITEM = "item";
    //Optional
    private static final String JSON_DIMENSIONID = "dimensionID";
    private static final String JSON_META_DATA = "meta_data";
    private static final String JSON_MIN_VALUE = "min_value";
    private static final String JSON_MAX_VALUE = "max_value";
    private static final String JSON_WEIGHT = "weight";
    private static final String JSON_EXCLUSIVE_GROUP_ID = "exclusive_group_ID";
    private static final Gson GSON = new GsonBuilder().registerTypeHierarchyAdapter(AEJSONEntry.class, new JsonDeserializer<AEJSONEntry>() {
        @Override
        public AEJSONEntry deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {

            final JsonObject object = json.getAsJsonObject();

            if(!object.has(JSON_ITEM))
            {
                throw new JsonParseException("AE2: Field not found: " + JSON_ITEM + " | Error: Missing Required Field");
            }

            final String item = object.get(JSON_ITEM).getAsString();

            final int dimensionID = object.has(JSON_DIMENSIONID) ? object.get(JSON_DIMENSIONID).getAsInt() : 0;
            final int meta_data = object.has(JSON_META_DATA) ? object.get(JSON_META_DATA).getAsInt() : 0;
            final int min_value = object.has(JSON_MIN_VALUE) ? object.get(JSON_MIN_VALUE).getAsInt() : 0;
            final int max_value = object.has(JSON_MAX_VALUE) ? object.get(JSON_MAX_VALUE).getAsInt() : 1;
            final int weight = object.has(JSON_WEIGHT) ? object.get(JSON_WEIGHT).getAsInt() : 1;
            final int exclusiveGroupID = object.has(JSON_EXCLUSIVE_GROUP_ID) ? object.get(JSON_EXCLUSIVE_GROUP_ID).getAsInt() : -1;

            return new AEJSONEntry(item, meta_data, min_value, max_value, weight, exclusiveGroupID);
        }
    }).setPrettyPrinting().create();
    public void toFile(File file) {
        try {
            FileUtils.writeStringToFile(file, GSON.toJson(this), Charset.defaultCharset());
        }
        catch (Exception e) {
            System.err.println("AE2: Could not write json config " + file.getAbsolutePath() + " | Error: Could not create JSON");
        }
    }

    public static AEJSONConfig fromFile(File file)
    {
        if (!file.exists()) {
            AEJSONConfig defaultConfig = createDefaultConfig();
            defaultConfig.toFile(file);
            return defaultConfig;
        }
        try {
            return GSON.fromJson(FileUtils.readFileToString(file, Charset.defaultCharset()), AEJSONConfig.class);
        }
        catch (Exception e) {
            System.err.println("AE2: Could not read json config " + file.getAbsolutePath() + " | Error: Could not pull JSON from file");
        }
        return new AEJSONConfig();
    }

    public static AEJSONConfig createDefaultConfig() {
        AEJSONConfig config = new AEJSONConfig();
        AEJSONEntry calcProcessorPress;
        AEJSONEntry engProcessorPress ;
        AEJSONEntry logicProcessorPress;
        AEJSONEntry siliconPress;
        AEJSONEntry goldNugget = new AEJSONEntry("minecraft:gold_nugget", 0, 0, 12, 1, 1);

        config.dimension_loot_tables.put("0", Arrays.asList(goldNugget));
        return config;
    }
    public static List<AEJSONEntry> getEntriesFromDimension(String dimensionID)
    {
        if(DimensionManager.isDimensionRegistered(Integer.parseInt(dimensionID))) {
            return dimension_loot_tables.get(dimensionID);
        }
        else {
            System.err.println("AE2: Failure While Getting Loot Tables for Dimension: " + dimensionID + " | Error: Dimension is not registered");
            return dimension_loot_tables.get("0");
        }
    }
}
