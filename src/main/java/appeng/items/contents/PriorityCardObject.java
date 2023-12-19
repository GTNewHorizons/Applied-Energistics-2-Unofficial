package appeng.items.contents;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import appeng.api.config.PriorityCardMode;
import appeng.api.config.Settings;
import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.container.interfaces.IInventorySlotAware;
import appeng.helpers.IPriorityHost;
import appeng.items.tools.ToolPriorityCard;
import appeng.util.ConfigManager;
import appeng.util.Platform;

public class PriorityCardObject implements IGuiItemObject, IPriorityHost, IInventorySlotAware, IConfigurableObject {

    private final ItemStack stack;
    private final int slot;

    private final IConfigManager configManager;

    public PriorityCardObject(final ItemStack stack, int slot) {
        this.stack = stack;
        this.slot = slot;
        this.configManager = createConfigManager();
    }

    @Override
    public ItemStack getItemStack() {
        return this.stack;
    }

    @Override
    public int getPriority() {
        return ToolPriorityCard.getPriority(stack);
    }

    @Override
    public void setPriority(int newValue) {
        ToolPriorityCard.setPriority(stack, newValue);
    }

    public PriorityCardMode getMode() {
        return (PriorityCardMode) configManager.getSetting(Settings.PRIORITY_CARD_MODE);
    }

    public void setMode(PriorityCardMode newValue) {
        configManager.putSetting(Settings.PRIORITY_CARD_MODE, newValue);
    }

    @Override
    public int getInventorySlot() {
        return slot;
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
    }

    private IConfigManager createConfigManager() {
        final ConfigManager out = new ConfigManager((manager, settingName, newValue) -> {
            final NBTTagCompound data = Platform.openNbtData(this.stack);
            manager.writeToNBT(data);
        });

        out.registerSetting(Settings.PRIORITY_CARD_MODE, PriorityCardMode.EDIT);

        out.readFromNBT((NBTTagCompound) Platform.openNbtData(this.stack).copy());
        return out;
    }
}
