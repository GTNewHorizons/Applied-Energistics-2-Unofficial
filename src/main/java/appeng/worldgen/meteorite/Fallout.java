package appeng.worldgen.meteorite;

import java.util.HashMap;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import appeng.api.definitions.IBlockDefinition;
import appeng.util.Platform;

public class Fallout {

    private final MeteoriteBlockPutter putter;
    private final IBlockDefinition skyStoneDefinition;
    private static HashMap<Integer, Block[]> dimensionDebrisBlocks = new HashMap<>();
    private static HashMap<Integer, int[]> dimensionDebrisMeta = new HashMap<>();

    public Fallout(final MeteoriteBlockPutter putter, final IBlockDefinition skyStoneDefinition) {
        this.putter = putter;
        dimensionDebrisBlocks.putIfAbsent(0, new Block[] { null, null, null, null, null });
        dimensionDebrisMeta.putIfAbsent(0, new int[] { 0, 0, 0, 0, 0 });
        this.skyStoneDefinition = skyStoneDefinition;
    }

    public static void addDebrisToDimension(int dimensionID, Block[] list, int meta[]) {
        dimensionDebrisBlocks.put(dimensionID, list);
        dimensionDebrisMeta.put(dimensionID, meta);
    }

    public int adjustCrater() {
        return 0;
    }

    public void getRandomFall(final double random, final IMeteoriteWorld w, final int x, final int y, final int z) {
        Block[] list = dimensionDebrisBlocks.get(w.getWorld().provider.dimensionId);
        int[] meta = dimensionDebrisMeta.get(w.getWorld().provider.dimensionId);
        if (random > 0.9) {
            this.putter.put(w, x, y, z, (list[0] == null ? Blocks.stone : list[0]), meta[0]);
        } else if (random > 0.8) {
            this.putter.put(w, x, y, z, (list[1] == null ? Blocks.cobblestone : list[1]), meta[1]);
        } else if (random > 0.7) {
            this.putter.put(
                    w,
                    x,
                    y,
                    z,
                    (list[2] == null ? w.getWorld().getBiomeGenForCoords(x, z).fillerBlock : list[2]),
                    meta[2]);
        } else {
            this.putter.put(w, x, y, z, (list[3] == null ? Blocks.gravel : list[3]), meta[3]);
        }
    }

    public void getRandomInset(final double random, final IMeteoriteWorld w, final int x, final int y, final int z) {
        Block[] list = dimensionDebrisBlocks.get(w.getWorld().provider.dimensionId);
        int[] meta = dimensionDebrisMeta.get(w.getWorld().provider.dimensionId);
        if (random > 0.9) {
            this.putter.put(w, x, y, z, (list[1] == null ? Blocks.cobblestone : list[1]), meta[1]);
        } else if (random > 0.8) {
            this.putter.put(w, x, y, z, (list[0] == null ? Blocks.stone : list[0]), meta[0]);
        } else if (random > 0.7) {
            this.putter.put(
                    w,
                    x,
                    y,
                    z,
                    (list[4] == null ? w.getWorld().getBiomeGenForCoords(x, z).topBlock : list[4]),
                    meta[4]);
        } else if (random > 0.6) {
            for (final Block skyStoneBlock : this.skyStoneDefinition.maybeBlock().asSet()) {
                this.putter.put(w, x, y, z, skyStoneBlock);
            }
        } else if (random > 0.5) {
            this.putter.put(w, x, y, z, (list[3] == null ? Blocks.gravel : list[3]), meta[3]);
        } else {
            this.putter.put(w, x, y, z, Platform.AIR_BLOCK);
        }
    }
}
