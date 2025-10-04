/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package appeng.worldgen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.AEApi;
import appeng.api.definitions.IBlockDefinition;
import appeng.api.definitions.IBlocks;
import appeng.core.AEConfig;
import appeng.core.AEJSONConfig;
import appeng.core.AEJSONEntry;
import appeng.core.AELog;
import appeng.core.features.AEFeature;
import appeng.core.worlddata.WorldData;
import appeng.util.InventoryAdaptor;
import appeng.util.Platform;
import appeng.worldgen.meteorite.Fallout;
import appeng.worldgen.meteorite.FalloutCopy;
import appeng.worldgen.meteorite.FalloutSand;
import appeng.worldgen.meteorite.FalloutSnow;
import appeng.worldgen.meteorite.IMeteoriteWorld;
import appeng.worldgen.meteorite.MeteoriteBlockPutter;
import cpw.mods.fml.common.registry.GameRegistry;

public final class MeteoritePlacer {

    private static final long SEED_OFFSET_CHEST_LOOT = 1;
    private static final long SEED_OFFSET_DECAY = 2;

    private static final Collection<Block> validSpawn = new HashSet<>();
    private static final Collection<Block> invalidSpawn = new HashSet<>();
    private static IBlockDefinition skyChestDefinition;
    private static IBlockDefinition skyStoneDefinition;

    private final IMeteoriteWorld world;
    private final long seed;
    private final int x, y, z;
    private final int skyMode;
    private final double meteoriteSize;
    private final double craterSize;
    private final double squaredMeteoriteSize;
    private final double squaredCraterSize;
    private final MeteoriteBlockPutter putter = new MeteoriteBlockPutter();
    private final NBTTagCompound settings;
    private Fallout type;

    private static void initializeSpawnLists() {
        if (validSpawn.isEmpty()) {
            final IBlocks blocks = AEApi.instance().definitions().blocks();
            skyChestDefinition = blocks.skyChest();
            skyStoneDefinition = blocks.skyStone();

            validSpawn.clear();
            validSpawn.add(Blocks.stone);
            validSpawn.add(Blocks.cobblestone);
            validSpawn.add(Blocks.grass);
            validSpawn.add(Blocks.sand);
            validSpawn.add(Blocks.dirt);
            validSpawn.add(Blocks.gravel);
            validSpawn.add(Blocks.netherrack);
            validSpawn.add(Blocks.iron_ore);
            validSpawn.add(Blocks.gold_ore);
            validSpawn.add(Blocks.diamond_ore);
            validSpawn.add(Blocks.redstone_ore);
            validSpawn.add(Blocks.hardened_clay);
            validSpawn.add(Blocks.ice);
            validSpawn.add(Blocks.snow);
            validSpawn.add(Blocks.stained_hardened_clay);

            for (String block : AEConfig.instance.meteoriteValidBlocks) {
                try {
                    String[] parts = block.split(":");
                    if (parts.length != 2) {
                        AELog.error(
                                "AE2: Invalid Block ID Format for validSpawnBlockWhiteList: " + block
                                        + " | Error: Too Many Semicolons");
                    }
                    Block blk = GameRegistry.findBlock(parts[0], parts[1]);
                    if (blk != null) {
                        validSpawn.add(blk);
                    } else {
                        AELog.error(
                                "AE2: Could not find block in registry for validSpawnBlockWhiteList: " + block
                                        + " | Error: Block not found");
                    }
                } catch (Exception e) {
                    AELog.error(
                            e,
                            "AE2: errored while whitelisting meteorite block spawns: " + e.getLocalizedMessage()
                                    + " | Error: Unknown | Stacktrace: "
                                    + Arrays.toString(e.getStackTrace()));
                }
            }

            invalidSpawn.clear();
            invalidSpawn.addAll(skyStoneDefinition.maybeBlock().asSet());
            invalidSpawn.add(Blocks.planks);
            invalidSpawn.add(Blocks.iron_door);
            invalidSpawn.add(Blocks.iron_bars);
            invalidSpawn.add(Blocks.wooden_door);
            invalidSpawn.add(Blocks.brick_block);
            invalidSpawn.add(Blocks.clay);
            invalidSpawn.add(Blocks.water);
            invalidSpawn.add(Blocks.log);
            invalidSpawn.add(Blocks.log2);

            for (String block : AEConfig.instance.meteoriteInvalidBlocks) {
                try {
                    String[] parts = block.split(":");
                    if (parts.length != 2) {
                        AELog.error(
                                "AE2: Invalid Block ID Format for invalidSpawnBlockWhiteList: " + block
                                        + " | Error: Too Many Semicolons");
                    }
                    Block blk = GameRegistry.findBlock(parts[0], parts[1]);
                    if (blk != null) {
                        invalidSpawn.add(blk);
                    } else {
                        AELog.error(
                                "AE2: Could not find block in registry for invalidSpawnBlockWhiteList: " + block
                                        + " | Error: Block not found");
                    }
                } catch (Exception e) {
                    AELog.error(
                            e,
                            "AE2: errored while blacklisting meteorite block spawns: " + e.getLocalizedMessage()
                                    + " | Error: Unknown | Stacktrace: "
                                    + Arrays.toString(e.getStackTrace()));
                }
            }
        }
    }

    public MeteoritePlacer(final IMeteoriteWorld world, final long seed, final int x, final int y, final int z) {
        initializeSpawnLists();

        this.seed = seed;
        Random rng = new Random(seed);

        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.meteoriteSize = (rng.nextDouble() * 6.0) + 2;
        this.craterSize = this.meteoriteSize * 2 + 5;
        this.squaredMeteoriteSize = this.meteoriteSize * this.meteoriteSize;
        this.squaredCraterSize = this.craterSize * this.craterSize;

        this.type = new Fallout(this.putter, skyStoneDefinition);

        int skyMode = 0;
        for (int i = x - 15; i < x + 15; i++) {
            for (int j = y - 15; j < y + 11; j++) {
                for (int k = z - 15; k < z + 15; k++) {
                    if (world.canBlockSeeTheSky(i, j, k)) {
                        skyMode++;
                    }
                }
            }
        }
        boolean solid = true;
        for (int j = y - 15; j < y - 1; j++) {
            if (world.getBlock(x, j, z) == Platform.AIR_BLOCK) {
                solid = false;
            }
        }
        if (!solid) {
            skyMode = 0;
        }
        this.skyMode = skyMode;

        Block blk = world.getBlock(x, y, z);
        this.settings = new NBTTagCompound();
        this.settings.setLong("seed", seed);
        this.settings.setInteger("x", x);
        this.settings.setInteger("y", y);
        this.settings.setInteger("z", z);
        this.settings.setInteger("blk", Block.getIdFromBlock(blk));
        this.settings.setInteger("skyMode", skyMode);

        this.settings.setDouble("real_sizeOfMeteorite", this.meteoriteSize);
        this.settings.setDouble("realCrater", this.craterSize);
        this.settings.setDouble("sizeOfMeteorite", this.squaredMeteoriteSize);
        this.settings.setDouble("crater", this.squaredCraterSize);

        this.settings.setBoolean("lava", rng.nextFloat() > 0.9F);
    }

    public MeteoritePlacer(final IMeteoriteWorld world, final NBTTagCompound meteoriteBlob) {
        Random rng = new Random();
        this.settings = meteoriteBlob;
        long dataSeed = meteoriteBlob.getLong("seed");
        // Meteor generated without a pre-set seed, from an older version
        if (dataSeed == 0) {
            // Generate a position-based seed
            Platform.seedFromGrid(
                    rng,
                    world.getWorld().getSeed(),
                    meteoriteBlob.getInteger("x"),
                    meteoriteBlob.getInteger("z"));
            while (dataSeed == 0) {
                dataSeed = rng.nextLong();
            }
        }
        this.seed = dataSeed;
        rng.setSeed(dataSeed);

        this.world = world;
        this.x = this.settings.getInteger("x");
        this.y = this.settings.getInteger("y");
        this.z = this.settings.getInteger("z");

        this.type = new Fallout(this.putter, skyStoneDefinition);

        this.meteoriteSize = this.settings.getDouble("real_sizeOfMeteorite");
        this.craterSize = this.settings.getDouble("realCrater");
        this.squaredMeteoriteSize = this.settings.getDouble("sizeOfMeteorite");
        this.squaredCraterSize = this.settings.getDouble("crater");
        this.skyMode = this.settings.getInteger("skyMode");
    }

    void spawnMeteorite() {
        final Block blk = Block.getBlockById(this.settings.getInteger("blk"));

        if (blk == Blocks.sand) {
            this.type = new FalloutSand(world, x, y, z, this.putter, skyStoneDefinition);
        } else if (blk == Blocks.hardened_clay) {
            this.type = new FalloutCopy(world, x, y, z, this.putter, skyStoneDefinition);
        } else if (blk == Blocks.ice || blk == Blocks.snow) {
            this.type = new FalloutSnow(world, x, y, z, this.putter, skyStoneDefinition);
        }

        // Crater
        if (skyMode > 10) {
            this.placeCrater(world, x, y, z);
        }

        this.placeMeteorite(world, x, y, z);

        // collapse blocks...
        if (skyMode > 3) {
            this.decay(world, x, y, z);
        }

        world.done();
    }

    private void placeCrater(final IMeteoriteWorld w, final int x, final int y, final int z) {
        final boolean lava = this.settings.getBoolean("lava");

        final int maxY = 255;
        final int minX = w.minX(x - 200);
        final int maxX = w.maxX(x + 200);
        final int minZ = w.minZ(z - 200);
        final int maxZ = w.maxZ(z + 200);

        for (int j = y - 5; j < maxY; j++) {
            boolean changed = false;

            for (int i = minX; i < maxX; i++) {
                for (int k = minZ; k < maxZ; k++) {
                    final double dx = i - x;
                    final double dz = k - z;
                    final double h = y - this.meteoriteSize + 1 + this.type.adjustCrater();

                    final double distanceFrom = dx * dx + dz * dz;

                    if (j > h + distanceFrom * 0.02) {
                        if (lava && j < y && w.getBlock(x, y - 1, z).isBlockSolid(w.getWorld(), i, j, k, 0)) {
                            if (j > h + distanceFrom * 0.02) {
                                this.putter.put(w, i, j, k, Blocks.lava);
                            }
                        } else {
                            changed = this.putter.put(w, i, j, k, Platform.AIR_BLOCK) || changed;
                        }
                    }
                }
            }
        }

        for (final Object o : w.getWorld().getEntitiesWithinAABB(
                EntityItem.class,
                AxisAlignedBB.getBoundingBox(
                        w.minX(x - 30),
                        y - 5,
                        w.minZ(z - 30),
                        w.maxX(x + 30),
                        y + 30,
                        w.maxZ(z + 30)))) {
            final Entity e = (Entity) o;
            e.setDead();
        }
    }

    private void placeMeteorite(final IMeteoriteWorld w, final int x, final int y, final int z) {
        final int meteorXLength = w.minX(x - 8);
        final int meteorXHeight = w.maxX(x + 8);
        final int meteorZLength = w.minZ(z - 8);
        final int meteorZHeight = w.maxZ(z + 8);

        // spawn meteor
        for (int i = meteorXLength; i < meteorXHeight; i++) {
            for (int j = y - 8; j < y + 8; j++) {
                for (int k = meteorZLength; k < meteorZHeight; k++) {
                    final double dx = i - x;
                    final double dy = j - y;
                    final double dz = k - z;

                    if (dx * dx * 0.7 + dy * dy * (j > y ? 1.4 : 0.8) + dz * dz * 0.7 < this.squaredMeteoriteSize) {
                        for (final Block skyStoneBlock : skyStoneDefinition.maybeBlock().asSet()) {
                            this.putter.put(w, i, j, k, skyStoneBlock);
                        }
                    }
                }
            }
        }

        if (AEConfig.instance.isFeatureEnabled(AEFeature.SpawnPressesInMeteorites)) {
            for (final Block skyChestBlock : skyChestDefinition.maybeBlock().asSet()) {
                this.putter.put(w, x, y, z, skyChestBlock);
            }

            final TileEntity te = w.getTileEntity(x, y, z);
            if (te instanceof IInventory) {
                try {
                    Random lootRng = new Random(this.seed + SEED_OFFSET_CHEST_LOOT);
                    InventoryAdaptor ap = InventoryAdaptor.getAdaptor(te, ForgeDirection.UP);

                    int dimID = w.getWorld().provider.dimensionId;
                    ArrayList<AEJSONEntry> lootTable = new ArrayList<>(
                            AEJSONConfig.instance.getWeightedLootTable(dimID, lootRng));
                    Map<Integer, ArrayList<AEJSONEntry>> exclusionTableMap = new HashMap<>();

                    int totalNormalWeight = 0; // Non-exclusive entries
                    for (AEJSONEntry entry : lootTable) {
                        if (entry.exclusiveGroupID == -1) {
                            totalNormalWeight += entry.weight;
                        }
                    }
                    ArrayList<ItemStack> loot = new ArrayList<>();

                    int curWeight = 0;
                    int randWeight = (totalNormalWeight != 0 ? lootRng.nextInt(totalNormalWeight) : 0);
                    for (AEJSONEntry entry : lootTable) {
                        if (entry.exclusiveGroupID == -1) {
                            curWeight += entry.weight;
                            if (randWeight <= curWeight) {
                                loot.add(entry.getItemStack(lootRng));
                            }
                        } else {
                            if (exclusionTableMap.containsKey(entry.exclusiveGroupID)) {
                                ArrayList<AEJSONEntry> temp = exclusionTableMap.get(entry.exclusiveGroupID);
                                temp.add(entry);
                                exclusionTableMap.put(entry.exclusiveGroupID, temp);
                            } else exclusionTableMap.put(entry.exclusiveGroupID, new ArrayList<>(Arrays.asList(entry)));
                        }
                    }
                    for (Integer key : exclusionTableMap.keySet()) {
                        if (exclusionTableMap.get(key).size() > 1) {
                            int totalExclusiveWeight = 0;
                            for (AEJSONEntry entry : exclusionTableMap.get(key)) {
                                totalExclusiveWeight += entry.weight;
                            }
                            randWeight = (totalExclusiveWeight != 0 ? lootRng.nextInt(totalExclusiveWeight) : 0);
                            curWeight = 0;
                            for (AEJSONEntry entry : exclusionTableMap.get(key)) {
                                curWeight += entry.weight;
                                if (randWeight <= curWeight) {
                                    loot.add(entry.getItemStack(lootRng));
                                    break;
                                }
                            }
                        } else {
                            AEJSONEntry entry = exclusionTableMap.get(key).get(0);
                            if (entry.weight > 0) {
                                loot.add(entry.getItemStack(lootRng));
                            }
                        }
                    }

                    for (ItemStack items : loot) {
                        if (items != null) {
                            ap.addItems(items.copy());
                        } else AELog.error(
                                "AE2: Item is null! | Error: Failed while adding item to loot chest in meteoritePlacer");
                    }
                } catch (Exception e) {
                    AELog.error(
                            e,
                            "AE2: An unexpected error occurred! Check your JSON or report if issue is persistent! | Error: Runtime error while loading loot for meteorite. Printing info: \n"
                                    + "Stack Trace: "
                                    + Arrays.toString(e.getStackTrace())
                                    + "\n"
                                    + "Stack Message: "
                                    + e.getMessage()
                                    + "\n"
                                    + "Class: "
                                    + e.getClass()
                                    + "\n");
                }
            }
        }
    }

    private void decay(final IMeteoriteWorld w, final int x, final int y, final int z) {
        final Random decayRng = new Random(this.seed + SEED_OFFSET_DECAY);
        double randomShit = 0;

        final int meteorXLength = w.minX(x - 30);
        final int meteorXHeight = w.maxX(x + 30);
        final int meteorZLength = w.minZ(z - 30);
        final int meteorZHeight = w.maxZ(z + 30);

        for (int i = meteorXLength; i < meteorXHeight; i++) {
            for (int k = meteorZLength; k < meteorZHeight; k++) {
                for (int j = y - 9; j < y + 30; j++) {
                    Block blk = w.getBlock(i, j, k);
                    if (blk == Blocks.lava) {
                        continue;
                    }

                    if (blk.isReplaceable(w.getWorld(), i, j, k)) {
                        blk = Platform.AIR_BLOCK;
                        final Block blk_b = w.getBlock(i, j + 1, k);

                        if (blk_b != blk) {
                            final int meta_b = w.getBlockMetadata(i, j + 1, k);

                            w.setBlock(i, j, k, blk_b, meta_b, 3);
                            w.setBlock(i, j + 1, k, blk);
                        } else if (randomShit < 100 * this.squaredCraterSize) {
                            final double dx = i - x;
                            final double dy = j - y;
                            final double dz = k - z;
                            final double dist = dx * dx + dy * dy + dz * dz;

                            final Block xf = w.getBlock(i, j - 1, k);
                            if (!xf.isReplaceable(w.getWorld(), i, j - 1, k)) {
                                final double extraRange = decayRng.nextDouble() * 0.6;
                                final double height = this.squaredCraterSize * (extraRange + 0.2)
                                        - Math.abs(dist - this.squaredCraterSize * 1.7);

                                if (xf != blk && height > 0 && decayRng.nextFloat() > 0.6F) {
                                    randomShit++;
                                    this.type.getRandomFall(decayRng.nextDouble(), w, i, j, k);
                                }
                            }
                        }
                    } else {
                        // decay.
                        final Block blk_b = w.getBlock(i, j + 1, k);
                        if (blk_b == Platform.AIR_BLOCK) {
                            if (decayRng.nextFloat() > 0.4F) {
                                final double dx = i - x;
                                final double dy = j - y;
                                final double dz = k - z;

                                if (dx * dx + dy * dy + dz * dz < this.squaredCraterSize * 1.6) {
                                    this.type.getRandomInset(decayRng.nextDouble(), w, i, j, k);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public boolean spawnMeteoriteCenter() {

        if (!world.hasNoSky()) {
            return false;
        }

        Block blk = world.getBlock(x, y, z);
        if (!validSpawn.contains(blk)) {
            return false; // must spawn on a valid block..
        }

        if (blk == Blocks.sand) {
            this.type = new FalloutSand(world, x, y, z, this.putter, skyStoneDefinition);
        } else if (blk == Blocks.hardened_clay) {
            this.type = new FalloutCopy(world, x, y, z, this.putter, skyStoneDefinition);
        } else if (blk == Blocks.ice || blk == Blocks.snow) {
            this.type = new FalloutSnow(world, x, y, z, this.putter, skyStoneDefinition);
        }

        int realValidBlocks = 0;

        for (int i = x - 6; i < x + 6; i++) {
            for (int j = y - 6; j < y + 6; j++) {
                for (int k = z - 6; k < z + 6; k++) {
                    blk = world.getBlock(i, j, k);
                    if (validSpawn.contains(blk)) {
                        realValidBlocks++;
                    }
                }
            }
        }

        int validBlocks = 0;
        for (int i = x - 15; i < x + 15; i++) {
            for (int j = y - 15; j < y + 15; j++) {
                for (int k = z - 15; k < z + 15; k++) {
                    blk = world.getBlock(i, j, k);
                    if (invalidSpawn.contains(blk)) {
                        return false;
                    }
                    if (validSpawn.contains(blk)) {
                        validBlocks++;
                    }
                }
            }
        }

        final int minBlocks = 200;
        if (validBlocks > minBlocks && realValidBlocks > 80) {
            // We can spawn here!
            // Crater
            if (skyMode > 10) {
                this.placeCrater(world, x, y, z);
            }

            this.placeMeteorite(world, x, y, z);

            // collapse blocks...
            if (skyMode > 3) {
                this.decay(world, x, y, z);
            }

            world.done();

            WorldData.instance().spawnData()
                    .addNearByMeteorites(world.getWorld().provider.dimensionId, x >> 4, z >> 4, this.settings);
            return true;
        }
        return false;
    }

    NBTTagCompound getSettings() {
        return this.settings;
    }
}
