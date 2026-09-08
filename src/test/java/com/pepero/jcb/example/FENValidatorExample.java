package com.pepero.jcb.example;

import com.pepero.jcb.api.exception.FENConvertException;
import com.pepero.jcb.api.parse.FENValidator;
import com.pepero.jcb.core.GameVariant;

public class FENValidatorExample {
    public static void main(String[] args) {
        try {
            FENValidator.validate(
                    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQ1BNR w KQkq - 0 1", // wrong position (king missing)
                    false, // is chess 960 flag
                    GameVariant.STANDARD // game variant
            );
        } catch (FENConvertException e) {
            System.err.println("FEN Convert Error!!");
            System.err.println("Error type : " + e.getErrorType());

            // if you want to know error types, go to com.pepero.jcb.api.exception.type.FENErrorType.
        }

        System.out.println();

        try {
            FENValidator.validate(
                    "8/4k3/3q4/8/3K4/8/8/8 b - - 0 1", // wrong position (king can be captured)
                    false, // is chess 960 flag
                    GameVariant.STANDARD // game variant
            );
        } catch (FENConvertException e) {
            System.err.println("FEN Convert Error!!");
            System.err.println("Error type : " + e.getErrorType());
        }
    }
}
