package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.ChessClock;
import com.pepero.jcb.api.book.PolyglotHashUtils;
import com.pepero.jcb.api.dto.MoveDataDTO;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.dto.MoveNodeDTO;
import com.pepero.jcb.api.enums.*;
import com.pepero.jcb.api.exception.*;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.core.constant.BoardSquares;
import com.pepero.jcb.core.*;
import com.pepero.jcb.core.encode.EncodeMove;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class ChessGameTest {
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String SCHOLARS_MATE_FEN = "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4";
    private static final String STALEMATE_FEN = "7k/5K2/6Q1/8/8/8/8/8 b - - 0 1";

    @Test
    @DisplayName("초기 FEN 문자열을 정확히 반환해야 한다")
    void getFEN() {
        ChessGame chessGame = ChessGame.startPosition();

        assertEquals(START_FEN, chessGame.getFEN());
    }

    @Test
    @DisplayName("LAN 문자열로 수를 두면 상태가 변해야 한다")
    void makeMove() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        assertFalse(chessGame.isEmpty(Square.e4));
        assertTrue(chessGame.isEmpty(Square.e2));
    }

    @Test
    @DisplayName("불법수를 두면 IllegalMoveException 이 터져야 한다")
    void testIllegalMove() {
        ChessGame chessGame = ChessGame.startPosition();

        assertThrows(IllegalMoveException.class, () -> chessGame.makeMoveLan("e2f3"));
    }

    @Test
    @DisplayName("Undo 를 하면 기보가 삭제되고 보드가 원상복구되어야 한다")
    void unmakeMove() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        assertEquals(1, chessGame.getMoveHistory().size());

        MoveInfo undoneMove = chessGame.unmakeMove();
        assertEquals(0, chessGame.getMoveHistory().size());
        assertEquals(START_FEN, chessGame.getFEN());
        assertNotNull(undoneMove);
    }

    @Test
    @DisplayName("아무 수도 두지 않고 무르기를 하면 예외가 발생해야 한다")
    void unmakeMoveEmpty() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(EmptyMoveUndoException.class, chessGame::unmakeMove);
    }

    @Test
    @DisplayName("턴이 번갈아가며 바뀌어야 한다")
    void getTurn() {
        ChessGame chessGame = ChessGame.startPosition();
        assertTrue(chessGame.getTurn());

        chessGame.makeMoveLan("e2e4");
        assertFalse(chessGame.getTurn());
    }

    @Test
    @DisplayName("기물을 잡으면 잡힌 기물 목록에 추가되어야 한다")
    void getCapturedPieces() {
        ChessGame chessGame = ChessGame.startPosition();

        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("d7d5");
        chessGame.makeMoveLan("e4d5");

        Map<PieceType, Integer> capturedByWhite = chessGame.getCapturedPieces(true);
        assertEquals(1, capturedByWhite.get(PieceType.PAWN));
    }

    @Test
    @DisplayName("기물 점수가 기물 이득에 따라 변해야 한다")
    void getPieceScore() {
        ChessGame chessGame = ChessGame.startPosition();
        assertEquals(0, chessGame.getPieceScore());

        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("d7d5");
        chessGame.makeMoveLan("e4d5");

        assertEquals(1, chessGame.getPieceScore());
    }

    @Test
    @DisplayName("특정 칸에 있는 기물을 정확히 가져와야 한다")
    void getPieceOnSquare() {
        ChessGame chessGame = ChessGame.startPosition();
        assertEquals(Piece.WHITE_QUEEN, chessGame.getPieceOnSquare(Square.d1));
        assertEquals(Piece.BLACK_ROOK, chessGame.getPieceOnSquare(Square.h8));
        assertEquals(Piece.NONE, chessGame.getPieceOnSquare(Square.e4));
    }

    @Test
    @DisplayName("초기 상태의 전체 합법수는 정확히 20개여야 한다")
    void getLegalMoves() {
        ChessGame chessGame = ChessGame.startPosition();
        List<MoveInfo> moves = chessGame.getLegalMoves();
        assertEquals(20, moves.size());
    }

    @Test
    @DisplayName("Source Square 에서 합법수만 필터링해서 가져와야 한다")
    void getLegalMovesForSource() {
        ChessGame chessGame = ChessGame.startPosition();
        List<MoveInfo> e2Moves = chessGame.getLegalMovesForSource(Square.e2);

        assertEquals(2, e2Moves.size());
        assertEquals(Square.e3, e2Moves.get(0).targetSquare());
        assertEquals(Square.e4, e2Moves.get(1).targetSquare());
    }

    @Test
    @DisplayName("Target Square 에서 합법수만 필터링해서 가져와야 한다")
    void getLegalMovesForTarget() {
        ChessGame chessGame = ChessGame.startPosition();
        List<MoveInfo> e2Moves = chessGame.getLegalMovesForTarget(Square.e4);

        assertEquals(1, e2Moves.size());
        assertEquals(Square.e4, e2Moves.getFirst().targetSquare());
    }

    @Test
    @DisplayName("초기 보드의 기물 개수는 정확히 32개여야 한다")
    void getBoardStateMap() {
        ChessGame chessGame = ChessGame.startPosition();
        Map<Square, Piece> boardMap = chessGame.getBoardStateMap();
        assertEquals(32, boardMap.size());
    }

    @Test
    @DisplayName("체크메이트 상태를 정확히 판별해야 한다")
    void isCheckmate() {
        ChessGame chessGame = ChessGame.fromFEN(SCHOLARS_MATE_FEN);
        assertTrue(chessGame.isCheck());
        assertTrue(chessGame.isCheckmate());
        assertEquals(GameOverReason.CHECKMATE, chessGame.isGameOver());
    }

    @Test
    @DisplayName("체크 하는 기물을 정확히 봐야 한다.")
    void getChecker() {
        Chessboard chessboard = new Chessboard(SCHOLARS_MATE_FEN);

        int val = ChessboardUtils.getChecker(chessboard);

        assertEquals(BoardSquares.f7, val & 0x3f);
        assertEquals(1, val >>> 12 & 3);
    }

    @Test
    @DisplayName("스테일메이트 상태를 정확히 판별해야 한다")
    void isStalemate() {
        ChessGame chessGame = ChessGame.fromFEN(STALEMATE_FEN);
        assertFalse(chessGame.isCheck());
        assertTrue(chessGame.isStalemate());
        assertEquals(GameOverReason.STALEMATE, chessGame.isGameOver());
    }

    @Test
    @DisplayName("3회 동형 반복 무승부를 판별해야 한다")
    void isThreefoldRepetition() {
        ChessGame chessGame = ChessGame.startPosition();

        for (int i = 0; i < 2; i++) {
            chessGame.makeMoveLan("g1f3");
            chessGame.makeMoveLan("g8f6");
            chessGame.makeMoveLan("f3g1");
            chessGame.makeMoveLan("f6g8");
        }
        chessGame.makeMoveLan("g1f3");
        chessGame.makeMoveLan("g8f6");

        assertTrue(chessGame.canClaimDraw());
        assertEquals(GameOverReason.THREEFOLD_CLAIM, chessGame.isGameOver(true));

        chessGame.makeMoveLan("f3g1");
        chessGame.makeMoveLan("f6g8");

        for (int i = 0; i < 3; i++) {
            chessGame.makeMoveLan("g1f3");
            chessGame.makeMoveLan("g8f6");
            chessGame.makeMoveLan("f3g1");
            chessGame.makeMoveLan("f6g8");
        }

        assertEquals(GameOverReason.FIVEFOLD, chessGame.isGameOver(false));
    }

    @Test
    @DisplayName("LAN 을 SAN 으로 정상 변환해야 한다")
    void toSan() {
        ChessGame chessGame = ChessGame.startPosition();
        assertEquals("e4", chessGame.toSan("e2e4"));
        assertEquals("Nf3", chessGame.toSan("g1f3"));

        assertEquals("e4 e5 Nf3", chessGame.toSan("e2e4 e7e5 g1f3"));
    }

    @Test
    @DisplayName("SAN 을 LAN 으로 정상 변환해야 한다")
    void toLan() {
        ChessGame chessGame = ChessGame.startPosition();

        assertEquals("e2e4", chessGame.toLanString("e4"));
        assertEquals("g1f3", chessGame.toLanString("Nf3"));

        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("d7d5");

        assertEquals("e4d5", chessGame.toLanString("exd5"));
    }

    @Test
    @DisplayName("되돌리기와 다시 되돌리기가 정상 작동 해야 한다.")
    void undoRedo() {
        ChessGame chessGame = ChessGame.startPosition();

        String start = chessGame.getFEN();

        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.makeMoveLan("g1f3");

        chessGame.unmakeMove();
        chessGame.unmakeMove();

        chessGame.remakeMove();
        chessGame.remakeMove();
        chessGame.unmakeMove();
        chessGame.unmakeMove();
        chessGame.unmakeMove();

        assertEquals(start, chessGame.getFEN());
    }

    @Test
    @DisplayName("바리에이션이 정확히 작성되어야 한다.")
    void variation() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.unmakeMove();

        chessGame.makeMoveLan("d7d5");
        chessGame.unmakeMove();

        chessGame.remakeMove(1);

        ChessGame test = ChessGame.startPosition();
        test.makeMoveLan("e2e4");
        test.makeMoveLan("d7d5");

        assertEquals(chessGame.getFEN(), test.getFEN());
    }

    @Test
    @DisplayName("스퀘어가 밝은 칸인지 어두운 칸인지 판별해야 한다.")
    void isLightSquare() {
        assertTrue(Square.e2.isLightSquare());
        assertFalse(Square.f8.isLightSquare());
    }

    @Test
    @DisplayName("FEN 이 정확히 작성되어야 한다.")
    void fenAdvanced(){
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.makeMoveLan("g1f3");
        chessGame.makeMoveLan("g8f6");
        chessGame.makeMoveLan("b1c3");
        chessGame.makeMoveLan("b8c6");
        chessGame.makeMoveLan("f1c4");
        chessGame.makeMoveLan("f8c5");
        chessGame.makeMoveLan("e1g1");
        chessGame.makeMoveLan("e8g8");

        assertEquals("r1bq1rk1/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQ1RK1 w - - 8 6",
                chessGame.getFEN());
    }

    @Test
    @DisplayName("PGN을 정확히 파싱하고 다시 동일한 텍스트로 내보낼 수 있어야 한다.")
    void pgnConvert() {
        ChessGame chessGame = ChessGame.startPosition();
        String pgnString = """
        \uFEFF[Event "Variation Stress Test"]
        [Result "*"]

        1.e4(1.d4{Queen's Pawn}Nf6 2.c4(2.Nf3 d5)2...e6)1...e5 2.Nf3(2.f4{King's Gambit}exf4 3.Nf3)2...Nc6 3.Bb5(3 .Bc4 Bc5(3...Nf6 4.d3)4.c3)3...a6$1 4.Ba4(4.Bxc6 dxc6 5.0-0(5.d3 f6))4...Nf6 *""";

        chessGame.loadPGN(pgnString);
    }

    @Test
    @DisplayName("Chess 960 에서 e1h1 입력시 정상적으로 파싱되어야 한다")
    public void testChess960Castling() {
        Chessboard board = new Chessboard("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1",
                true);

        int encodedMove = ConvertStringMoveUtils.lanToMoveData(board, "e1h1");

        assertTrue(EncodeMove.getMoveCastling(encodedMove));
        assertEquals(BoardSquares.h1, EncodeMove.getMoveTarget(encodedMove));
    }

    @Test
    @DisplayName("메인 라인 노드 및 노드 아이디를 통한 체스 보드가 잘 불러와져야 한다.")
    public void jumpToNode() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.makeMoveLan("g1f3");
        String fen1 = chessGame.getFEN();
        long uuid1 = chessGame.getCurrentNodeId();

        chessGame.makeMoveLan("b8c6");
        chessGame.unmakeMove();
        chessGame.makeMoveLan("g8f6");
        String fen2 = chessGame.getFEN();
        long uuid2 = chessGame.getCurrentNodeId();

        // e4 e5 Nf3 Nc6 (Nf6)

        chessGame.jumpToNode(uuid1);
        assertEquals(fen1, chessGame.getFEN());

        chessGame.jumpToNode(uuid2);
        assertEquals(fen2, chessGame.getFEN());
    }

    @Test
    @DisplayName("중첩 변이 및 평가 기호 통합 파싱/NAG 분리가 되어야 한다")
    void testMultiversePGNTreeStructure() {
        String multiversePgn = """
                \uFEFF[Event "Variation Stress Test"]
                [Result "*"]

                1.e4!!(1.d4!?{Queen's Pawn}Nf6 2.c4(2.Nf3 d5)2...e6)1...e5?? 2.Nf3!?(2.f4{King's Gambit}exf4 3.Nf3)2...Nc6?! 3.Bb5(3 .Bc4 Bc5(3...Nf6 4.d3)4.c3)3...a6$1 4.Ba4(4.Bxc6 dxc6 5.0-0(5.d3 f6))4...Nf6 *""";

        ChessGame chessGame = ChessGame.startPosition();
        chessGame.loadPGN(multiversePgn);
        MoveNodeDTO root = chessGame.getRootNode();

        assertNotNull(root, "루트 노드는 생성되어야 합니다.");

        MoveNodeDTO move1_e4 = root.children().getFirst();
        assertEquals("e4", move1_e4.san());
        assertEquals("$3", move1_e4.annotation().nag(), "!! 기호는 $3으로 매핑되어야 합니다.");

        MoveNodeDTO var1_d4 = root.children().get(1);
        assertEquals("d4", var1_d4.san());
        assertEquals("$5", var1_d4.annotation().nag(), "!? 기호는 $5로 매핑되어야 합니다.");

        MoveNodeDTO move1_e5 = move1_e4.children().getFirst();
        assertEquals("e5", move1_e5.san());
        assertEquals("$4", move1_e5.annotation().nag(), "?? 기호는 $4로 매핑되어야 합니다.");

        MoveNodeDTO move2_Nf3 = move1_e5.children().getFirst();
        assertEquals("Nf3", move2_Nf3.san());
        assertEquals("$5", move2_Nf3.annotation().nag());

        MoveNodeDTO move2_Nc6 = move2_Nf3.children().getFirst();
        MoveNodeDTO move3_Bb5 = move2_Nc6.children().getFirst();
        MoveNodeDTO move3_a6 = move3_Bb5.children().getFirst();
        assertEquals("a6", move3_a6.san());
        assertEquals("$1", move3_a6.annotation().nag(), "$1 기호는 그대로 저장되어야 합니다.");
    }

    @Test
    @DisplayName("PGN 주석 내 [%clk] 시간 태그 파싱을 정확히 해야 한다")
    void testPGNClockParsingAndExporting() {
        String inputPgn = """
                [Event "Clock Stress Test"]
                [Result "*"]

                1.e4 {[%clk 0:05:00]} 1...e5 {Black responds [%clk 0:04:58]} 2.Nf3!! {[%clk 0:04:45]} (2.f4 {King's Gambit [%clk 0:04:50]} exf4 {[%clk 0:04:48]}) 2...Nc6 {[%clk 0:04:55]} *""";

        ChessGame chessGame = ChessGame.startPosition();

        chessGame.loadPGN(inputPgn);
        MoveNodeDTO root = chessGame.getRootNode();

        assertNotNull(root);
        assertFalse(root.children().isEmpty(), "루트의 자식이 있어야 합니다.");

        MoveNodeDTO move1_e4 = root.children().getFirst();
        assertEquals("e4", move1_e4.san());
        assertEquals("0:05:00", move1_e4.annotation().clk(), "e4의 clk 값이 정확히 매핑되어야 합니다.");
        assertNull(move1_e4.annotation().comment(), "순수 주석이 없으므로 null 이어야 합니다.");

        MoveNodeDTO move1_e5 = move1_e4.children().getFirst();
        assertEquals("e5", move1_e5.san());
        assertEquals("0:04:58", move1_e5.annotation().clk());
        assertEquals("Black responds", move1_e5.annotation().comment(), "주석 텍스트에서 clk 태그만 깔끔하게 제거되어야 합니다.");

        MoveNodeDTO move2_Nf3 = move1_e5.children().getFirst();
        assertEquals("Nf3", move2_Nf3.san());
        assertEquals("$3", move2_Nf3.annotation().nag(), "이전의 !! 기호 분리 로직도 깨지지 않고 $3으로 유지되어야 합니다.");
        assertEquals("0:04:45", move2_Nf3.annotation().clk());

        assertTrue(move1_e5.children().size() > 1, "2수 백에는 변이 라인이 존재해야 합니다.");
        MoveNodeDTO var2_f4 = move1_e5.children().get(1);
        assertEquals("f4", var2_f4.san());
        assertEquals("0:04:50", var2_f4.annotation().clk());
        assertEquals("King's Gambit", var2_f4.annotation().comment());

        String exportedPgn = chessGame.getPGN();

        assertTrue(exportedPgn.contains("e4 {[%clk 0:05:00]}"));
        assertTrue(exportedPgn.contains("e5 {[%clk 0:04:58] Black responds}"));
        assertTrue(exportedPgn.contains("Nf3 $3 {[%clk 0:04:45]}"));
        assertTrue(exportedPgn.contains("f4 {[%clk 0:04:50] King's Gambit}"));
    }

    @Test
    @DisplayName("외부에서 수동으로 입력한 시계 데이터가 PGN에 올바르게 저장되는지 검증")
    void testManualClockSetting() {
        ChessGame chessGame = ChessGame.startPosition();

        chessGame.makeMoveLan("e2e4");
        chessGame.setCurrentMoveClock(0, 4, 55);

        chessGame.makeMoveLan("e7e5");
        chessGame.setCurrentMoveClock("0:04:52");

        MoveNodeDTO root = chessGame.getRootNode();
        MoveNodeDTO move1 = root.children().getFirst();
        MoveNodeDTO move2 = move1.children().getFirst();

        assertEquals("0:04:55", move1.annotation().clk());
        assertEquals("0:04:52", move2.annotation().clk());

        String pgn = chessGame.getPGN();
        assertTrue(pgn.contains("e4 {[%clk 0:04:55]}"));
        assertTrue(pgn.contains("e5 {[%clk 0:04:52]}"));
    }

    @Test
    @DisplayName("크레이지하우스: API 메서드(makeDropMove)로 포켓의 기물을 드랍할 수 있어야 한다")
    void testMakeDropMoveAPI() {
        // 백 포켓에 Q, 흑 포켓에 p 가 있는 상태
        String crazyFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[Qp] w KQkq - 0 1";
        ChessGame chessGame = ChessGame.fromFEN(crazyFen, GameVariant.CRAZY_HOUSE);

        // API 메서드를 이용한 퀸 드랍
        chessGame.makeDropMove(PieceType.QUEEN, Square.e4);

        assertEquals(Piece.WHITE_QUEEN, chessGame.getPieceOnSquare(Square.e4));
        assertEquals(0, chessGame.getCapturedPieces(true).getOrDefault(PieceType.QUEEN, 0), "백의 퀸이 포켓에서 소모되어 0개가 되어야 합니다.");
    }

    @Test
    @DisplayName("크레이지하우스: 문자열로 드랍 이동이 정상 파싱 및 처리되어야 한다")
    void testDropMoveFromString() {
        String crazyFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[P] w KQkq - 0 1";
        ChessGame chessGame = ChessGame.fromFEN(crazyFen, GameVariant.CRAZY_HOUSE);

        chessGame.makeMoveLan("P@e4");

        assertEquals(Piece.WHITE_PAWN, chessGame.getPieceOnSquare(Square.e4));
        assertTrue(chessGame.getMoveHistory().getFirst().isDrop(), "히스토리에 기록된 DTO의 isDrop 플래그가 true여야 합니다.");
    }

    @Test
    @DisplayName("크레이지하우스: 폰을 1랭크나 8랭크에 드랍하려고 하면 예외가 발생해야 한다")
    void testIllegalPawnDrop() {
        String crazyFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[P] w KQkq - 0 1";
        ChessGame chessGame = ChessGame.fromFEN(crazyFen, GameVariant.CRAZY_HOUSE);

        assertThrows(IllegalMoveException.class, () -> chessGame.makeMoveLan("P@e8"), "8랭크 폰 드랍은 불법수입니다.");
        assertThrows(IllegalMoveException.class, () -> chessGame.makeMoveLan("P@e1"), "1랭크 폰 드랍은 불법수입니다.");
    }

    @Test
    @DisplayName("크레이지하우스: 포켓 기물이 기물 점수에 정상적으로 합산되어야 한다")
    void testPocketPieceScore() {
        String fen = "8/8/8/8/8/8/8/k6K[Q] w - - 0 1";
        ChessGame chessGame = ChessGame.fromFEN(fen, GameVariant.CRAZY_HOUSE);

        assertEquals(9, chessGame.getPieceScore());
    }

    @Test
    @DisplayName("크레이지하우스: 포켓에 기물이 있으면 기물 부족 무승부가 발생하지 않아야 한다")
    void testPocketPreventsInsufficientMaterial() {
        String fen = "8/8/8/8/8/8/8/k6K[P] w - - 0 1";
        ChessGame chessGame = ChessGame.fromFEN(fen, GameVariant.CRAZY_HOUSE);

        assertFalse(chessGame.isInsufficientMaterial(), "포켓에 기물이 있으므로 기물 부족이 아닙니다.");
        assertNotEquals(GameOverReason.INSUFFICIENT_MATERIAL, chessGame.isGameOver());
    }

    @Test
    @DisplayName("Polyglot 이 제대로 생성되어야 한다")
    void polyglot() {
        assertEquals("463b96181691fc9c",
                Long.toHexString(PolyglotHashUtils.getPolyglotHash(new Chessboard(Chessboard.start_position))));
    }

    @Test
    @DisplayName("Move Generator 가 정상 작동 해야한다")
    void moveGenerating() {
        Chessboard chessboard = new Chessboard("r1b2rk1/p3pp1p/n1pp1np1/8/qp1PP3/5PN1/PPPQ2PP/R3KB1R w KQ - 0 13");

        int[] move_list = new int[255];
        int move_count = MoveGenerator.generateMoves(chessboard, move_list);
        for(int i=0;i<move_count;i++) {
            int move = move_list[i];
            if(EncodeMove.getMoveSource(move) == BoardSquares.e1
                && EncodeMove.getMoveTarget(move) == BoardSquares.g1) fail();
        }
    }

    @Test
    @DisplayName("getMainlineData 에서 메인라인 데이터들이 정상적으로 생성되어야 한다")
    void getMainlineData() {
        ChessGame chessGame = ChessGame.startPosition();ChessGame.startPosition();

        chessGame.makeMoveSanAll("e4 e5 Nf3 Nc6 Bc4 Bc5");

        assertEquals(List.of(
                "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
                "rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2",
                "rnbqkbnr/pppp1ppp/8/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R b KQkq - 1 2",
                "r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3",
                "r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3",
                "r1bqk1nr/pppp1ppp/2n5/2b1p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4"),
                chessGame.getMainlineData().stream()
                        .map(MoveDataDTO::fen)
                        .collect(Collectors.toList()));
    }

    @Test
    @DisplayName("Full move 를 설정 했을 때 ply 와 같이 써지지 않아야 한다")
    void fullMovePly() {
        ChessGame chessGame = ChessGame.fromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 10000");
        chessGame.makeMoveLan("e2e4");
    }

    @Test
    @DisplayName("Make move all 을 하는 도중 예외가 발생한다면 다시 그 함수를 실행하기 전 포지션으로 돌아가야 한다")
    void makeMoveLanAll() {
        ChessGame chessGame = ChessGame.startPosition();
        try {
            chessGame.makeMoveLanAll("e2e4 e7e5 g1f4");
        } catch (Exception ignored) {}
        assertEquals(START_FEN, chessGame.getFEN());
        try {
            chessGame.makeMoveSanAll("e4 e5 Nf4");
        } catch (Exception ignored) {}
        assertEquals(START_FEN, chessGame.getFEN());

        assertEquals(0, chessGame.getRootNode().children().size());
    }


    @Test
    @DisplayName("3 Check : FEN 을 잘 파싱 해야 한다.")
    void threeCheckParsing() {
        ChessGame chessGame = ChessGame.fromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 +1+1",
                GameVariant.THREE_CHECK);
        assertEquals(1, chessGame.getWhiteCheckedCount());
        assertEquals(1, chessGame.getBlackCheckedCount());
        chessGame = ChessGame.fromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 3+3 0 1",
                GameVariant.THREE_CHECK);
        assertEquals(0, chessGame.getWhiteCheckedCount());
        assertEquals(0, chessGame.getBlackCheckedCount());
    }

    @Test
    @DisplayName("3 Check : 체크가 3번 된다면 종료되어야 한다.")
    void threeCheckGameOver() {
        ChessGame chessGame = ChessGame.fromFEN("r1b2k1r/p5qp/2pNpp1Q/8/2B5/8/PpP2bPP/R4R1K w - - 1+2 6 20",
                GameVariant.THREE_CHECK);
        assertEquals(GameOverReason.NOTGAMEOVER, chessGame.isGameOver());
        chessGame.makeMoveSan("Qxg7#");
        assertEquals(GameOverReason.THREE_CHECK, chessGame.isGameOver());
    }

    @Test
    @DisplayName("3 Check : 체크 1번 한 것과 체크 2번 한것과 Zobrist 해쉬가 달라야 한다.")
    void threeCheckRepetition() {
        ChessGame chessGame = ChessGame.fromFEN(
                "rnbq1bnr/1ppp1ppp/p2k4/4p2Q/2B1P3/2N5/PPPP1PPP/R1B1K1NR w KQ - 3+3 0 5",
                GameVariant.THREE_CHECK
        );
        long previous = chessGame.getZobristHash();
        chessGame.makeMoveSanAll("Qg6+ Ke7 Qh5 Kd6");
        assertNotEquals(previous, chessGame.getZobristHash());
    }

    @Test
    @DisplayName("King of the hill : 킹이 중앙안에 들어와 있다면 즉시 게임이 끝나야 한다.")
    void kingGoneToHill() {
        ChessGame chessGame = ChessGame.fromFEN(
                "rnbq1bnr/ppppkppp/8/4p3/4K3/4P3/PPPP1PPP/RNBQ1BNR b - - 1 4",
                GameVariant.KING_OF_THE_HILL
        );
        assertEquals(GameOverReason.KING_OF_THE_HILL, chessGame.isGameOver());
        assertEquals(GameResult.WHITE_WON, chessGame.getGameResult());
    }

    @Test
    @DisplayName("Horde : 백 기물이 모두 없어졌다면 게임이 종료되어야 한다.")
    void hordePieceGone() {
        ChessGame chessGame = ChessGame.fromFEN("3q2k1/8/8/6P1/8/7q/8/8 b - - 0 62", GameVariant.HORDE);
        chessGame.makeMoveSan("Qxg5#");
        assertEquals(GameResult.BLACK_WON, chessGame.getGameResult());
        assertEquals(GameOverReason.HORDE, chessGame.isGameOver());
    }

    @Test
    @DisplayName("게임이 이미 끝났다면 되돌려도 결과는 같아야 한다")
    void gameResult() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveSanAll("e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#");
        chessGame.goBackward();
        chessGame.goBackward();
        chessGame.goForward();
        chessGame.makeMoveSan("Qf3");
        assertEquals(GameResult.WHITE_WON, chessGame.getGameResult());
        assertEquals(GameOverReason.CHECKMATE, chessGame.getGameOverReason());
    }

    @Test
    @DisplayName("캐슬링을 e1h1 으로 줘도 잘 인식해야 한다")
    void castlingE1H1() {
        ChessGame chessGame = ChessGame.fromFEN("rnbqkbnr/pppppppp/8/8/2B1P3/5N2/PPPPRPPP/1NBQK2R w Kkq - 0 1");
        assertThrows(IllegalMoveException.class, () -> chessGame.makeMove(Square.e1, Square.e2));
        chessGame.makeMove(Square.e1, Square.h1);
    }

    @Test
    void spendTime_doesNotAddIncrementAfterTimeUp() {
        ChessClock clock = new ChessClock(500, 2000, 500, 2000); // 예시 생성자

        clock.spendTime(true, 800);

        assertTrue(clock.isTimeUp(true));
        assertTrue(clock.getWhiteTimeMs() <= 0);
    }

    @Test
    @DisplayName("tryMakeMove: 합법수면 true를 반환하고 실제로 반영되어야 한다")
    void tryMakeMove_legal() {
        ChessGame chessGame = ChessGame.startPosition();

        assertTrue(chessGame.tryMakeMoveLan("e2e4"));
        assertFalse(chessGame.isEmpty(Square.e4));
    }

    @Test
    @DisplayName("tryMakeMove: 불법수면 예외 없이 false만 반환하고 상태는 그대로여야 한다")
    void tryMakeMove_illegal_doesNotThrowAndKeepsState() {
        ChessGame chessGame = ChessGame.startPosition();

        assertFalse(chessGame.tryMakeMoveLan("e2f3"));
        assertEquals(START_FEN, chessGame.getFEN());
        assertEquals(0, chessGame.getMoveHistory().size());
    }

    @Test
    @DisplayName("tryMakeMoveSan: 불법 SAN이면 false, 히스토리도 그대로여야 한다")
    void tryMakeMoveSan_illegal() {
        ChessGame chessGame = ChessGame.startPosition();

        assertFalse(chessGame.tryMakeMoveSan("Qh5#")); // 시작 포지션에서 불가능한 수
        assertEquals(0, chessGame.getMoveHistory().size());
    }

    @Test
    @DisplayName("tryMakeMoveAll: 중간에 불법수가 있으면 false 반환하고, 실제 동작은 부분 적용 없이 원래 포지션으로 남는다")
    void tryMakeMoveLanAll_partialFailure_currentlyRollsBackFully() {
        ChessGame chessGame = ChessGame.startPosition();

        boolean result = chessGame.tryMakeMoveLanAll("e2e4 e7e5 g1f4"); // g1f4는 불법수

        assertFalse(result);
        assertEquals(START_FEN, chessGame.getFEN(),
                "makeMoveAll처럼 원자적으로 롤백된다면 시작 포지션 그대로여야 합니다. " +
                        "만약 이 assert가 깨진다면 javadoc이 실제로 맞게 고쳐진 것이니 문서/동작 불일치 이슈를 닫으면 됩니다.");
        assertEquals(0, chessGame.getRootNode().children().size());
    }

    @Test
    @DisplayName("tryMakeMoveRaw: 반영은 되지만 history/listener 업데이트는 없어야 한다")
    void tryMakeMoveRaw_doesNotUpdateHistory() {
        ChessGame chessGame = ChessGame.startPosition();

        List<String> notified = new ArrayList<>();
        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onMoveMade(ChessGame source, MoveInfo moveInfo) {
                notified.add("onMoveMade");
            }
        });

        assertTrue(chessGame.tryMakeMoveRawLan("e2e4"));
        assertFalse(chessGame.isEmpty(com.pepero.jcb.api.enums.Square.e4), "보드 상태 자체는 반영되어야 합니다.");
        assertEquals(0, chessGame.getMoveHistory().size(), "raw 메서드는 히스토리를 갱신하지 않아야 합니다.");
        assertTrue(notified.isEmpty(), "raw 메서드는 리스너도 호출하지 않아야 합니다.");
    }

    @Test
    @DisplayName("tryMakeMoveRaw: 불법수면 false, 보드도 그대로여야 한다")
    void tryMakeMoveRaw_illegal() {
        ChessGame chessGame = ChessGame.startPosition();

        assertFalse(chessGame.tryMakeMoveRawLan("e2f3"));
        assertEquals(START_FEN, chessGame.getFEN());
    }

    @Test
    @DisplayName("isAtomicOver: 표준 체스(ATOMIC이 아님)에서 호출하면 VariantNotMatchException")
    void isAtomicOver_wrongVariant_throws() {
        ChessGame chessGame = ChessGame.startPosition(); // 기본은 STANDARD
        assertThrows(VariantNotMatchException.class, chessGame::isAtomicOver);
    }

    @Test
    @DisplayName("isGiveawayOver: 표준 체스에서 호출하면 VariantNotMatchException")
    void isGiveawayOver_wrongVariant_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(VariantNotMatchException.class, chessGame::isGiveawayOver);
    }

    @Test
    @DisplayName("isSuicideOver: 표준 체스에서 호출하면 VariantNotMatchException")
    void isSuicideOver_wrongVariant_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(VariantNotMatchException.class, chessGame::isSuicideOver);
    }

    @Test
    @DisplayName("isKingRaceOver: 표준 체스에서 호출하면 VariantNotMatchException")
    void isKingRaceOver_wrongVariant_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(VariantNotMatchException.class, chessGame::isKingRaceOver);
    }

    @Test
    @DisplayName("isAtomicOver: 시작 포지션에서는 당연히 false여야 한다")
    void isAtomicOver_startPosition_isFalse() {
        ChessGame chessGame = ChessGame.fromFEN(START_FEN, GameVariant.ATOMIC);
        assertFalse(chessGame.isAtomicOver());
    }

    @Test
    @DisplayName("isGiveawayOver: 시작 포지션에서는 당연히 false여야 한다")
    void isGiveawayOver_startPosition_isFalse() {
        ChessGame chessGame = ChessGame.fromFEN(START_FEN, GameVariant.GIVEAWAY);
        assertFalse(chessGame.isGiveawayOver());
    }

    @Test
    @DisplayName("isSuicideOver: 시작 포지션에서는 당연히 false여야 한다")
    void isSuicideOver_startPosition_isFalse() {
        ChessGame chessGame = ChessGame.fromFEN(START_FEN, GameVariant.SUICIDE);
        assertFalse(chessGame.isSuicideOver());
    }

    @Test
    @DisplayName("isKingRaceOver: 시작 포지션에서는 당연히 false여야 한다")
    void isKingRaceOver_startPosition_isFalse() {
        ChessGame chessGame = ChessGame.startPosition(GameVariant.RACING_KINGS);
        assertFalse(chessGame.isKingRaceOver());
    }

    @Test
    @DisplayName("isAtomicOver: 한쪽 킹이 폭발로 사라진 포지션은 true여야 한다")
    void isAtomicOver_kingExploded_isTrue() {
        String fen = "8/8/4k3/8/8/3KPr2/8/8 b - - 0 1";
        ChessGame chessGame = ChessGame.fromFEN(fen, GameVariant.ATOMIC);
        assertFalse(chessGame.isAtomicOver());
        chessGame.makeMoveSan("Rxe3#");
        assertTrue(chessGame.isAtomicOver());
    }

    @Test
    @DisplayName("isGiveawayOver: 백 기물이 전부 사라지면(백 승리 조건) true여야 한다")
    void isGiveawayOver_allPiecesGone_isTrue() {
        String fenNoWhitePieces = "8/1p6/8/8/8/8/8/8 w - - 0 1";
        ChessGame chessGame = ChessGame.fromFEN(fenNoWhitePieces, GameVariant.GIVEAWAY);
        assertTrue(chessGame.isGiveawayOver());
    }

    @Test
    @DisplayName("isKingRaceOver: 백 킹이 8랭크에 먼저 도달하면 true여야 한다")
    void isKingRaceOver_kingReachedRank8_isTrue() {
        String fenWhiteKingOnRank8 = "4K3/8/8/8/8/8/8/4k3 b - - 0 1";
        ChessGame chessGame = ChessGame.fromFEN(fenWhiteKingOnRank8, GameVariant.RACING_KINGS);
        assertTrue(chessGame.isKingRaceOver());
    }

    @Test
    @DisplayName("deleteVariation: 존재하지 않는 nodeId면 MoveNotFoundException")
    void deleteVariation_unknownNode_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(MoveNotFoundException.class, () -> chessGame.deleteVariation(999_999L));
    }

    @Test
    @DisplayName("deleteVariation: 루트 노드를 지우려 하면 HistoryTreeException")
    void deleteVariation_rootNode_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        long rootId = chessGame.getRootNode().id();
        assertThrows(HistoryTreeException.class, () -> chessGame.deleteVariation(rootId));
    }

    @Test
    @DisplayName("deleteVariation: 서브 variation을 지우면 부모의 children에서 사라지고, 나머지 mainline은 그대로여야 한다")
    void deleteVariation_removesSubtree() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.unmakeMove();
        chessGame.makeMoveLan("d7d5");
        long variationId = chessGame.getCurrentNodeId();
        chessGame.unmakeMove();

        MoveNodeDTO afterE4 = chessGame.getRootNode().children().getFirst();
        assertEquals(2, afterE4.children().size(), "e5(메인)와 d5(변이) 두 개가 있어야 합니다.");

        chessGame.deleteVariation(variationId);

        MoveNodeDTO afterDelete = chessGame.getRootNode().children().getFirst();
        assertEquals(1, afterDelete.children().size(), "변이가 지워지고 메인라인(e5)만 남아야 합니다.");
        assertEquals("e5", afterDelete.children().getFirst().san());
    }

    @Test
    @DisplayName("deleteVariation: 현재 위치한 노드를 지우면 부모 노드로 자동 점프해야 한다")
    void deleteVariation_currentNode_jumpsToParent() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        long e4Id = chessGame.getCurrentNodeId();
        chessGame.makeMoveLan("e7e5"); // 현재 위치 = e5

        chessGame.deleteVariation(chessGame.getCurrentNodeId());

        assertEquals(e4Id, chessGame.getCurrentNodeId(), "지운 노드의 부모(e4)로 이동해 있어야 합니다.");
        assertEquals(0, chessGame.getRootNode().children().getFirst().children().size());
    }

    @Test
    @DisplayName("promoteVariationLocal: 변이를 메인라인으로 승격시키면 순서가 바뀌어야 한다 (javadoc 예시: e4 e5 Nf3 (Nc3 Nf6) -> e4 e5 Nc3 (Nf3) Nf6)")
    void promoteVariationLocal_swapsOrder() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.makeMoveLan("g1f3");
        chessGame.unmakeMove();
        chessGame.makeMoveLan("b1c3"); // Nf3 자리에 변이로 Nc3 추가됨
        long nc3Id = chessGame.getCurrentNodeId();

        MoveNodeDTO beforePromote = chessGame.getRootNode().children().getFirst().children().getFirst();
        assertEquals("Nf3", beforePromote.children().getFirst().san(), "승격 전엔 Nf3가 메인(0번)이어야 합니다.");

        chessGame.promoteVariationLocal(nc3Id);

        MoveNodeDTO afterPromote = chessGame.getRootNode().children().getFirst().children().getFirst();
        assertEquals("Nc3", afterPromote.children().getFirst().san(), "승격 후엔 Nc3가 메인(0번)이어야 합니다.");
        assertEquals("Nf3", afterPromote.children().get(1).san(), "Nf3는 변이(1번)로 밀려나야 합니다.");
    }

    @Test
    @DisplayName("promoteVariationLocal: 존재하지 않는 nodeId면 MoveNotFoundException")
    void promoteVariationLocal_unknownNode_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(MoveNotFoundException.class, () -> chessGame.promoteVariationLocal(999_999L));
    }

    @Test
    @DisplayName("jumpToMainlinePly: 메인라인의 특정 ply로 정확히 이동해야 한다 (javadoc 예시)")
    void jumpToMainlinePly_movesToCorrectPly() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLanAll("e2e4 e7e5 g1f3 g8f6");

        chessGame.jumpToMainlinePly(2);

        assertEquals("rnbqkbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNBQKBNR w KQkq e6 0 2", chessGame.getFEN(),
                "ply 2(e7e5까지)로 이동해 있어야 합니다.");
    }

    @Test
    @DisplayName("jumpToMainlinePly: 음수 ply는 MoveNotFoundException")
    void jumpToMainlinePly_negative_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(MoveNotFoundException.class, () -> chessGame.jumpToMainlinePly(-1));
    }

    @Test
    @DisplayName("jumpToMainlinePly: 범위를 벗어난 ply는 MoveNotFoundException")
    void jumpToMainlinePly_outOfBounds_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        assertThrows(MoveNotFoundException.class, () -> chessGame.jumpToMainlinePly(5));
    }

    private static class NoopListener implements ChessGameListener {
        @Override public void onMoveMade(ChessGame source, MoveInfo moveInfo) {}
        @Override public void onMoveUnmade(ChessGame source, MoveInfo moveInfo) {}
        @Override public void onMoveRemade(ChessGame source, MoveInfo moveInfo) {}
        @Override public void onPositionJumped(ChessGame source, String fen) {}
        @Override public void onGameOver(ChessGame source, GameResult result, GameOverReason reason) {}
        @Override public void onHistoryChanged(ChessGame source) {}
    }

    @Test
    @DisplayName("makeMove -> unmakeMove -> remakeMove 순서대로 정확히 한 번씩만 콜백되어야 한다")
    void listener_moveLifecycle_calledInOrder() {
        ChessGame chessGame = ChessGame.startPosition();
        List<String> events = new ArrayList<>();

        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onMoveMade(ChessGame source, MoveInfo moveInfo) {
                events.add("made:" + moveInfo.toLanString());
            }

            @Override
            public void onMoveUnmade(ChessGame source, MoveInfo moveInfo) {
                events.add("unmade:" + moveInfo.toLanString());
            }

            @Override
            public void onMoveRemade(ChessGame source, MoveInfo moveInfo) {
                events.add("remade:" + moveInfo.toLanString());
            }
        });

        chessGame.makeMoveLan("e2e4");
        chessGame.unmakeMove();
        chessGame.remakeMove();

        assertEquals(List.of("made:e2e4", "unmade:e2e4", "remade:e2e4"), events);
    }

    @Test
    @DisplayName("removeChessGameListener 이후에는 더 이상 콜백되지 않아야 한다")
    void listener_removed_noLongerNotified() {
        ChessGame chessGame = ChessGame.startPosition();
        List<String> events = new ArrayList<>();

        ChessGameListener listener = new NoopListener() {
            @Override
            public void onMoveMade(ChessGame source, MoveInfo moveInfo) {
                events.add("made");
            }
        };

        chessGame.addChessGameListener(listener);
        chessGame.makeMoveLan("e2e4");
        assertEquals(1, events.size());

        chessGame.removeChessGameListener(listener);
        chessGame.makeMoveLan("e7e5");
        assertEquals(1, events.size(), "제거 후에는 콜백이 더 늘어나지 않아야 합니다.");
    }

    @Test
    @DisplayName("체크메이트가 나면 onGameOver가 정확한 결과/사유와 함께 호출되어야 한다")
    void listener_onGameOver_calledWithCorrectResult() {
        ChessGame chessGame = ChessGame.startPosition();
        List<GameResult> results = new ArrayList<>();
        List<GameOverReason> reasons = new ArrayList<>();

        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onGameOver(ChessGame source, GameResult result, GameOverReason reason) {
                results.add(result);
                reasons.add(reason);
            }
        });

        chessGame.makeMoveSanAll("e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#");

        assertEquals(1, results.size(), "게임이 끝난 시점에 정확히 한 번 호출되어야 합니다.");
        assertEquals(GameResult.WHITE_WON, results.getFirst());
        assertEquals(GameOverReason.CHECKMATE, reasons.getFirst());
    }

    @Test
    @DisplayName("jumpToNode / jumpToMainlinePly 호출 시 onPositionJumped가 이동 후 FEN과 함께 호출되어야 한다")
    void listener_onPositionJumped_calledWithNewFen() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        long e4Id = chessGame.getCurrentNodeId();
        chessGame.makeMoveLan("e7e5");

        List<String> jumpedFens = new ArrayList<>();
        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onPositionJumped(ChessGame source, String fen) {
                jumpedFens.add(fen);
            }
        });

        chessGame.jumpToNode(e4Id);

        assertEquals(1, jumpedFens.size());
        assertTrue(jumpedFens.getFirst().startsWith("rnbqkbnr/pppppppp/8/8/4P3/8"),
                "e4를 둔 직후 포지션의 FEN이어야 합니다.");
    }

    @Test
    @DisplayName("deleteVariation / promoteVariationLocal은 onHistoryChanged를 호출해야 한다")
    void listener_onHistoryChanged_calledOnTreeEdit() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.unmakeMove();
        chessGame.makeMoveLan("d7d5");
        long variationId = chessGame.getCurrentNodeId();

        int[] historyChangedCount = {0};
        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onHistoryChanged(ChessGame source) {
                historyChangedCount[0]++;
            }
        });

        chessGame.deleteVariation(variationId);

        assertEquals(1, historyChangedCount[0]);
    }

    @Test
    @DisplayName("lightWeightCopy: 복사 시점의 포지션은 같아야 하지만, 리스너/히스토리는 복사되지 않아야 한다")
    void lightWeightCopy_copiesPositionButNotHistoryOrListeners() {
        ChessGame original = ChessGame.startPosition();
        original.makeMoveLan("e2e4");
        original.makeMoveLan("e7e5");

        List<String> notifiedOnOriginalListener = new ArrayList<>();
        original.addChessGameListener(new NoopListener() {
            @Override
            public void onMoveMade(ChessGame source, MoveInfo moveInfo) {
                notifiedOnOriginalListener.add("made");
            }
        });

        ChessGame copy = ChessGame.lightWeightCopy(original);

        assertEquals(original.getFEN(), copy.getFEN(), "복사 시점의 포지션(FEN)은 같아야 합니다.");
        assertEquals(0, copy.getRootNode().children().size(), "히스토리 트리는 복사되지 않고 새로 시작해야 합니다.");

        copy.makeMoveLan("g1f3");
        assertTrue(notifiedOnOriginalListener.isEmpty(), "원본 리스너가 복사본의 이벤트에 반응하면 안 됩니다.");
    }

    @Test
    @DisplayName("lightWeightCopy: 복사 후 원본을 변경해도 복사본의 보드 상태는 영향받지 않아야 한다 (얕은 복사 방지)")
    void lightWeightCopy_isIndependentFromOriginal() {
        ChessGame original = ChessGame.startPosition();
        ChessGame copy = ChessGame.lightWeightCopy(original);

        original.makeMoveLan("e2e4");

        assertEquals(START_FEN, copy.getFEN(), "원본을 바꿔도 복사본은 그대로여야 합니다 (내부 배열/비트보드 공유 금지).");
        assertNotEquals(original.getFEN(), copy.getFEN());
    }

    @Test
    @DisplayName("getBoardSnapshot: 스냅샷 이후 원본에 수를 둬도 스냅샷 자체는 변하지 않아야 한다")
    void getBoardSnapshot_isIndependentOfFurtherMoves() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");

        Chessboard snapshot = chessGame.getBoardSnapshot();
        String snapshotFenBefore = ChessboardUtils.getFen(snapshot);

        chessGame.makeMoveLan("e7e5");

        String snapshotFenAfter = ChessboardUtils.getFen(snapshot);
        assertEquals(snapshotFenBefore, snapshotFenAfter,
                "getBoardSnapshot()이 내부 chessboard 참조를 그대로 반환한다면 이 assert가 깨집니다 (얕은 복사 의심).");
    }

    @Test
    @DisplayName("MoveNode 에서 ply 데이터를 저장해야 한다")
    void moveNodePly() {
        ChessGame chessGame = ChessGame.startPosition();
    }
}