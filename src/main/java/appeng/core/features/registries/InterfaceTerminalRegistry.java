package appeng.core.features.registries;

import java.util.HashSet;
import java.util.Set;

import appeng.api.features.IInterfaceTerminalRegistry;
import appeng.api.util.IInterfaceViewable;
import appeng.parts.misc.PartInterface;
import appeng.parts.p2p.PartP2PInterface;
import appeng.tile.misc.TileInterface;

/**
 * Interface Terminal Registry impl for registering viewable instances.
 */
public class InterfaceTerminalRegistry implements IInterfaceTerminalRegistry {

    private final Set<Class<? extends IInterfaceViewable>> supportedClasses = new HashSet<>();
    private static InterfaceTerminalRegistry INSTANCE;

    /**
     * Singleton, do not instantiate more than once.
     */
    public InterfaceTerminalRegistry() {
        supportedClasses.add(TileInterface.class);
        supportedClasses.add(PartInterface.class);
        supportedClasses.add(PartP2PInterface.class);
        INSTANCE = this;
    }

    /**
     * Get all supported classes that were registered during startup
     */
    public Set<Class<? extends IInterfaceViewable>> getSupportedClasses() {
        return supportedClasses;
    }

    @Override
    public void register(Class<? extends IInterfaceViewable> clazz) {
        supportedClasses.add(clazz);
    }

    public static InterfaceTerminalRegistry instance() {
        return INSTANCE;
    }
}
