package appeng.api.parts;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Mark a part with this interface to seamlessly transition it from a deprecated part to a supported part.
 */
public interface IPartDeprecated {

    /**
     * transformPart
     * 
     * @param def the def data for the part
     * @return the transformed def data for the replacement part
     */
    NBTTagCompound transformPart(NBTTagCompound def);

    /**
     * transformNBT
     * 
     * @param extra the extra data for the part
     * @return the transformed extra data for the replacement part
     */
    NBTTagCompound transformNBT(NBTTagCompound extra);
}
