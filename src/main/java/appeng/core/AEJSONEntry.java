package appeng.core;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import cpw.mods.fml.common.registry.GameRegistry;

public class AEJSONEntry {

    public String item;
    public int meta_data = 0;
    public int min_value = 0;
    public int max_value = 1;
    public int weight = 1;
    public int exclusiveGroupID = -1;

    /**
     * Primary constructor with optional parameters.
     *
     * @param item             Required. Format: "modid:item" or "ore:OredictName"
     * @param meta_data        Optional, default 0
     * @param min_value        Optional, default 0
     * @param max_value        Optional, default 1
     * @param weight           Optional, default 1
     * @param exclusiveGroupID Optional, default -1
     */
    public AEJSONEntry(String item, Integer meta_data, Integer min_value, Integer max_value, Integer weight,
            Integer exclusiveGroupID) {
        if (item == null) {
            NullPointerException e = new NullPointerException();
            AELog.error(new NullPointerException(), "AE2: itemName is required! | Error: While loading JSON");
            throw e;
        }
        this.item = item;
        if (meta_data != null) this.meta_data = meta_data;
        if (min_value != null) this.min_value = min_value;
        if (max_value != null) this.max_value = max_value;
        if (weight != null) this.weight = weight;
        if (exclusiveGroupID != null) this.exclusiveGroupID = exclusiveGroupID;
    }

    // No-arg constructor for Gson
    public AEJSONEntry() {}

    private ItemStack getItemStack(int amount, Random rand) {
        String[] temp = item.split(":");
        switch (temp.length) {
            case 2: {
                if (GameRegistry.findItem(temp[0], temp[1]) == null) {
                    AELog.error(
                            "AE2: NO SUCH ITEM FOUND IN REGISTRY. CONFIRM YOU ENTERED IT CORRECTLY. USING GLOWSTONE DUST | Error Getting ItemStack for Meteorite Loot from entry: "
                                    + this);
                    return new ItemStack(Items.glowstone_dust);
                }
                return new ItemStack(GameRegistry.findItem(temp[0], temp[1]), amount, meta_data);
            }
            case 1: {
                if (temp[0].startsWith("ore#")) {
                    ArrayList<ItemStack> oreDictLoot = OreDictionary.getOres(temp[0].substring(4));
                    if (!oreDictLoot.isEmpty()) {
                        ItemStack stack = oreDictLoot.get(rand.nextInt(oreDictLoot.size()));
                        if (stack != null) {
                            stack = stack.copy();
                            stack.stackSize = amount;
                            return stack;
                        }
                    }
                    AELog.error(
                            "AE2: NO SUCH ORE DICTIONARY OBJECT FOUND: " + this
                                    + " USING GLOWSTONE DUST | ERROR: Getting ItemStack for Meteorite Loot");
                    return new ItemStack(Items.glowstone_dust);
                }
            }
            default: {
                IllegalArgumentException e = new IllegalArgumentException();
                AELog.error(
                        e,
                        "AE2: INCORRECT ENTRY FORMAT " + this
                                + " MAKE SURE TO USE CORRECT ITEMID FORMAT: modid:item or ore#oreDictName | ERROR: Getting ItemStack for Meteorite Loot");
                throw e;
            }
        }
    }

    public ItemStack getItemStack(Random rand) {
        return getItemStack(
                ((this.max_value - min_value > 0) ? (rand.nextInt(this.max_value + 1 - min_value)) : 0) + min_value,
                rand);
    }

    public String toString() {
        return "Entry for " + item
                + ". Meta data: "
                + meta_data
                + ". Minimum and Maximum Value: "
                + min_value
                + ", "
                + max_value
                + ". Weight and Exclusive Group ID: "
                + weight
                + ", "
                + exclusiveGroupID
                + ".";
    }
}
