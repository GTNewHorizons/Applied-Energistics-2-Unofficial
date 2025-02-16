package appeng.api.features;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Registry for items that are ignored by blocking mode of interfaces.
 */
public interface IBlockingModeIgnoreItemRegistry {

    /**
     * Registers item to be ignored by blocking mode of interfaces. Matches with any metadata.
     */
    void register(Item item);

    /**
     * Registers item to be ignored by blocking mode of interfaces. Matches with the supplied metadata.
     */
    void register(ItemStack itemStack);

    /**
     * Checks if given item should be ignored by blocking mode.
     */
    boolean isIgnored(ItemStack itemStack);
}
