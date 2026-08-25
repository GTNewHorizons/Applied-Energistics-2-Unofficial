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
     * The returned string will be appended as-is (e.g. {@code " [24]"}), with the exception of the localization tokens
     * described below.
     *
     * <h3>Localization tokens</h3>
     *
     * The suffix is built on the server, where client language files are not loaded, so any name resolved there would
     * stay in the server's language. To defer localization to the client, the suffix may embed these tokens:
     *
     * <ul>
     * <li>{@code {i:modid:itemname:meta}} - replaced with the display name of
     * {@code new ItemStack(item, 1, meta)}.</li>
     * <li>{@code {ip:modid:itemname:meta}} - the same, but if the display name ends with a closing parenthesis, only
     * the contents of the last pair of parentheses are kept ("Extruder Shape (Rod)" becomes "Rod"). Otherwise the whole
     * name is used.</li>
     * <li>{@code {t:lang.key}} - replaced with the client-side translation of {@code lang.key}.</li>
     * </ul>
     *
     * All three parts of an item token are mandatory; the meta must be a plain integer.
     *
     * <h3>Guarantees</h3>
     *
     * <ul>
     * <li>Text outside of tokens is never modified, including stray curly braces in custom names.</li>
     * <li>A token that cannot be resolved (unknown item, unknown translation key, malformed token) is left in place as
     * raw text. This is the defined behaviour, not an error: an older client receiving a tokenized suffix simply shows
     * {@code {i:gregtech:gt.metaitem.01:32306}} and keeps working.</li>
     * <li>A token carries only an item id and meta, so names that depend on NBT cannot be reproduced through it.</li>
     * <li>The suffix travels as a single string field in the terminal update packet, which caps it at 16383 bytes of
     * UTF-8. Providers are responsible for keeping the suffix reasonably short.</li>
     * </ul>
     *
     * @return suffix string or {@code null}
     */
    String getInterfaceNameSuffix();
}
