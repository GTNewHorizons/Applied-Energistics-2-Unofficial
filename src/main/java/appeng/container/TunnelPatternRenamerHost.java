package appeng.container;

import java.util.Objects;

import net.minecraft.tileentity.TileEntity;

import appeng.api.implementations.guiobjects.IGuiItemObject;
import appeng.api.parts.IPart;

/** A validated world or item GUI anchor for the Tunnel Pattern renamer. */
public final class TunnelPatternRenamerHost {

    private final Object anchor;

    public TunnelPatternRenamerHost(final TileEntity tile) {
        this.anchor = Objects.requireNonNull(tile);
    }

    public TunnelPatternRenamerHost(final IPart part) {
        this.anchor = Objects.requireNonNull(part);
    }

    public TunnelPatternRenamerHost(final IGuiItemObject item) {
        this.anchor = Objects.requireNonNull(item);
    }

    public static boolean supports(final Object anchor) {
        return anchor instanceof TunnelPatternRenamerHost || anchor instanceof TileEntity
                || anchor instanceof IPart
                || anchor instanceof IGuiItemObject;
    }

    public static TunnelPatternRenamerHost from(final Object anchor) {
        if (anchor instanceof TunnelPatternRenamerHost host) return host;
        if (anchor instanceof IPart part) return new TunnelPatternRenamerHost(part);
        if (anchor instanceof TileEntity tile) return new TunnelPatternRenamerHost(tile);
        if (anchor instanceof IGuiItemObject item) return new TunnelPatternRenamerHost(item);
        throw new IllegalArgumentException("Not a world or item GUI anchor: " + anchor);
    }

    public Object getAnchor() {
        return this.anchor;
    }
}
