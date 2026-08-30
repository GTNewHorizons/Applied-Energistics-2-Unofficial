package appeng.api.interfaces;

import net.minecraft.util.IChatComponent;

/**
 * Implement this on a TileEntity (or IMetaTileEntity, wrapped via the hosting TileEntity) to provide a dynamic suffix
 * for the AE2 interface name shown in the terminal.
 *
 * Example use-case: a GregTech machine adjacent to an ME Interface can expose its ghost-circuit configuration number so
 * the terminal displays "MachineName [24]".
 */
public interface IInterfaceNameProvider {

    /**
     * Returns a suffix to append to the ME Interface's display name, or {@code null} if no suffix should be shown.
     *
     * The suffix is built on the server, where client language files are not loaded, so it is returned as a component
     * and only turned into text on the client:
     *
     * <pre>
     * IChatComponent suffix = new ChatComponentText(" (");
     * suffix.appendSibling(new ChatComponentItemDisplayName(stack));
     * suffix.appendText(")");
     * return suffix;
     * </pre>
     *
     * Any component works, including the custom ones GTNHLib registers. AE2 serializes it when sending the terminal
     * update and appends {@code getUnformattedText()} on the client, so formatting is dropped and only the text is
     * used. A component the client cannot deserialize is shown as raw text rather than dropping the whole entry.
     *
     * The serialized form travels as a single string field in the terminal update packet, which caps it at 16383 bytes
     * of UTF-8. Providers are responsible for keeping the suffix reasonably short.
     *
     * @return suffix component or {@code null}
     */
    IChatComponent getInterfaceNameSuffix();
}
