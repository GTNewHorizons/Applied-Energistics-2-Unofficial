package appeng.api.features;

import java.util.Set;

import appeng.api.util.IInterfaceViewable;

/**
 * Registry for interface terminal support.
 */
public interface IInterfaceTerminalRegistry {

    /**
     * Registers a class to be considered supported in interface terminals.
     */
    void register(Class<? extends IInterfaceViewable> clazz);

    /**
     * Get all supported classes that were registered during startup
     */
    Set<Class<? extends IInterfaceViewable>> getSupportedClasses();
}
