package appeng.client.gui.widgets;

import net.minecraft.item.ItemStack;

public interface IDropToFillTextField {

    public boolean isOverTextField(final int mousex, final int mousey);

    public void setTextFieldValue(final String displayName, final int mousex, final int mousey, final ItemStack stack);
}
