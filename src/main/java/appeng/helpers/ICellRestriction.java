package appeng.helpers;

import net.minecraft.item.ItemStack;

public interface ICellRestriction {

    String getCellData(ItemStack is);

    void setCellRestriction(ItemStack is, String newData);
}
