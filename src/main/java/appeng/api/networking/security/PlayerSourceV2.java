/*
 * The MIT License (MIT) Copyright (c) 2013 AlgorithmX2 Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions: The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software. THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.networking.security;

import net.minecraft.entity.player.EntityPlayer;

/**
 * Represents an action initiated by a player.
 */
public class PlayerSourceV2 implements BaseActionSourceV2 {

    /** The player responsible for the action. */
    private final EntityPlayer player;

    /** The machine or interface used to perform the action. */
    private final IActionHost actionHost;

    /**
     * Creates a new player action source.
     *
     * @param player     The player performing the action.
     * @param actionHost The machine or interface used.
     */
    public PlayerSourceV2(final EntityPlayer player, final IActionHost actionHost) {
        this.player = player;
        this.actionHost = actionHost;
    }

    @Override
    public boolean isPlayer() {
        return true;
    }

    public EntityPlayer getPlayer() {
        return this.player;
    }

    public IActionHost getActionHost() {
        return this.actionHost;
    }
}
