package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.parse.pgn.MoveAnnotation;

public record MoveDataDTO(
        long id,                  // node uuid
        String fen,               // now fen
        MoveInfo moveData,        // move data
        MoveAnnotation annotation // move annotation
) {
    @Override
    public String toString() {
        if(moveData == null) return "";
        return moveData.toString();
    }
}
