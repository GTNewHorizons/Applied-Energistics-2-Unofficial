/*
 * The MIT License (MIT) Copyright (c) 2013 AlgorithmX2 Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions: The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Represents a location in the Minecraft Universe
 */
public class DimensionalCoord extends WorldCoord {

    private static final WeakReference<World> NULL_WORLD = new WeakReference<>(null);
    private final WeakReference<World> w;
    protected final int dimId;

    public DimensionalCoord(final DimensionalCoord s) {
        super(s.x, s.y, s.z);
        this.w = s.w;
        this.dimId = s.dimId;
    }

    public DimensionalCoord(final TileEntity s) {
        super(s);
        this.w = new WeakReference<>(s.getWorldObj());
        this.dimId = s.getWorldObj().provider.dimensionId;
    }

    public DimensionalCoord(final World _w, final int _x, final int _y, final int _z) {
        super(_x, _y, _z);
        this.w = new WeakReference<>(_w);
        this.dimId = _w.provider.dimensionId;
    }

    public DimensionalCoord(final int _x, final int _y, final int _z, final int _dim) {
        super(_x, _y, _z);
        this.w = NULL_WORLD;
        this.dimId = _dim;
    }

    @Override
    public DimensionalCoord copy() {
        return new DimensionalCoord(this);
    }

    @Override
    public int hashCode() {
        return super.hashCode() ^ this.dimId;
    }

    @Override
    public boolean equals(final Object obj) {
        return obj instanceof DimensionalCoord && this.isEqual((DimensionalCoord) obj);
    }

    public boolean isEqual(final DimensionalCoord c) {
        return this.x == c.x && this.y == c.y && this.z == c.z && c.dimId == this.dimId;
    }

    private static void writeToNBT(final NBTTagCompound data, int x, int y, int z, int dimId) {
        data.setInteger("dim", dimId);
        data.setInteger("x", x);
        data.setInteger("y", y);
        data.setInteger("z", z);
    }

    public void writeToNBT(final NBTTagCompound data) {
        writeToNBT(data, this.x, this.y, this.z, this.dimId);
    }

    public static void writeListToNBT(final NBTTagCompound tag, List<DimensionalCoord> list) {
        int i = 0;
        for (DimensionalCoord d : list) {
            NBTTagCompound data = new NBTTagCompound();
            writeToNBT(data, d.x, d.y, d.z, d.dimId);
            tag.setTag("pos#" + i, data);
            i++;
        }
    }

    public static DimensionalCoord readFromNBT(final NBTTagCompound data) {
        return new DimensionalCoord(
                data.getInteger("x"),
                data.getInteger("y"),
                data.getInteger("z"),
                data.getInteger("dim"));
    }

    public static List<DimensionalCoord> readAsListFromNBT(final NBTTagCompound tag) {
        List<DimensionalCoord> list = new ArrayList<>();
        int i = 0;
        while (tag.hasKey("pos#" + i)) {
            NBTTagCompound data = tag.getCompoundTag("pos#" + i);
            list.add(readFromNBT(data));
            i++;
        }
        return list;
    }

    @Override
    public String toString() {
        return "dimension=" + this.dimId + ", " + super.toString();
    }

    public boolean isInWorld(final World world) {
        return this.w.get() == world;
    }

    public World getWorld() {
        return this.w.get();
    }

    public int getDimension() {
        return dimId;
    }
}
