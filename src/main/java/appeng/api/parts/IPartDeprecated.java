package appeng.api.parts;

import javax.annotation.Nullable;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Mark a part with this interface to seamlessly transition it from a deprecated part to a supported part. Methods can
 * return null to indicate that the part should be removed entirely.
 */
public interface IPartDeprecated {

    /**
     * transformPart - takes the "def:" tag and transforms it. The returned tag may be a copy of the tag passed in, or
     * it could be transformed in-place.
     * 
     * @param def the def data for the deprecated part
     * @return the transformed def data for the replacement part, or null if part is to be deleted
     */
    @Nullable
    NBTTagCompound transformPart(NBTTagCompound def);

    /**
     * transformNBT - takes the "extra:" tag transforms it. The returned tag may be a copy of the tag passed in, or it
     * could be transformed in-place.
     * 
     * @param extra the extra data for the deprecated part
     * @return the transformed extra data for the replacement part, or null if part is to be deleted
     */
    @Nullable
    NBTTagCompound transformNBT(NBTTagCompound extra);
}
