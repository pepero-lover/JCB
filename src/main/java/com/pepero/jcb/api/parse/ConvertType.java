package com.pepero.jcb.api.parse;

import com.pepero.jcb.api.exception.ConvertMoveException;

/**
 * Convert type for distinguishing converting which to which. <br>
 * Used on {@link ConvertMoveException}
 */
public enum ConvertType {
    LAN,   // convert LAN to another (UCI to another)
    SAN,   // convert SAN to another (SAN to another)
    MANUAL // convert source square, target square, promotion type to another
}
