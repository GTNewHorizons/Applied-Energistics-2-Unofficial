package appeng.container.implementations;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;

import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.util.AEColor;
import appeng.container.AEBaseContainer;
import appeng.container.sync.SyncRegistrar;
import appeng.container.sync.handlers.IntSyncHandler;

public class ContainerWirelessNetworkManager extends AEBaseContainer {

    private final ItemStack terminal;
    public final IntSyncHandler color;

    public ContainerWirelessNetworkManager(InventoryPlayer ip, Object anchor) {
        super(ip, anchor);

        this.terminal = ((IGuiItemObject) anchor).getItemStack();

        final SyncRegistrar sync = this.syncRegistrar();
        this.color = sync.intSync("color").onServerChange((oldValue, newValue) -> this.setCurrent(newValue));
    }

    public boolean[] getKeys() {
        final boolean[] keysStatus = new boolean[16];

        final NBTTagCompound data = ItemStackNBT.get(this.terminal);
        if (data.hasKey("encryptionKeys")) {
            final NBTTagCompound keys = data.getCompoundTag("encryptionKeys");
            for (int i = 0; i < 16; i++) {
                final String key = AEColor.values()[i].name();
                keysStatus[i] = keys.hasKey(key);
            }
        } else if (data.hasKey("encryptionKey")) {
            final NBTTagCompound keys = new NBTTagCompound();
            keys.setString(AEColor.values()[0].name(), data.getString("encryptionKey"));
            keysStatus[0] = true;
        }

        return keysStatus;
    }

    @Override
    public void detectAndSendChanges() {}

    private void setCurrent(final int color) {
        final NBTTagCompound data = ItemStackNBT.get(this.terminal);
        final NBTTagCompound keys = data.getCompoundTag("encryptionKeys");
        if (color >= 100) keys.removeTag(AEColor.values()[color - 100].name());
        else ItemStackNBT.of(this.terminal).setString("encryptionKey", keys.getString(AEColor.values()[color].name()));
        this.checkItem(this.getTarget());
        if (color < 100) Minecraft.getMinecraft().thePlayer.closeScreen();
    }
}
