package appeng.container.implementations;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import org.jetbrains.annotations.NotNull;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;

import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.util.AEColor;
import appeng.container.AEBaseContainer;
import appeng.container.sync.ActionHandler;
import appeng.container.sync.StreamCodecs;
import appeng.container.sync.SyncRegistrar;
import appeng.util.Platform;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class ContainerWirelessNetworkManager extends AEBaseContainer {

    private final ItemStack terminal;
    private final @NotNull ActionHandler<Byte> switchAction;
    private final @NotNull ActionHandler<Byte> removeAction;
    private final @NotNull ActionHandler<NewName> renameAction;

    private final static class NewName {

        public final AEColor color;
        public final String name;

        public NewName(AEColor color, String name) {
            this.color = color;
            this.name = name;
        }

        public NewName(ByteBuf buf) {
            this.color = AEColor.fromOrdinal(buf.readInt());
            this.name = ByteBufUtils.readUTF8String(buf);
        }

        public static void writeToPacket(ByteBuf buf, NewName value) {
            buf.writeInt(value.color.ordinal());
            ByteBufUtils.writeUTF8String(buf, value.name);
        }
    }

    public ContainerWirelessNetworkManager(InventoryPlayer ip, Object anchor) {
        super(ip, anchor);

        this.terminal = ((IGuiItemObject) anchor).getItemStack();

        final SyncRegistrar sync = this.syncRegistrar();

        this.switchAction = sync.actionC2S("switch", StreamCodecs.byteValue()).onServerAction(this::setCurrent);
        this.removeAction = sync.actionC2S("remove", StreamCodecs.byteValue()).onServerAction(this::remove);
        this.renameAction = sync
                .actionC2S("rename", StreamCodecs.of(NewName.class.getName(), NewName::writeToPacket, NewName::new))
                .onServerAction(this::rename);

        if (Platform.isServer()) {
            final NBTTagCompound data = ItemStackNBT.get(this.terminal);
            if (!data.hasKey("encryptionKeys") && data.hasKey("encryptionKey")) {
                final NBTTagCompound keys = new NBTTagCompound();
                final String key = data.getString("encryptionKey");
                final String colorKey = AEColor.values()[0].name();
                final String colorName = AEColor.values()[0].toString();

                keys.setString(colorKey, key);
                keys.setString(colorKey + "Name", colorName);
                data.setTag("encryptionKeys", keys);
            }
        }
    }

    public Int2ObjectOpenHashMap<Pair<Boolean, String>> getKeys() {
        final Int2ObjectOpenHashMap<Pair<Boolean, String>> keysStatus = new Int2ObjectOpenHashMap<>();

        final NBTTagCompound data = ItemStackNBT.get(this.terminal);
        if (data.hasKey("encryptionKeys")) {
            final NBTTagCompound keys = data.getCompoundTag("encryptionKeys");
            for (int i = 0; i < 16; i++) {
                final String key = AEColor.values()[i].name();
                final String name = keys.hasKey(key + "Name") ? keys.getString(key + "Name")
                        : AEColor.values()[i].toString();
                keysStatus.put(i, Pair.of(keys.hasKey(key), name));
            }
        }

        return keysStatus;
    }

    @Override
    protected void portableSourceTick() {}

    public void switch2Action(final byte color) {
        this.switchAction.send(color);
    }

    private void setCurrent(final byte color) {
        final NBTTagCompound data = ItemStackNBT.get(this.terminal);
        final NBTTagCompound keys = data.getCompoundTag("encryptionKeys");
        ItemStackNBT.of(this.terminal).setString("encryptionKey", keys.getString(AEColor.values()[color].name()));
        this.checkItem(this.getTarget());
        this.getInventoryPlayer().player.closeScreen();
    }

    public void removeNetwork(final byte color) {
        this.removeAction.send(color);
        this.remove(color);
    }

    private void remove(final byte color) {
        final NBTTagCompound data = ItemStackNBT.get(this.terminal);
        final NBTTagCompound keys = data.getCompoundTag("encryptionKeys");
        keys.removeTag(AEColor.values()[color].name());
        keys.removeTag(AEColor.values()[color].name() + "Name");
        if (Platform.isServer()) this.checkItem(this.getTarget());
    }

    public void renameNetwork(final AEColor color, final String newName) {
        final NewName n = new NewName(color, newName);
        this.renameAction.send(n);
        this.rename(n);
    }

    private void rename(final NewName newName) {
        final NBTTagCompound data = ItemStackNBT.get(this.terminal);
        final NBTTagCompound keys = data.getCompoundTag("encryptionKeys");
        keys.setString(newName.color.name() + "Name", newName.name);
        if (Platform.isServer()) this.checkItem(this.getTarget());
    }
}
