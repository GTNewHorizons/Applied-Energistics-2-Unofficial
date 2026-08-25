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
     * The returned string will be appended as-is (e.g. {@code " [24]"}), with the exception described below.
     *
     * <h3>Client-side localization</h3>
     *
     * The suffix is built on the server, where client language files are not loaded, so anything localized here would
     * stay in the server's language. To defer localization to the client, return the suffix serialized as an
     * {@link net.minecraft.util.IChatComponent} instead of plain text:
     *
     * <pre>
     * IChatComponent suffix = new ChatComponentText(" (");
     * suffix.appendSibling(new ChatComponentItemDisplayName(stack, true));
     * suffix.appendText(")");
     * return IChatComponent.Serializer.func_150696_a(suffix);
     * </pre>
     *
     * The client deserializes it and appends {@code getUnformattedText()}, so any component works, including the custom
     * ones GTNHLib registers ({@code ChatComponentItemDisplayName} resolves an item's display name on the client, with
     * an option to keep only the contents of the last pair of parentheses).
     *
     * <h3>Guarantees</h3>
     *
     * <ul>
     * <li>A suffix that does not start with {@code '{'} is treated as plain text and never modified, so existing
     * providers keep working unchanged.</li>
     * <li>A suffix that starts with {@code '{'} but fails to deserialize, for any reason including an unknown component
     * type, is shown as raw text. This is the defined behaviour, not an error: it keeps a client that lacks the
     * component readable instead of broken.</li>
     * <li>Formatting is dropped, only the text is used.</li>
     * <li>The suffix travels as a single string field in the terminal update packet, which caps it at 16383 bytes of
     * UTF-8. Providers are responsible for keeping the suffix reasonably short.</li>
     * </ul>
     *
     * @return suffix string or {@code null}
     */
    String getInterfaceNameSuffix();
}
