package appeng.items.contents;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.config.Settings;
import appeng.api.config.WirelessToolGroupBy;
import appeng.api.config.YesNo;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.util.ConfigManager;
import appeng.util.Platform;

public class WirelessKitObject implements IGuiItemObject, IConfigurableObject {

    private final ItemStack stack;
    private final World world;

    public WirelessKitObject(final ItemStack stack, World w) {
        this.stack = stack;
        this.world = w;
    }

    @Override
    public ItemStack getItemStack() {
        return this.stack;
    }

    public World getWorld() {
        return this.world;
    }

    @Override
    public IConfigManager getConfigManager() {
        final ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            final NBTTagCompound data = Platform.openNbtData(WirelessKitObject.this.stack);
            manager.writeToNBT(data);
        });

        out.registerSetting(Settings.WIRELESS_TOOL_GROUP_BY, WirelessToolGroupBy.Single);
        out.registerSetting(Settings.WIRELESS_TOOL_HIDE_BOUNDED, YesNo.NO);

        out.readFromNBT((NBTTagCompound) Platform.openNbtData(this.stack).copy());
        return out;
    }
}
