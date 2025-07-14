package appeng.helpers;

import java.util.ArrayList;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import appeng.api.util.AEColor;
import appeng.api.util.DimensionalCoord;

public class SuperWirelessToolDataObject {

    public int network;
    public String customName;
    public DimensionalCoord cord;
    public boolean isConnected;
    public DimensionalCoord targetCord;
    public AEColor color;
    public int channels;
    public boolean isHub;
    public int slots;

    public SuperWirelessToolDataObject(int n, String name, DimensionalCoord cord, boolean isConnected,
            DimensionalCoord targetCord, AEColor color, int channels, boolean isHub, int slots) {
        this.network = n;
        this.customName = name;
        this.cord = cord;
        this.isConnected = isConnected;
        this.targetCord = targetCord;
        this.color = color;
        this.channels = channels;
        this.isHub = isHub;
        this.slots = slots;
    }

    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setInteger("network", this.network);
        nbt.setString("name", this.customName);

        NBTTagCompound cordNbt = new NBTTagCompound();
        this.cord.writeToNBT(cordNbt);
        nbt.setTag("cord", cordNbt);

        nbt.setBoolean("isConnected", this.isConnected);

        if (this.isConnected && !this.isHub) {
            NBTTagCompound otherCordNbt = new NBTTagCompound();
            this.targetCord.writeToNBT(otherCordNbt);
            nbt.setTag("targetCord", otherCordNbt);
        }

        nbt.setInteger("color", this.color.ordinal());
        nbt.setInteger("channels", this.channels);
        nbt.setBoolean("isHub", this.isHub);
        nbt.setInteger("slots", this.slots);
    }

    public static SuperWirelessToolDataObject readFromNBT(NBTTagCompound nbt) {
        boolean isConnected = nbt.getBoolean("isConnected");
        return new SuperWirelessToolDataObject(
                nbt.getInteger("network"),
                nbt.getString("name"),
                DimensionalCoord.readFromNBT(nbt.getCompoundTag("cord")),
                isConnected,
                isConnected ? DimensionalCoord.readFromNBT(nbt.getCompoundTag("targetCord")) : null,
                AEColor.values()[nbt.getInteger("color")],
                nbt.getInteger("channels"),
                nbt.getBoolean("isHub"),
                nbt.getInteger("slots"));
    }

    public static void writeToNBTasList(ArrayList<SuperWirelessToolDataObject> list, NBTTagCompound nbt) {
        final NBTTagList dataList = new NBTTagList();

        for (final SuperWirelessToolDataObject d : list) {
            final NBTTagCompound dn = new NBTTagCompound();
            d.writeToNBT(dn);
            dataList.appendTag(dn);
        }

        nbt.setTag("SWTDO_List", dataList);
    }

    public static ArrayList<SuperWirelessToolDataObject> readFromNBTasList(NBTTagCompound nbt) {
        final NBTTagList List = nbt.getTagList("SWTDO_List", 10);
        ArrayList<SuperWirelessToolDataObject> arrayList = new ArrayList<>();

        if (List != null) {
            for (int x = 0; x < List.tagCount(); x++) {
                arrayList.add(SuperWirelessToolDataObject.readFromNBT(List.getCompoundTagAt(x)));
            }
        }
        return arrayList;
    }
}
