package appeng.items.contents;

import net.minecraft.item.ItemStack;

import appeng.api.implementations.guiobjects.IGuiItemObject;

public class ColorizerObj implements IGuiItemObject {

    private final ItemStack is;

    public ColorizerObj(final ItemStack is) {
        this.is = is;
    }

    @Override
    public ItemStack getItemStack() {
        return this.is;
    }
}
