package appeng.items.misc;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import appeng.api.storage.data.IAEStack;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.items.AEBaseItem;
import appeng.util.Platform;

public class ItemLeftoverContainer extends AEBaseItem {

    public ItemLeftoverContainer() {
        this.setFeature(EnumSet.of(AEFeature.Core));
        this.setMaxStackSize(1);
    }

    @Override
    protected void addCheckedInformation(ItemStack stack, EntityPlayer player, List<String> lines,
            boolean displayMoreInfo) {
        super.addCheckedInformation(stack, player, lines, displayMoreInfo);

        lines.add(GuiText.LeftoverContainerDesc1.getLocal());
        lines.add(GuiText.LeftoverContainerDesc2.getLocal());

        final IAEStack<?> aes = Platform.handleLeftover(stack);
        if (aes != null) lines.add(aes.getDisplayName() + ": " + aes.getStackSize());
    }
}
