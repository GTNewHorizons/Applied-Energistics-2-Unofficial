package appeng.api.interfaces;

/**
 * Implement this on a TileEntity (or IMetaTileEntity, wrapped via the hosting TileEntity) to provide a dynamic suffix
 * for the AE2 interface name shown in the terminal.
 *
 * Example use-case: a GregTech machine adjacent to an ME Interface can expose its ghost-circuit configuration number so
 * the terminal displays "MachineName [24]".
 */
public interface IInterfaceNameProvider {

    /**
     * Returns a suffix string to append to the ME Interface's display name, or {@code null} if no suffix should be
     * shown.
     *
     * <p>
     * The returned string will be appended as-is (e.g. {@code " [24]"}). The ME Interface is responsible for
     * deduplication/replacement of an existing bracket suffix.
     * </p>
     *
     * @return suffix string or {@code null}
     */
    String getInterfaceNameSuffix();
}
