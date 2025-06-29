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

import java.util.Random;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.chunk.IChunkProvider;

import appeng.api.features.IWorldGen.WorldGenType;
import appeng.core.AEConfig;
import appeng.core.features.registries.WorldGenRegistry;
import appeng.core.worlddata.WorldData;
import appeng.hooks.TickHandler;
import appeng.util.IWorldCallable;
import appeng.util.Platform;
import appeng.worldgen.meteorite.ChunkOnly;
import cpw.mods.fml.common.IWorldGenerator;

public final class MeteoriteWorldGen implements IWorldGenerator {

    private static final int DEFAULTVALUE = -3124; // This is the value which is assigned for cellSize and
                                                   // spawnSurfaceMeteorite. These variables should be reassigned unless
                                                   // something goes wrong.

    @Override
    public void generate(final Random rng, final int chunkX, final int chunkZ, final World world,
            final IChunkProvider chunkGenerator, final IChunkProvider chunkProvider) {

        if (!WorldGenRegistry.INSTANCE.isWorldGenEnabled(WorldGenType.Meteorites, world)) {
            WorldData.instance().compassData().service().updateArea(world, chunkX, chunkZ);
            return;
        }

        // Find the meteorite grid cell corresponding to this chunk
        int gridCellSize = DEFAULTVALUE;
        for (String distance : AEConfig.instance.minMeteoriteDistance) {
            String[] split = distance.split("=");
            if (split[0].equals(String.valueOf(world.provider.dimensionId))) {
                gridCellSize = Math.max(8, Integer.parseInt(split[1]));
                break;
            }
            if (split[0].equals("0") && gridCellSize == DEFAULTVALUE) {
                gridCellSize = Integer.parseInt(split[1]); // set overworld value instead default value
            }
        }
        if (gridCellSize == DEFAULTVALUE)
            throw new RuntimeException("AE2: MISSING VALUE minMeteoriteDistance FOR OVERWORLD");

        final int gridCellMargin = Math.max(1, gridCellSize / 10);
        final int gridX = Math.floorDiv(chunkX << 4, gridCellSize);
        final int gridZ = Math.floorDiv(chunkZ << 4, gridCellSize);
        // Override chunk-based seed with grid-based seed, constructed in the same way as the FML-provided seed
        Platform.seedFromGrid(rng, world.getSeed(), gridX, gridZ);
        // Calculate a deterministic position of the meteorite in the grid cell
        int check = DEFAULTVALUE;
        for (String chance : AEConfig.instance.meteoriteSpawnChance) {
            String[] split = chance.split("=");
            if (split[0].equals(String.valueOf(world.provider.dimensionId))) {
                check = (rng.nextDouble() < Double.parseDouble(split[1]) ? 1 : 0);
            }
            if (split[0].equals("0") && check == DEFAULTVALUE) {
                check = (rng.nextDouble() < Double.parseDouble(split[1]) ? 1 : 0); // set overworld value instead
                                                                                   // default value
            }
        }
        if (check == DEFAULTVALUE) throw new RuntimeException("AE2: MISSING VALUE meteoriteSpawnChance FOR OVERWORLD");

        boolean spawnSurfaceMeteor = (check == 1);

        final int meteorX = (gridX * gridCellSize) + rng.nextInt(gridCellSize - 2 * gridCellMargin) + gridCellMargin;
        final int meteorZ = (gridZ * gridCellSize) + rng.nextInt(gridCellSize - 2 * gridCellMargin) + gridCellMargin;
        final int meteorDepth = 180 + rng.nextInt(20);
        final int meteorChunkX = meteorX >> 4;
        final int meteorChunkZ = meteorZ >> 4;
        long meteorSeed = rng.nextLong();
        while (meteorSeed == 0) {
            meteorSeed = rng.nextLong();
        }
        // add new meteorites?
        if ((meteorChunkX == chunkX) && (meteorChunkZ == chunkZ)) {
            TickHandler.INSTANCE.addCallable(world, new ExistingMeteoriteSpawn(chunkX, chunkZ));
            TickHandler.INSTANCE.addCallable(
                    world,
                    new MeteoriteSpawn(meteorX, spawnSurfaceMeteor ? meteorDepth : 128, meteorZ, meteorSeed));
        } else {
            TickHandler.INSTANCE.addCallable(world, new ExistingMeteoriteSpawn(chunkX, chunkZ));
        }

    }

    /**
     * Spawns blocks for meteorites that were previously generated in neighboring chunks
     */
    private static final class ExistingMeteoriteSpawn implements IWorldCallable<Object> {

        private final int chunkX;
        private final int chunkZ;

        public ExistingMeteoriteSpawn(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private Iterable<NBTTagCompound> getNearByMeteorites(final World w, final int chunkX, final int chunkZ) {
            return WorldData.instance().spawnData().getNearByMeteorites(w.provider.dimensionId, chunkX, chunkZ);
        }

        @Override
        public Object call(final World world) throws Exception {
            // Generate blocks for nearby meteorites
            for (final NBTTagCompound data : this.getNearByMeteorites(world, chunkX, chunkZ)) {
                final MeteoritePlacer mp = new MeteoritePlacer(new ChunkOnly(world, chunkX, chunkZ), data);
                mp.spawnMeteorite();
            }

            WorldData.instance().spawnData().setGenerated(world.provider.dimensionId, chunkX, chunkZ);
            WorldData.instance().compassData().service().updateArea(world, chunkX, chunkZ);
            return null;
        }
    }

    private static final class MeteoriteSpawn implements IWorldCallable<Object> {

        private final int x;
        private final int z;
        private final int depth;
        private final long seed;

        public MeteoriteSpawn(final int x, final int depth, final int z, final long seed) {
            this.x = x;
            this.z = z;
            this.depth = depth;
            this.seed = seed;
        }

        private boolean tryMeteorite(final World w) {
            int depth = this.depth;
            for (int tries = 0; tries < 20; tries++) {
                final MeteoritePlacer mp = new MeteoritePlacer(
                        new ChunkOnly(w, x >> 4, z >> 4),
                        this.seed,
                        x,
                        depth,
                        z);

                if (mp.spawnMeteoriteCenter()) {
                    final int px = x >> 4;
                    final int pz = z >> 4;

                    for (int cx = px - 6; cx < px + 6; cx++) {
                        for (int cz = pz - 6; cz < pz + 6; cz++) {
                            if (w.getChunkProvider().chunkExists(cx, cz)) {
                                if (px == cx && pz == cz) {
                                    continue;
                                }

                                if (WorldData.instance().spawnData().hasGenerated(w.provider.dimensionId, cx, cz)) {
                                    final MeteoritePlacer mp2 = new MeteoritePlacer(
                                            new ChunkOnly(w, cx, cz),
                                            mp.getSettings());
                                    mp2.spawnMeteorite();
                                }
                            }
                        }
                    }

                    return true;
                }

                depth -= 15;
                if (depth < 40) {
                    return false;
                }
            }

            return false;
        }

        @Override
        public Object call(final World world) throws Exception {
            final int chunkX = this.x >> 4;
            final int chunkZ = this.z >> 4;

            this.tryMeteorite(world);

            WorldData.instance().spawnData().setGenerated(world.provider.dimensionId, chunkX, chunkZ);
            WorldData.instance().compassData().service().updateArea(world, chunkX, chunkZ);

            return null;
        }
    }
}
