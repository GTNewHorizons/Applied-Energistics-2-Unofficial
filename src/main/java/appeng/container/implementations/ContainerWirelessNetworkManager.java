package appeng.container.implementations;

import java.util.Objects;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import com.gtnewhorizon.gtnhlib.item.ItemStackNBT;

import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.util.AEColor;
import appeng.container.AEBaseContainer;
import appeng.container.guisync.IGuiPacketWritable;
import appeng.container.sync.SyncCodecs;
import appeng.container.sync.SyncRegistrar;
import appeng.container.sync.handlers.IntSyncHandler;
import appeng.container.sync.handlers.ObjectSyncHandler;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

public class ContainerWirelessNetworkManager extends AEBaseContainer {

    private final ItemStack terminal;
    public final IntSyncHandler color;
    private final ObjectSyncHandler<NewName> nameSetter;

    private final static class NewName implements IGuiPacketWritable {

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

        @Override
        public void writeToPacket(ByteBuf buf) {
            buf.writeInt(this.color.ordinal());
            ByteBufUtils.writeUTF8String(buf, this.name);
        }

        public NewName copy() {
            return new NewName(this.color, this.name);
        }
    }

    public ContainerWirelessNetworkManager(InventoryPlayer ip, Object anchor) {
        super(ip, anchor);

        this.terminal = ((IGuiItemObject) anchor).getItemStack();

        final SyncRegistrar sync = this.syncRegistrar();
        this.color = sync.intSync("color").onServerChange((oldValue, newValue) -> this.setCurrent(newValue));
        this.nameSetter = sync
                .object(
                        "nameSetter",
                        SyncCodecs.packetWritable(NewName.class, NewName::new, NewName::copy, Objects::equals),
                        new NewName(AEColor.Transparent, ""))
                .onServerChange((oldValue, newValue) -> this.setName(newValue));
    }

    public void setNewName(final AEColor color, final String newName) {
        this.nameSetter.set(new NewName(color, newName));
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
        } else if (data.hasKey("encryptionKey")) {
            final NBTTagCompound keys = new NBTTagCompound();
            final String name = data.getString("encryptionKey");
            keys.setString(AEColor.values()[0].name(), name);
            keysStatus.put(0, Pair.of(true, name));
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

    private void setName(final NewName newName) {
        final NBTTagCompound data = ItemStackNBT.get(this.terminal);
        final NBTTagCompound keys = data.getCompoundTag("encryptionKeys");
        keys.setString(newName.color.name() + "Name", newName.name);
        this.checkItem(this.getTarget());
    }
}
