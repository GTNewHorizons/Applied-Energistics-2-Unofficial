package appeng.helpers;

import java.util.ArrayList;

import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;
import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;

public class SuperWirelessKitCommand {

    public enum SuperWirelessKitCommands {
        BIND,
        UNBIND,
        PIN,
        DELETE,
        RENAME_SINGLE,
        RENAME_GROUP,
        RECOLOR
    }

    public enum PinType {
        SINGLE,
        COLOR,
        NETWORK
    }

    public static class SubCommand {

        public DimensionalCoord coord = null;
        public DimensionalCoord networkPos = null;
        public boolean includeConnectors = false;
        public boolean includeHubs = false;
        public AEColor color = null;
        public PinType groupBy = null;

        public void write(ByteBuf buf) {
            if (this.coord != null) {
                buf.writeBoolean(true);
                this.coord.writeToPacket(buf);
            } else buf.writeBoolean(false);

            if (this.networkPos != null) {
                buf.writeBoolean(true);
                this.networkPos.writeToPacket(buf);
            } else buf.writeBoolean(false);

            buf.writeBoolean(this.includeConnectors);
            buf.writeBoolean(this.includeHubs);

            if (this.color != null) {
                buf.writeBoolean(true);
                buf.writeInt(this.color.ordinal());
            } else buf.writeBoolean(false);

            if (this.groupBy != null) {
                buf.writeBoolean(true);
                buf.writeInt(this.groupBy.ordinal());
            } else buf.writeBoolean(false);
        }

        public static SubCommand read(ByteBuf buf) {
            SubCommand cmd = new SubCommand();
            if (buf.readBoolean()) cmd.setCoord(DimensionalCoord.readFromPacket(buf));
            if (buf.readBoolean()) cmd.setNetworkPos(DimensionalCoord.readFromPacket(buf));
            if (buf.readBoolean()) cmd.includeConnectors();
            if (buf.readBoolean()) cmd.includeHubs();
            if (buf.readBoolean()) cmd.setColor(AEColor.values()[buf.readInt()]);
            if (buf.readBoolean()) cmd.setGroupBy(PinType.values()[buf.readInt()]);

            return cmd;
        }

        public void setCoord(DimensionalCoord coord) {
            this.coord = coord;
        }

        public void setNetworkPos(DimensionalCoord networkPos) {
            this.networkPos = networkPos;
        }

        public void includeConnectors() {
            this.includeConnectors = true;
        }

        public void includeHubs() {
            this.includeHubs = true;
        }

        public void setColor(AEColor color) {
            this.color = color;
        }

        public void setGroupBy(PinType groupBy) {
            this.groupBy = groupBy;
        }
    }

    public final SuperWirelessKitCommands command;
    public String name = "";
    public AEColor color = null;
    public DimensionalCoord networkPos = null;
    public boolean pin = false;
    public SubCommand subCommand = null;
    public final ArrayList<SubCommand> toBindRow = new ArrayList<>();
    public final ArrayList<SubCommand> targetRow = new ArrayList<>();

    public SuperWirelessKitCommand(SuperWirelessKitCommands command) {
        this.command = command;
    }

    public void write(ByteBuf buf) {
        buf.writeInt(this.command.ordinal());
        ByteBufUtils.writeUTF8String(buf, this.name);

        if (this.color != null) {
            buf.writeBoolean(true);
            buf.writeInt(this.color.ordinal());
        } else buf.writeBoolean(false);

        if (this.networkPos != null) {
            buf.writeBoolean(true);
            this.networkPos.writeToPacket(buf);
        } else buf.writeBoolean(false);

        buf.writeBoolean(this.pin);

        if (this.subCommand != null) {
            buf.writeBoolean(true);
            this.subCommand.write(buf);
        } else buf.writeBoolean(false);

        buf.writeInt(this.toBindRow.size());
        this.toBindRow.forEach(cmd -> cmd.write(buf));

        buf.writeInt(this.targetRow.size());
        this.targetRow.forEach(cmd -> cmd.write(buf));
    }

    public static SuperWirelessKitCommand read(ByteBuf buf) {
        final SuperWirelessKitCommand command = new SuperWirelessKitCommand(
                SuperWirelessKitCommands.values()[buf.readInt()]);
        command.setName(ByteBufUtils.readUTF8String(buf));

        if (buf.readBoolean()) command.setColor(AEColor.values()[buf.readInt()]);
        if (buf.readBoolean()) command.setNetworkPos(DimensionalCoord.readFromPacket(buf));
        final boolean pin = buf.readBoolean();
        if (buf.readBoolean()) {
            final SubCommand subCommand = SubCommand.read(buf);
            if (pin) command.setPinCommand(subCommand);
            else command.setCommand(subCommand);
        }

        final int toBindRowSize = buf.readInt();
        for (int i = 0; i < toBindRowSize; i++) {
            command.toBindRow.add(SubCommand.read(buf));
        }

        final int targetRowSize = buf.readInt();
        for (int i = 0; i < targetRowSize; i++) {
            command.targetRow.add(SubCommand.read(buf));
        }

        return command;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setNetworkPos(DimensionalCoord networkPos) {
        this.networkPos = networkPos;
    }

    public void setColor(AEColor color) {
        this.color = color;
    }

    public void addToBind(SubCommand subCommand) {
        this.toBindRow.add(subCommand);
    }

    public void addTarget(SubCommand subCommand) {
        this.targetRow.add(subCommand);
    }

    public void setCommand(SubCommand SubCommand) {
        this.subCommand = SubCommand;
    }

    public void setPinCommand(SubCommand SubCommand) {
        this.pin = true;
        this.subCommand = SubCommand;
    }
}
