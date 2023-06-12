package appeng.api.storage;

import java.util.List;
import java.util.function.BiPredicate;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import appeng.api.config.TypeFilter;
import appeng.api.storage.data.IAEItemStack;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * API for item display repo.
 */
public interface IItemDisplayRegistry {

    /**
     * Blacklist the specified item from the item terminals from being displayed in item terminals.
     */
    void blacklistItemDisplay(Item item);

    /**
     * Blacklist the specified item class from being displayed in item terminals. Note that all instances of this class
     * are blacklisted.
     */
    void blacklistItemDisplay(Class<? extends Item> item);

    boolean isBlacklisted(Item item);

    boolean isBlacklisted(Class<? extends Item> item);

    /**
     * Adds a filter option. This is primarily for ae2fc's item drops, but can be expanded.
     */
    void addItemFilter(BiPredicate<TypeFilter, IAEItemStack> filter);

    List<BiPredicate<TypeFilter, IAEItemStack>> getItemFilters();

    /**
     * Adds a render hook to be called after the item itself has been rendered for overlays.
     */
    @SideOnly(Side.CLIENT)
    void addPostItemRenderHook(ItemRenderHook hook);

    @SideOnly(Side.CLIENT)
    interface ItemRenderHook {

        /**
         * Render the item overlay. This is called before any normal overlay code is called.
         * 
         * @return whether to skip the regular rendering code.
         */
        boolean renderOverlay(FontRenderer fr, TextureManager tm, ItemStack is, int x, int y);
    }
}
