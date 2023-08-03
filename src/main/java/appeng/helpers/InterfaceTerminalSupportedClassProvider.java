package appeng.helpers;

import java.util.HashSet;
import java.util.Set;

import appeng.parts.misc.PartInterface;
import appeng.parts.p2p.PartP2PInterface;
import appeng.tile.misc.TileInterface;

public class InterfaceTerminalSupportedClassProvider {

    private static final Set<Class<? extends IInterfaceTerminalSupport>> supportedClasses = new HashSet<>();

    static {
        supportedClasses.add(TileInterface.class);
        supportedClasses.add(PartInterface.class);
        supportedClasses.add(PartP2PInterface.class);
    }

    public static Set<Class<? extends IInterfaceTerminalSupport>> getSupportedClasses() {
        return supportedClasses;
    }

    public static void register(Class<? extends IInterfaceTerminalSupport> clazz) {
        supportedClasses.add(clazz);
    }
}
