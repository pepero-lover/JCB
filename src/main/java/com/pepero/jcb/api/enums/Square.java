package com.pepero.jcb.api.enums;

import com.pepero.jcb.api.exception.SquareConvertException;

public enum Square {
    a8, b8, c8, d8, e8, f8, g8, h8,
    a7, b7, c7, d7, e7, f7, g7, h7,
    a6, b6, c6, d6, e6, f6, g6, h6,
    a5, b5, c5, d5, e5, f5, g5, h5,
    a4, b4, c4, d4, e4, f4, g4, h4,
    a3, b3, c3, d3, e3, f3, g3, h3,
    a2, b2, c2, d2, e2, f2, g2, h2,
    a1, b1, c1, d1, e1, f1, g1, h1,;

    private static final Square[] VALUES = values();

    /**
     * Find square from index
     *
     * @param index square index
     * @return found square
     */
    public static Square fromIndex(int index){
        if (index < 0 || index >= 64) {
            throw new SquareConvertException(index);
        }
        return VALUES[index];
    }

    /**
     * Find square from string
     *
     * @param square string square like "e4", "a7"
     * @return found square
     */
    public static Square fromString(String square){
        if (square == null || square.length() != 2){
            throw new SquareConvertException(square);
        }

        try {
            return valueOf(square.toLowerCase());
        } catch (IllegalArgumentException e){
            throw new SquareConvertException(square);
        }
    }

    /**
     * Get Index on this square
     *
     * @return index on this square
     */
    public int getIndex() {
        return ordinal();
    }

    /**
     * Get file on this square (type : char)
     * <p>
     * Example : e5 -> e || d8 -> d
     *
     * @return file (type : int)
     */
    public char getFile() {
        return (char) ('a' + (this.ordinal() % 8));
    }


    /**
     * Get file on this square (type : int)
     * <p>
     * Example : e5 -> e -> 5 || d8 -> d -> 4
     *
     * @return file (type : int)
     */
    public int getFileInteger() {
        return (this.ordinal() % 8) + 1;
    }

    /**
     * Get rank on this square
     * <p>
     * Example : e4 -> 4 || g6 -> 6
     *
     * @return rank
     */
    public int getRank() {
        return 8 - (this.ordinal() / 8);
    }

    /**
     * Get whether this square is light square
     * <p>
     * Example : <br>
     * e2 -> light square -> true <br>
     * f8 -> dark square -> false <br>
     *
     * @return whether this square is light square
     */
    public boolean isLightSquare() {
        int index = this.ordinal();
        int rank = index / 8;
        int file = index % 8;
        return (rank + file) % 2 == 0;
    }

    @Override
    public String toString() {
        return String.valueOf(getFile()) + getRank();
    }
}
