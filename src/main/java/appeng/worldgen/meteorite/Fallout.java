package appeng.worldgen.meteorite;

import net.minecraft.block.Block;
import net.minecraft.init.Blocks;

import appeng.api.definitions.IBlockDefinition;
import appeng.util.Platform;

public class Fallout {

    private final MeteoriteBlockPutter putter;
    private final IBlockDefinition skyStoneDefinition;

    public Fallout(final MeteoriteBlockPutter putter, final IBlockDefinition skyStoneDefinition) {
        this.putter = putter;
        this.skyStoneDefinition = skyStoneDefinition;
    }

    public int adjustCrater() {
        return 0;
    }

    public void getRandomFall(final double random, final IMeteoriteWorld w, final int x, final int y, final int z) {
        if (random > 0.9) {
            this.putter.put(w, x, y, z, Blocks.stone);
        } else if (random > 0.8) {
            this.putter.put(w, x, y, z, Blocks.cobblestone);
        } else if (random > 0.7) {
            this.putter.put(w, x, y, z, w.getWorld().getBiomeGenForCoords(x, z).fillerBlock);
        } else {
            this.putter.put(w, x, y, z, Blocks.gravel);
        }
    }

    public void getRandomInset(final double random, final IMeteoriteWorld w, final int x, final int y, final int z) {
        if (random > 0.9) {
            this.putter.put(w, x, y, z, Blocks.cobblestone);
        } else if (random > 0.8) {
            this.putter.put(w, x, y, z, Blocks.stone);
        } else if (random > 0.7) {
            this.putter.put(w, x, y, z, w.getWorld().getBiomeGenForCoords(x, z).topBlock);
        } else if (random > 0.6) {
            for (final Block skyStoneBlock : this.skyStoneDefinition.maybeBlock().asSet()) {
                this.putter.put(w, x, y, z, skyStoneBlock);
            }
        } else if (random > 0.5) {
            this.putter.put(w, x, y, z, Blocks.gravel);
        } else {
            this.putter.put(w, x, y, z, Platform.AIR_BLOCK);
        }
    }
}
