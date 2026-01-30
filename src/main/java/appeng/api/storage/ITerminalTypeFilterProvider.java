package appeng.api.storage;

import net.minecraft.entity.player.EntityPlayer;

import org.jetbrains.annotations.NotNull;

import appeng.api.storage.data.IAEStackType;
import it.unimi.dsi.fastutil.objects.Reference2BooleanMap;

public interface ITerminalTypeFilterProvider {

    @NotNull
    Reference2BooleanMap<IAEStackType<?>> getTypeFilter(EntityPlayer player);

    void saveTypeFilter();
}
