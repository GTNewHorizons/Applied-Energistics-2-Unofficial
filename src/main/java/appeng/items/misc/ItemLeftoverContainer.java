package appeng.items.misc;

import java.util.EnumSet;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;

import appeng.api.storage.data.IAEStack;
import appeng.core.features.AEFeature;
import appeng.core.localization.GuiText;
import appeng.items.AEBaseItem;

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

        final IAEStack<?> aes = toAEStack(stack);
        if (aes != null) lines.add(aes.getDisplayName() + ": " + aes.getStackSize());
    }

    @Nullable
    public static IAEStack<?> toAEStack(@NotNull final ItemStack is) {
        if (is.getItem() instanceof ItemLeftoverContainer) {
            return IAEStack.fromNBTGeneric(ItemStackNBT.get(is));
        }
        return null;
    }
}
