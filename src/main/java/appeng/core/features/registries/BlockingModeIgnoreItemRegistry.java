package appeng.core.features.registries;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.gtnewhorizon.gtnhlib.util.map.ItemStackMap;

import appeng.api.AEApi;
import appeng.api.features.IBlockingModeIgnoreItemRegistry;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;

public class BlockingModeIgnoreItemRegistry implements IBlockingModeIgnoreItemRegistry {

    private static BlockingModeIgnoreItemRegistry INSTANCE;

    private final Set<Item> items = new HashSet<>();
    private final ItemStackMap<Boolean> itemStacks = new ItemStackMap<>();

    BlockingModeIgnoreItemRegistry() {
        INSTANCE = this;
    }

    @Override
    public void register(Item item) {
        this.items.add(item);
    }

    @Override
    public void register(ItemStack itemStack) {
        this.itemStacks.put(itemStack, true);
    }

    @Override
    public boolean isIgnored(ItemStack itemStack) {
        return this.items.contains(itemStack.getItem()) || itemStacks.containsKey(itemStack);
    }

    public static BlockingModeIgnoreItemRegistry instance() {
        return INSTANCE;
    }

    public void registerDefault() {
        register(AEApi.instance().definitions().materials().calcProcessorPress().maybeStack(1).orNull());
        register(AEApi.instance().definitions().materials().engProcessorPress().maybeStack(1).orNull());
        register(AEApi.instance().definitions().materials().logicProcessorPress().maybeStack(1).orNull());
        register(AEApi.instance().definitions().materials().namePress().maybeStack(1).orNull());
        register(AEApi.instance().definitions().materials().siliconPress().maybeStack(1).orNull());

        if (Loader.isModLoaded("AWWayofTime")) { // blood magic
            register(GameRegistry.findItem("AWWayofTime", "weakBloodOrb"));
            register(GameRegistry.findItem("AWWayofTime", "apprenticeBloodOrb"));
            register(GameRegistry.findItem("AWWayofTime", "magicianBloodOrb"));
            register(GameRegistry.findItem("AWWayofTime", "masterBloodOrb"));
            register(GameRegistry.findItem("AWWayofTime", "archmageBloodOrb"));
            register(GameRegistry.findItem("AWWayofTime", "transcendentBloodOrb"));

            if (Loader.isModLoaded("Avaritia")) {
                register(GameRegistry.findItem("Avaritia", "Orb_Armok"));
            }

            if (Loader.isModLoaded("ForbiddenMagic")) {
                register(GameRegistry.findItem("ForbiddenMagic", "EldritchOrb"));
            }
        }
    }
}
