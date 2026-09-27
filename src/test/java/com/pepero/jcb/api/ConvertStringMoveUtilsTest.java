package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.exception.convert.ConvertMoveException;
import com.pepero.jcb.api.exception.game.IllegalMoveException;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.core.Chessboard;
import com.pepero.jcb.core.ChessboardUtils;
import com.pepero.jcb.core.MoveGenerator;
import com.pepero.jcb.core.constant.BoardSquares;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.pepero.jcb.core.constant.EncodedPieces.NO_PIECE_CONSTANT;
import static org.junit.jupiter.api.Assertions.*;

public class ConvertStringMoveUtilsTest {
    private static final String SCHOLARS_MATE_FEN = "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4";

    @Test
    @DisplayName("체크메이트 상황이면 true 를 반환해야 한다")
    void shouldShowCheckmateSymbolTrue() {
        Chessboard board = new Chessboard(SCHOLARS_MATE_FEN);
        assertTrue(ConvertStringMoveUtils.shouldShowCheckmateSymbol(board));
    }

    @Test
    @DisplayName("체크메이트가 아니면 false 를 반환해야 한다")
    void shouldShowCheckmateSymbolFalse() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        assertFalse(ConvertStringMoveUtils.shouldShowCheckmateSymbol(board));
    }

    @Test
    @DisplayName("일반적인 폰/나이트 수를 LAN 에서 SAN 으로 정확히 변환해야 한다")
    void toSanStringBasicMoves() {
        Chessboard board = new Chessboard(Chessboard.start_position);

        assertEquals("e4", ConvertStringMoveUtils.toSanString(board, "e2e4"));
        assertEquals("Nf3", ConvertStringMoveUtils.toSanString(board, "g1f3"));
    }

    @Test
    @DisplayName("폰이 캡처하면 파일 문자와 x 가 포함된 SAN 을 반환해야 한다")
    void toSanStringPawnCapture() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        MoveGenerator.makeMove(board, ConvertStringMoveUtils.lanToMoveData(board, "e2e4"));
        MoveGenerator.makeMove(board, ConvertStringMoveUtils.lanToMoveData(board, "d7d5"));

        assertEquals("exd5", ConvertStringMoveUtils.toSanString(board, "e4d5"));
    }

    @Test
    @DisplayName("체크를 유발하는 수는 SAN 뒤에 + 가 붙어야 한다")
    void toSanStringCheckSymbol() {
        Chessboard board = new Chessboard(Chessboard.start_position);

        String san = ConvertStringMoveUtils.parseLanSequenceToSan(board, "e2e4 d7d5 f1b5");
        assertEquals("e4 d5 Bb5+", san.trim());
    }

    @Test
    @DisplayName("체크메이트를 유발하는 수는 SAN 뒤에 # 이 붙어야 한다 (Scholar's mate)")
    void toSanStringCheckmateSymbol() {
        Chessboard board = new Chessboard(Chessboard.start_position);

        String san = ConvertStringMoveUtils.parseLanSequenceToSan(board,
                "e2e4 e7e5 f1c4 b8c6 d1h5 g8f6 h5f7");
        assertEquals("e4 e5 Bc4 Nc6 Qh5 Nf6 Qxf7#", san.trim());
    }

    @Test
    @DisplayName("같은 종류의 기물이 같은 칸으로 갈 수 있으면 파일로 구분(disambiguation)해야 한다")
    void toSanStringDisambiguation() {
        Chessboard board = new Chessboard("4k3/8/8/8/8/8/8/1N1N3K w - - 0 1");

        assertEquals("Nbc3", ConvertStringMoveUtils.toSanString(board, "b1c3"));
        assertEquals("Ndc3", ConvertStringMoveUtils.toSanString(board, "d1c3"));
    }

    @Test
    @DisplayName("킹사이드/퀸사이드 캐슬링은 O-O, O-O-O 로 변환되어야 한다")
    void toSanStringCastling() {
        Chessboard board = new Chessboard("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");

        assertEquals("O-O", ConvertStringMoveUtils.toSanString(board, "e1g1"));
        assertEquals("O-O-O", ConvertStringMoveUtils.toSanString(board, "e1c1"));
    }

    @Test
    @DisplayName("프로모션 수는 =Q, =N 같은 접미사가 붙어야 한다")
    void toSanStringPromotion() {
        Chessboard board = new Chessboard("8/P7/1k6/8/8/8/8/7K w - - 0 1");

        assertEquals("a8=Q", ConvertStringMoveUtils.toSanString(board, "a7a8q"));
        assertEquals("a8=N+", ConvertStringMoveUtils.toSanString(board, "a7a8n"));
    }

    @Test
    @DisplayName("toSanString 호출 후에도 원본 chessboard 상태는 그대로 유지되어야 한다")
    void toSanStringDoesNotMutateBoard() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        String fenBefore = ChessboardUtils.getFen(board);

        ConvertStringMoveUtils.toSanString(board, "e2e4");

        assertEquals(fenBefore, ChessboardUtils.getFen(board));
    }

    @Test
    @DisplayName("MoveInfo, 인코딩된 int 오버로드도 문자열 오버로드와 동일한 SAN 을 반환해야 한다")
    void toSanStringOverloadsAreConsistent() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        int encodedMove = ConvertStringMoveUtils.lanToMoveData(board, "g1f3");

        assertEquals("Nf3", ConvertStringMoveUtils.toSanString(board, "g1f3"));
        assertEquals("Nf3", ConvertStringMoveUtils.toSanString(board, encodedMove));
        assertEquals("Nf3", ConvertStringMoveUtils.toSanString(board, new MoveInfo(encodedMove)));
    }

    @Test
    @DisplayName("인코딩된 수 배열을 SAN 시퀀스 문자열로 변환해야 한다")
    void parseEncodedMoveToSan() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        String fenBefore = ChessboardUtils.getFen(board);

        int e4Move = ConvertStringMoveUtils.lanToMoveData(board, "e2e4");
        MoveGenerator.makeMove(board, e4Move);
        int e5Move = ConvertStringMoveUtils.lanToMoveData(board, "e7e5");
        MoveGenerator.unmakeMove(board, e4Move);

        String san = ConvertStringMoveUtils.parseEncodedMoveToSan(board, new int[]{e4Move, e5Move});

        assertEquals("e4 e5", san.trim());
        assertEquals(fenBefore, ChessboardUtils.getFen(board), "원본 board는 변하지 않아야 함");
    }

    @Test
    @DisplayName("LAN 시퀀스를 SAN 시퀀스로 정확히 변환해야 한다")
    void parseLanSequenceToSan() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        String fenBefore = ChessboardUtils.getFen(board);

        String san = ConvertStringMoveUtils.parseLanSequenceToSan(board, "e2e4 e7e5 g1f3");

        assertEquals("e4 e5 Nf3", san.trim());
        assertEquals(fenBefore, ChessboardUtils.getFen(board), "원본 board는 변하지 않아야 함");
    }

    @Test
    @DisplayName("빈 문자열 LAN 시퀀스는 빈 문자열을 반환해야 한다")
    void parseLanSequenceToSanEmpty() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        assertEquals("", ConvertStringMoveUtils.parseLanSequenceToSan(board, "   "));
    }

    @Test
    @DisplayName("lanToMoveData 는 정상적인 LAN 을 인코딩된 수로 변환해야 한다")
    void lanToMoveDataValid() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        int encodedMove = ConvertStringMoveUtils.lanToMoveData(board, "e2e4");

        assertEquals("e4", ConvertStringMoveUtils.toSanString(board, encodedMove));
    }

    @Test
    @DisplayName("LAN 문자열 길이가 너무 짧거나 길면 ConvertMoveException 이 발생해야 한다")
    void lanToMoveDataInvalidLength() {
        Chessboard board = new Chessboard(Chessboard.start_position);

        assertThrows(ConvertMoveException.class, () -> ConvertStringMoveUtils.lanToMoveData(board, "e2e"));
        assertThrows(ConvertMoveException.class, () -> ConvertStringMoveUtils.lanToMoveData(board, "e2e4e4e4"));
    }

    @Test
    @DisplayName("존재하지 않는 좌표가 포함되면 ConvertMoveException 이 발생해야 한다")
    void lanToMoveDataInvalidSquare() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        assertThrows(ConvertMoveException.class, () -> ConvertStringMoveUtils.lanToMoveData(board, "z9z8"));
    }

    @Test
    @DisplayName("존재하지 않는 프로모션 문자가 포함되면 ConvertMoveException 이 발생해야 한다")
    void lanToMoveDataInvalidPromotionChar() {
        Chessboard board = new Chessboard("8/P7/1k6/8/8/8/8/7K w - - 0 1");
        assertThrows(ConvertMoveException.class, () -> ConvertStringMoveUtils.lanToMoveData(board, "a7a8x"));
    }

    @Test
    @DisplayName("불법수를 lanToMoveData 로 변환하려 하면 IllegalMoveException 이 발생해야 한다")
    void lanToMoveDataIllegalMove() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        assertThrows(IllegalMoveException.class, () -> ConvertStringMoveUtils.lanToMoveData(board, "e2e5"));
    }

    @Test
    @DisplayName("LAN 시퀀스를 MoveInfo 리스트로 변환하고, 원본 board는 변하지 않아야 한다")
    void parseLanSequenceToMoveData() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        String fenBefore = ChessboardUtils.getFen(board);

        List<MoveInfo> moves = ConvertStringMoveUtils.parseLanSequenceToMoveData(board, "e2e4 e7e5 g1f3");

        assertEquals(3, moves.size());
        assertEquals("e2e4", moves.get(0).toLanString());
        assertEquals("e7e5", moves.get(1).toLanString());
        assertEquals("g1f3", moves.get(2).toLanString());
        assertEquals(fenBefore, ChessboardUtils.getFen(board));
    }

    @Test
    @DisplayName("SAN 시퀀스를 MoveInfo 리스트로 변환하고, 원본 board는 변하지 않아야 한다")
    void parseSanSequenceToMoveData() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        String fenBefore = ChessboardUtils.getFen(board);

        List<MoveInfo> moves = ConvertStringMoveUtils.parseSanSequenceToMoveData(board, "e4 e5 Nf3");

        assertEquals(3, moves.size());
        assertEquals("e2e4", moves.get(0).toLanString());
        assertEquals("e7e5", moves.get(1).toLanString());
        assertEquals("g1f3", moves.get(2).toLanString());
        assertEquals(fenBefore, ChessboardUtils.getFen(board));
    }

    @Test
    @DisplayName("빈 문자열 시퀀스는 빈 리스트를 반환해야 한다")
    void parseSequenceToMoveDataEmpty() {
        Chessboard board = new Chessboard(Chessboard.start_position);

        assertTrue(ConvertStringMoveUtils.parseLanSequenceToMoveData(board, "   ").isEmpty());
        assertTrue(ConvertStringMoveUtils.parseSanSequenceToMoveData(board, "").isEmpty());
    }

    @Test
    @DisplayName("시퀀스 도중 기물은 있지만 이동할 수 없는 불법수가 있으면 IllegalMoveException 이 발생해야 한다")
    void parseSequenceToMoveDataIllegalMove() {
        Chessboard lanBoard = new Chessboard(Chessboard.start_position);
        assertThrows(IllegalMoveException.class,
                () -> ConvertStringMoveUtils.parseLanSequenceToMoveData(lanBoard, "e2e4 e7e5 e4e5"));

        Chessboard sanBoard = new Chessboard(Chessboard.start_position);
        assertThrows(IllegalMoveException.class,
                () -> ConvertStringMoveUtils.parseSanSequenceToMoveData(sanBoard, "e4 e5 e5"));
    }

    @Test
    @DisplayName("시퀀스 도중 출발 칸에 기물 자체가 없으면(이미 이동한 칸을 재사용) ConvertMoveException 이 발생해야 한다")
    void parseLanSequenceToMoveDataPieceNotFound() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        assertThrows(ConvertMoveException.class,
                () -> ConvertStringMoveUtils.parseLanSequenceToMoveData(board, "e2e4 e2e4"));
    }

    @Test
    @DisplayName("SAN 을 LAN 으로 정확히 변환해야 한다")
    void toLanStringBasicMoves() {
        Chessboard board = new Chessboard(Chessboard.start_position);

        assertEquals("e2e4", ConvertStringMoveUtils.toLanString(board, "e4"));
        assertEquals("g1f3", ConvertStringMoveUtils.toLanString(board, "Nf3"));
    }

    @Test
    @DisplayName("캡처가 포함된 SAN 을 LAN 으로 정확히 변환해야 한다")
    void toLanStringCapture() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        MoveGenerator.makeMove(board, ConvertStringMoveUtils.lanToMoveData(board, "e2e4"));
        MoveGenerator.makeMove(board, ConvertStringMoveUtils.lanToMoveData(board, "d7d5"));

        assertEquals("e4d5", ConvertStringMoveUtils.toLanString(board, "exd5"));
    }

    @Test
    @DisplayName("O-O, O-O-O 캐슬링 SAN 을 LAN 으로 정확히 변환해야 한다")
    void toLanStringCastling() {
        Chessboard board = new Chessboard("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");

        assertEquals("e1g1", ConvertStringMoveUtils.toLanString(board, "O-O"));
        assertEquals("e1c1", ConvertStringMoveUtils.toLanString(board, "O-O-O"));
    }

    @Test
    @DisplayName("존재하지 않는 SAN(불법수) 을 변환하려 하면 IllegalMoveException 이 발생해야 한다")
    void toLanStringIllegalMove() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        assertThrows(IllegalMoveException.class, () -> ConvertStringMoveUtils.toLanString(board, "Qh5"));
    }

    @Test
    @DisplayName("sanToMoveData 와 lanToMoveData 는 같은 수에 대해 동일한 인코딩 값을 반환해야 한다")
    void sanToMoveDataMatchesLanToMoveData() {
        Chessboard board = new Chessboard(Chessboard.start_position);

        assertEquals(ConvertStringMoveUtils.lanToMoveData(board, "e2e4"),
                ConvertStringMoveUtils.sanToMoveData(board, "e4"));
    }

    @Test
    @DisplayName("SAN 시퀀스를 LAN 시퀀스로 정확히 변환해야 한다")
    void parseSanSequenceToLan() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        String fenBefore = ChessboardUtils.getFen(board);

        String lan = ConvertStringMoveUtils.parseSanSequenceToLan(board, "e4 e5 Nf3");

        assertEquals("e2e4 e7e5 g1f3", lan.trim());
        assertEquals(fenBefore, ChessboardUtils.getFen(board), "원본 board는 변하지 않아야 함");
    }

    @Test
    @DisplayName("빈 문자열 SAN 시퀀스는 빈 문자열을 반환해야 한다")
    void parseSanSequenceToLanEmpty() {
        Chessboard board = new Chessboard(Chessboard.start_position);
        assertEquals("", ConvertStringMoveUtils.parseSanSequenceToLan(board, "   "));
    }

    @Test
    @DisplayName("source/target/promotion 값으로 lanToMoveData 와 동일한 인코딩된 수를 생성해야 한다")
    void parseMoveDataToEncodedMoveMatchesLan() {
        Chessboard board = new Chessboard(Chessboard.start_position);

        int fromLan = ConvertStringMoveUtils.lanToMoveData(board, "e2e4");
        int fromManual = ConvertStringMoveUtils.parseMoveDataToEncodedMove(
                board, BoardSquares.e2, BoardSquares.e4, NO_PIECE_CONSTANT);

        assertEquals(fromLan, fromManual);
    }

    @Test
    @DisplayName("source 또는 target square 가 범위(0~63)를 벗어나면 ConvertMoveException 이 발생해야 한다")
    void parseMoveDataToEncodedMoveInvalidSquare() {
        Chessboard board = new Chessboard(Chessboard.start_position);

        assertThrows(ConvertMoveException.class, () -> ConvertStringMoveUtils.parseMoveDataToEncodedMove(
                board, -1, BoardSquares.e4, NO_PIECE_CONSTANT));
        assertThrows(ConvertMoveException.class, () -> ConvertStringMoveUtils.parseMoveDataToEncodedMove(
                board, BoardSquares.e2, 64, NO_PIECE_CONSTANT));
    }

    @Test
    @DisplayName("프로모션 타입 값이 범위(0~11)를 벗어나면 ConvertMoveException 이 발생해야 한다")
    void parseMoveDataToEncodedMoveInvalidPromotion() {
        Chessboard board = new Chessboard(Chessboard.start_position);

        assertThrows(ConvertMoveException.class, () -> ConvertStringMoveUtils.parseMoveDataToEncodedMove(
                board, BoardSquares.e2, BoardSquares.e4, 12));
    }

    @Test
    @DisplayName("백의 차례일 때 SAN 시퀀스에 '1. e4' 형태로 수 번호가 붙어야 한다")
    void addMoveNumberWhiteTurn() {
        assertEquals("1. e4 e5 2. Nf3",
                ConvertStringMoveUtils.addMoveNumberToSanSequence(1, true, "e4 e5 Nf3"));
    }

    @Test
    @DisplayName("흑의 차례일 때 SAN 시퀀스에 '1... e5' 형태로 수 번호가 붙어야 한다")
    void addMoveNumberBlackTurn() {
        assertEquals("1... e5 2. Nf3 Nc6",
                ConvertStringMoveUtils.addMoveNumberToSanSequence(1, false, "e5 Nf3 Nc6"));
    }

    @Test
    @DisplayName("half-ply 오버로드는 fullMove/turn 오버로드와 동일한 결과를 반환해야 한다")
    void addMoveNumberPlyOverloadMatches() {
        assertEquals(ConvertStringMoveUtils.addMoveNumberToSanSequence(1, true, "e4 e5 Nf3"),
                ConvertStringMoveUtils.addMoveNumberToSanSequence(0, "e4 e5 Nf3"));

        assertEquals(ConvertStringMoveUtils.addMoveNumberToSanSequence(1, false, "e5 Nf3 Nc6"),
                ConvertStringMoveUtils.addMoveNumberToSanSequence(1, "e5 Nf3 Nc6"));
    }

    @Test
    @DisplayName("null 이나 빈 문자열이 들어오면 빈 문자열을 반환해야 한다")
    void addMoveNumberEmpty() {
        assertEquals("", ConvertStringMoveUtils.addMoveNumberToSanSequence(1, true, null));
        assertEquals("", ConvertStringMoveUtils.addMoveNumberToSanSequence(1, true, "   "));
    }

    @Test
    @DisplayName("removeMoveNumberFromSanSequence 는 addMoveNumberToSanSequence 의 역변환(round trip)이어야 한다")
    void removeMoveNumberRoundTrip() {
        String numberedWhite = ConvertStringMoveUtils.addMoveNumberToSanSequence(1, true, "e4 e5 Nf3 Nc6");
        assertEquals("e4 e5 Nf3 Nc6", ConvertStringMoveUtils.removeMoveNumberFromSanSequence(numberedWhite));

        String numberedBlack = ConvertStringMoveUtils.addMoveNumberToSanSequence(1, false, "e5 Nf3 Nc6");
        assertEquals("e5 Nf3 Nc6", ConvertStringMoveUtils.removeMoveNumberFromSanSequence(numberedBlack));
    }

    @Test
    @DisplayName("null 이나 빈(공백) 문자열이 들어오면 빈 문자열을 반환해야 한다")
    void removeMoveNumberEmpty() {
        assertEquals("", ConvertStringMoveUtils.removeMoveNumberFromSanSequence(null));
        assertEquals("", ConvertStringMoveUtils.removeMoveNumberFromSanSequence("   "));
    }

    @Test
    @DisplayName("LAN 문자열에서 source/target 좌표를 정확히 추출해야 한다")
    void parseLanSquares() {
        assertArrayEquals(new String[]{"e2", "e4"}, ConvertStringMoveUtils.parseLanSquares("e2e4"));
        assertArrayEquals(new String[]{"e7", "e8"}, ConvertStringMoveUtils.parseLanSquares("e7e8q"));
    }

    @Test
    @DisplayName("SAN 시퀀스의 기물 알파벳이 유니코드 기호로 치환되어야 한다")
    void toUnicodePieces() {
        assertEquals("e4 e5 \u2658f3", ConvertStringMoveUtils.toUnicodePieces(true, "e4 e5 Nf3"));
    }

    @Test
    @DisplayName("백/흑 차례가 번갈아가며 색이 다른 유니코드 기호로 치환되어야 한다")
    void toUnicodePiecesAlternatesColor() {
        assertEquals("e4 \u265Ef6 \u2658f3", ConvertStringMoveUtils.toUnicodePieces(true, "e4 Nf6 Nf3"));
    }

    @Test
    @DisplayName("null 이나 빈 문자열이 들어오면 빈 문자열을 반환해야 한다")
    void toUnicodePiecesEmpty() {
        assertEquals("", ConvertStringMoveUtils.toUnicodePieces(true, null));
        assertEquals("", ConvertStringMoveUtils.toUnicodePieces(true, "   "));
    }
}