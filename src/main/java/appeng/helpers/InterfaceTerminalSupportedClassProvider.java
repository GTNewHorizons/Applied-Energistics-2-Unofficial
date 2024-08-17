package appeng.helpers;

import java.util.Set;

/**
 * Interface Terminal Support handler.
 *
 * @deprecated This is being refactored to the API.
 */
@Deprecated
public class InterfaceTerminalSupportedClassProvider {

    @Deprecated
    public static Set<Class<? extends IInterfaceTerminalSupport>> getSupportedClasses() {
        return null;
    }

    @Deprecated
    public static void register(Class<? extends IInterfaceTerminalSupport> clazz) {}
}
