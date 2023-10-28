package appeng.api.features;

import appeng.api.util.IInterfaceViewable;

/**
 * Registry for interface terminal support.
 */
public interface IInterfaceTerminalRegistry {

    /**
     * Registers a class to be considered supported in interface terminals.
     */
    void register(Class<? extends IInterfaceViewable> clazz);
}
