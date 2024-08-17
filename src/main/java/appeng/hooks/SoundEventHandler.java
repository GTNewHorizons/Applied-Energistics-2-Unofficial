package appeng.hooks;

import java.lang.ref.WeakReference;
import java.util.Collection;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.IWorldAccess;
import net.minecraft.world.World;
import net.minecraftforge.event.world.WorldEvent;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import appeng.api.util.DimensionalCoord;
import appeng.me.helpers.AENetworkProxy;
import appeng.parts.p2p.PartP2PSound;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

public final class SoundEventHandler {

    public static final SoundEventHandler INSTANCE = new SoundEventHandler();

    private final Multimap<DimensionalCoord, PartP2PSound> loadedP2Ps = HashMultimap.create(32, 1);

    private SoundEventHandler() {}

    public void activateP2P(PartP2PSound p2p) {
        final AENetworkProxy proxy = p2p.getProxy();
        if (proxy == null) {
            return;
        }
        final DimensionalCoord coord = proxy.getLocation();
        coord.add(p2p.getSide(), 1);
        loadedP2Ps.put(coord, p2p);
    }

    public void deactivateP2P(PartP2PSound p2p) {
        final AENetworkProxy proxy = p2p.getProxy();
        if (proxy == null) {
            return;
        }
        final DimensionalCoord coord = proxy.getLocation();
        coord.add(p2p.getSide(), 1);
        loadedP2Ps.remove(coord, p2p);
    }

    /**
     * @return Sound tunnels that point at the given block coordinate.
     */
    public Collection<PartP2PSound> getTunnelsAround(DimensionalCoord soundPosition) {
        return loadedP2Ps.get(soundPosition);
    }

    @SubscribeEvent
    public void onWorldLoad(final WorldEvent.Load event) {
        if (event.world.isRemote) {
            // ignore clients
            return;
        }
        event.world.addWorldAccess(new SoundProxyAccess(event.world));
    }

    private boolean inSoundEvent = false;

    public class SoundProxyAccess implements IWorldAccess {

        private final WeakReference<World> world;

        public SoundProxyAccess(World world) {
            this.world = new WeakReference<>(world);
        }

        @Override
        public void playRecord(String recordName, int x, int y, int z) {
            if (inSoundEvent) {
                return;
            }
            inSoundEvent = true;
            try {
                final World w = world.get();
                if (w == null) {
                    return;
                }
                final DimensionalCoord position = new DimensionalCoord(w, x, y, z);
                final Collection<PartP2PSound> p2ps = loadedP2Ps.get(position);
                p2ps.forEach(
                        p2p -> p2p.proxyCall(
                                (outPos, outWorld) -> outWorld.playRecord(recordName, outPos.x, outPos.y, outPos.z)));
            } finally {
                inSoundEvent = false;
            }
        }

        @Override
        public void broadcastSound(int soundId, int x, int y, int z, int extraData) {
            // ignored, this is for boss death server-wide sounds
        }

        @Override
        public void playAuxSFX(EntityPlayer optionalPlayer, int soundId, int x, int y, int z, int extraData) {
            if (inSoundEvent) {
                return;
            }
            inSoundEvent = true;
            try {
                final World w = world.get();
                if (w == null) {
                    return;
                }
                final DimensionalCoord position = new DimensionalCoord(w, x, y, z);
                final Collection<PartP2PSound> p2ps = loadedP2Ps.get(position);
                p2ps.forEach(
                        p2p -> p2p.proxyCall(
                                (outPos, outWorld) -> outWorld
                                        .playAuxSFX(soundId, outPos.x, outPos.y, outPos.z, extraData)));
            } finally {
                inSoundEvent = false;
            }
        }

        @Override
        public void playSound(String soundName, double dx, double dy, double dz, float volume, float pitch) {
            if (inSoundEvent) {
                return;
            }
            inSoundEvent = true;
            try {
                final int x = (int) Math.floor(dx), y = (int) Math.floor(dy), z = (int) Math.floor(dz);
                final World w = world.get();
                if (w == null) {
                    return;
                }
                final DimensionalCoord position = new DimensionalCoord(w, x, y, z);
                final Collection<PartP2PSound> p2ps = loadedP2Ps.get(position);
                p2ps.forEach(
                        p2p -> p2p.proxyCall(
                                (outPos, outWorld) -> outWorld
                                        .playSoundEffect(outPos.x, outPos.y, outPos.z, soundName, volume, pitch)));
            } finally {
                inSoundEvent = false;
            }
        }

        @Override
        public void playSoundToNearExcept(EntityPlayer srcPlayer, String soundName, double dx, double dy, double dz,
                float volume, float pitch) {
            if (inSoundEvent) {
                return;
            }
            inSoundEvent = true;
            try {
                final int x = (int) Math.floor(dx), y = (int) Math.floor(dy), z = (int) Math.floor(dz);
                final World w = world.get();
                if (w == null) {
                    return;
                }
                final DimensionalCoord position = new DimensionalCoord(w, x, y, z);
                final Collection<PartP2PSound> p2ps = loadedP2Ps.get(position);
                // Because this sound is proxied, it must be played to srcPlayer too
                p2ps.forEach(
                        p2p -> p2p.proxyCall(
                                (outPos, outWorld) -> outWorld
                                        .playSoundEffect(outPos.x, outPos.y, outPos.z, soundName, volume, pitch)));
            } finally {
                inSoundEvent = false;
            }
        }

        @Override
        public void markBlockForUpdate(int p_147586_1_, int p_147586_2_, int p_147586_3_) {}

        @Override
        public void markBlockForRenderUpdate(int p_147588_1_, int p_147588_2_, int p_147588_3_) {}

        @Override
        public void markBlockRangeForRenderUpdate(int p_147585_1_, int p_147585_2_, int p_147585_3_, int p_147585_4_,
                int p_147585_5_, int p_147585_6_) {}

        @Override
        public void spawnParticle(String p_72708_1_, double p_72708_2_, double p_72708_4_, double p_72708_6_,
                double p_72708_8_, double p_72708_10_, double p_72708_12_) {}

        @Override
        public void onEntityCreate(Entity p_72703_1_) {}

        @Override
        public void onEntityDestroy(Entity p_72709_1_) {}

        @Override
        public void destroyBlockPartially(int p_147587_1_, int p_147587_2_, int p_147587_3_, int p_147587_4_,
                int p_147587_5_) {}

        @Override
        public void onStaticEntitiesChanged() {}
    }
}
