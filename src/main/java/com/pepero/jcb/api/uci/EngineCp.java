package com.pepero.jcb.api.uci;

/**
 * Stores engine cp info
 *
 * @param cp cp data (if <b>isMate</b> is true, it's mate in N value) <br>
 *           It is always based on white.
 * @param isMate is mate value
 */
public record EngineCp(
        int cp,
        boolean isMate
) {
    @Override
    public String toString() {
        if(!isMate) {
            double eval = cp / 100.0;
            return (eval > 0 ? "+" : "") + eval;
        } else {
            if(cp >= 0) {
                return "+M" + cp;
            } else {
                return "-M" + (cp * -1);
            }
        }
    }
}
