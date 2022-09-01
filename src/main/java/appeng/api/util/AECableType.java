/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2013 AlgorithmX2
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package appeng.api.util;

public enum AECableType {
    /**
     * No Cable present.
     */
    NONE,

    /**
     * Connections to this block should render as glass.
     */
    GLASS,

    /**
     * Connections to this block should render as covered.
     */
    COVERED,

    /**
     * Connections to this block should render as smart.
     */
    SMART,

    /**
     * Dense Cable, represents a tier 2 block that can carry 32 channels.
     */
    DENSE,

    /**
     * Dense Covered Cable, represents a tier 2 block that can carry 32 channels that should render as covered.
     */
    DENSE_COVERED,

    /**
     * Ultra Dense Cable, represents a tier 3 block that can carry 128 channels.
     */
    ULTRA_DENSE,

    /**
     * Ultra Dense Cable, represents a tier 3 block that can carry 128 channels and renders as smart (4 channels per line).
     */
    ULTRA_DENSE_SMART
}
