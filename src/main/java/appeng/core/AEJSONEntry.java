package appeng.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.Item;

import java.lang.reflect.Type;

public class AEJSONEntry {



    public String item;
    public int meta_data = 0;
    public int min_value = 0;
    public int max_value = 1;
    public int weight = 1;
    public int exclusiveGroupID = -1;

    public AEJSONEntry(String item, int meta_data, int min_value, int max_value, int weight, int exclusiveGroupID)
    {
        this.item = item;
        this.meta_data = meta_data;
        this.min_value = min_value;
        this.max_value = max_value;
        this.weight = weight;
        this.exclusiveGroupID = exclusiveGroupID;
    }
}
