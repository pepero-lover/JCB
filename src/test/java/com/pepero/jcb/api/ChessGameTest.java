package com.pepero.jcb.api;

import com.pepero.jcb.api.arena.ChessClock;
import com.pepero.jcb.api.book.PolyglotHashUtils;
import com.pepero.jcb.api.dto.CastlingRightsInfo;
import com.pepero.jcb.api.dto.MoveDataDTO;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.dto.MoveNodeDTO;
import com.pepero.jcb.api.enums.*;
import com.pepero.jcb.api.exception.*;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.api.perft.PerftResult;
import com.pepero.jcb.core.constant.BoardSquares;
import com.pepero.jcb.core.*;
import com.pepero.jcb.core.encode.EncodeMove;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
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
    @DisplayName("jumpToNode: 같은 노드로 점프하면 board 상태가 변하지 않아야 한다 (early exit)")
    void jumpToNode_sameNode_noOp() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLanAll("e2e4 e7e5");

        long currentId = chessGame.getCurrentNodeId();
        String fenBefore = chessGame.getFEN();

        chessGame.jumpToNode(currentId);

        assertEquals(fenBefore, chessGame.getFEN());
        assertEquals(currentId, chessGame.getCurrentNodeId());
    }

    @Test
    @DisplayName("jumpToNode: 바로 위 부모 / 바로 아래 자식으로 한 칸 이동이 정확해야 한다")
    void jumpToNode_singleStepParentAndChild() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        long parentId = chessGame.getCurrentNodeId();
        String parentFen = chessGame.getFEN();

        chessGame.makeMoveLan("e7e5");
        long childId = chessGame.getCurrentNodeId();
        String childFen = chessGame.getFEN();

        chessGame.jumpToNode(parentId);
        assertEquals(parentFen, chessGame.getFEN(), "자식 -> 부모 1칸 이동");

        chessGame.jumpToNode(childId);
        assertEquals(childFen, chessGame.getFEN(), "부모 -> 자식 1칸 이동");
    }

    @Test
    @DisplayName("jumpToNode: 형제 variation 사이 이동 시 LCA가 root가 아닌 공통 조상이어야 한다 (e4 e5 Nf3 Nc6 (Bb5 | Bc4))")
    void jumpToNode_acrossSiblingVariations_shallowLCA() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLanAll("e2e4 e7e5 g1f3 b8c6");

        chessGame.makeMoveLan("f1b5"); // mainline: Bb5
        long bb5Id = chessGame.getCurrentNodeId();
        String bb5Fen = chessGame.getFEN();

        chessGame.unmakeMove();
        chessGame.makeMoveLan("f1c4"); // variation: Bc4
        long bc4Id = chessGame.getCurrentNodeId();
        String bc4Fen = chessGame.getFEN();

        chessGame.jumpToNode(bb5Id);
        assertEquals(bb5Fen, chessGame.getFEN());

        chessGame.jumpToNode(bc4Id);
        assertEquals(bc4Fen, chessGame.getFEN());
    }

    @Test
    @DisplayName("jumpToNode: 서로 다른 1수 variation 라인 사이 이동 시 LCA가 root여야 한다 (e4 라인 <-> d4 라인)")
    void jumpToNode_acrossDeepVariations_rootLCA() {
        ChessGame chessGame = ChessGame.startPosition();

        chessGame.makeMoveLanAll("e2e4 e7e5 g1f3 b8c6 f1b5 a7a6");
        long eLineId = chessGame.getCurrentNodeId();
        String eLineFen = chessGame.getFEN();

        for (int i = 0; i < 6; i++) {
            chessGame.unmakeMove();
        }
        chessGame.makeMoveLanAll("d2d4 d7d5 c2c4 e7e6");
        long dLineId = chessGame.getCurrentNodeId();
        String dLineFen = chessGame.getFEN();

        chessGame.jumpToNode(eLineId);
        assertEquals(eLineFen, chessGame.getFEN());

        chessGame.jumpToNode(dLineId);
        assertEquals(dLineFen, chessGame.getFEN());
    }

    @Test
    @DisplayName("jumpToMainlinePly: ply 0으로 이동하면 시작 포지션(root)으로 돌아가야 한다")
    void jumpToMainlinePly_toRoot() {
        ChessGame chessGame = ChessGame.startPosition();
        String startFen = chessGame.getFEN();
        long rootId = chessGame.getRootNode().id();

        chessGame.makeMoveLanAll("e2e4 e7e5 g1f3");
        chessGame.jumpToMainlinePly(0);

        assertEquals(startFen, chessGame.getFEN());
        assertEquals(rootId, chessGame.getCurrentNodeId());
    }

    @Test
    @DisplayName("jumpToMainlinePly: 여러 번 앞뒤로 이동해도 매번 정확한 ply에 도달해야 한다")
    void jumpToMainlinePly_repeatedBackAndForth() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        String fenPly1 = chessGame.getFEN();
        chessGame.makeMoveLan("e7e5");
        String fenPly2 = chessGame.getFEN();
        chessGame.makeMoveLan("g1f3");
        String fenPly3 = chessGame.getFEN();
        chessGame.makeMoveLan("b8c6");
        String fenPly4 = chessGame.getFEN();

        chessGame.jumpToMainlinePly(1);
        assertEquals(fenPly1, chessGame.getFEN());

        chessGame.jumpToMainlinePly(3);
        assertEquals(fenPly3, chessGame.getFEN());

        chessGame.jumpToMainlinePly(0);

        chessGame.jumpToMainlinePly(4);
        assertEquals(fenPly4, chessGame.getFEN());

        chessGame.jumpToMainlinePly(2);
        assertEquals(fenPly2, chessGame.getFEN());
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
    @DisplayName("getMainlinePGN 은 variation 없이 mainline 수만 포함해야 한다")
    void getMainlinePGN_excludesVariations() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveSanAll("e4 e5 Nf3 Nc6");

        chessGame.makeMoveLan("f1b5");
        chessGame.unmakeMove();
        chessGame.makeMoveLan("f1c4");

        String pgn = chessGame.getMainlinePGN();

        assertTrue(pgn.contains("Bb5"), "mainline 수는 포함되어야 한다");
        assertFalse(pgn.contains("Bc4"), "variation 수는 포함되면 안 된다");
        assertFalse(pgn.contains("("), "variation 괄호가 없어야 한다");
        assertFalse(pgn.contains(")"), "variation 괄호가 없어야 한다");
    }

    @Test
    @DisplayName("getMainlinePGN 은 move number 를 정확하게 붙여야 한다")
    void getMainlinePGN_moveNumbering() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveSanAll("e4 e5 Nf3 Nc6");

        String pgn = chessGame.getMainlinePGN();

        assertTrue(pgn.contains("1. e4 e5 2. Nf3 Nc6"));

        chessGame = ChessGame.fromFEN("rnbqr1k1/pppp1ppp/5n2/8/1b2P3/2NN4/PPPP1PPP/R1BQKB1R b KQ - 2 6");
        chessGame.makeMoveSan("Nxe4");

        pgn = chessGame.getMainlinePGN();

        assertTrue(pgn.contains("6... Nxe4"));
    }

    @Test
    @DisplayName("getMainlinePGN 은 주석/시계 데이터를 포함하지 않아야 한다")
    void getMainlinePGN_excludesAnnotations() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        chessGame.setCurrentMoveClock(0, 4, 55);
        chessGame.setCurrentMoveComment("Good opening move");

        String pgn = chessGame.getMainlinePGN();

        assertTrue(pgn.contains("e4"));
        assertFalse(pgn.contains("%clk"));
        assertFalse(pgn.contains("Good opening move"));
    }

    @Test
    @DisplayName("getMainlinePGN 은 체크메이트로 끝난 게임에 결과 문자열을 붙여야 한다")
    void getMainlinePGN_appendsGameResult() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveSanAll("e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#");

        String pgn = chessGame.getMainlinePGN();

        assertTrue(pgn.trim().endsWith("1-0"), "백 승리 체크메이트니까 1-0 로 끝나야 한다");
    }

    @Test
    @DisplayName("getMainlinePGN 은 진행 중인 게임에는 * 를 붙여야 한다")
    void getMainlinePGN_ongoingGameResult() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveSanAll("e4 e5");

        String pgn = chessGame.getMainlinePGN();

        assertTrue(pgn.trim().endsWith("*"));
    }

    @Test
    @DisplayName("getMainlinePGN 은 헤더를 포함해야 한다")
    void getMainlinePGN_includesHeaders() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");

        String pgn = chessGame.getMainlinePGN();

        assertTrue(pgn.contains("[Event \"?\"]"));
        assertTrue(pgn.contains("[White \"?\"]"));
    }

    @Test
    @DisplayName("크레이지하우스: API 메서드(makeDropMove)로 포켓의 기물을 드랍할 수 있어야 한다")
    void testMakeDropMoveAPI() {
        String crazyFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[Qp] w KQkq - 0 1";
        ChessGame chessGame = ChessGame.fromFEN(crazyFen, GameVariant.CRAZY_HOUSE);

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
        chessGame.unmakeMove();
        chessGame.unmakeMove();
        chessGame.remakeMove();
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
        ChessClock clock = new ChessClock(500, 2000, 500, 2000);

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

        assertFalse(chessGame.tryMakeMoveSan("Qh5#"));
        assertEquals(0, chessGame.getMoveHistory().size());
    }

    @Test
    @DisplayName("tryMakeMoveAll: 중간에 불법수가 있으면 false 반환하고, 실제 동작은 부분 적용 없이 원래 포지션으로 남는다")
    void tryMakeMoveLanAll_partialFailure_currentlyRollsBackFully() {
        ChessGame chessGame = ChessGame.startPosition();

        boolean result = chessGame.tryMakeMoveLanAll("e2e4 e7e5 g1f4");

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
        ChessGame chessGame = ChessGame.startPosition();
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
        chessGame.makeMoveLan("e7e5");

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
        chessGame.makeMoveLan("b1c3");
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
        @Override public void onGameStateChecked(ChessGame source, GameResult result, GameOverReason reason) {}
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
    @DisplayName("게임이 끝나지 않는 일반적인 수에서는 onGameStateChecked가 UNKNOWN과 함께 호출되고, onGameOver는 호출되지 않아야 한다")
    void listener_onGameStateChecked_calledOnNormalMove_withUnknownResult() {
        ChessGame chessGame = ChessGame.startPosition();

        List<GameResult> stateCheckedResults = new ArrayList<>();
        List<GameResult> gameOverResults = new ArrayList<>();

        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onGameStateChecked(ChessGame source, GameResult result, GameOverReason reason) {
                stateCheckedResults.add(result);
            }

            @Override
            public void onGameOver(ChessGame source, GameResult result, GameOverReason reason) {
                gameOverResults.add(result);
            }
        });

        chessGame.makeMoveLan("e2e4");

        assertEquals(1, stateCheckedResults.size(), "수를 둘 때마다 onGameStateChecked가 호출되어야 합니다.");
        assertEquals(GameResult.UNKNOWN, stateCheckedResults.getFirst());
        assertTrue(gameOverResults.isEmpty(), "게임이 끝나지 않았으므로 onGameOver는 호출되지 않아야 합니다.");
    }

    @Test
    @DisplayName("이미 평가되어 캐싱된 체크메이트 노드를 재방문하면, onGameStateChecked는 매번 호출되지만 onGameOver는 최초 1회만 호출되어야 한다")
    void listener_onGameStateChecked_firesEveryVisit_onGameOver_firesOnlyOnce() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveSanAll("e4 e5 Qh5 Nc6 Bc4 Nf6");

        List<GameResult> gameOverResults = new ArrayList<>();
        List<GameResult> stateCheckedResults = new ArrayList<>();

        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onGameOver(ChessGame source, GameResult result, GameOverReason reason) {
                gameOverResults.add(result);
            }

            @Override
            public void onGameStateChecked(ChessGame source, GameResult result, GameOverReason reason) {
                stateCheckedResults.add(result);
            }
        });

        chessGame.makeMoveSan("Qxf7#");

        assertEquals(1, gameOverResults.size(), "최초 메이트 시점엔 onGameOver가 한 번 호출되어야 합니다.");
        assertEquals(GameResult.WHITE_WON, gameOverResults.getFirst());
        assertEquals(1, stateCheckedResults.size());
        assertEquals(GameResult.WHITE_WON, stateCheckedResults.getFirst());

        for (int i = 0; i < 3; i++) {
            chessGame.unmakeMove();
            chessGame.remakeMove();
        }

        assertEquals(1, gameOverResults.size(),
                "이미 알려진 메이트 노드를 여러 번 재방문해도 onGameOver는 재발생하면 안 됩니다 (원래 버그였던 부분).");

        long mateReentryCount = stateCheckedResults.stream()
                .filter(result -> result == GameResult.WHITE_WON)
                .count();
        assertEquals(4, mateReentryCount,
                "최초 1회 + 재진입 3회 = 총 4번, 메이트 노드에 들어갈 때마다 onGameStateChecked가 WHITE_WON으로 호출되어야 합니다.");
    }

    @Test
    @DisplayName("jumpToNode / jumpToMainlinePly 호출 시에도 onGameStateChecked가 호출되어야 한다")
    void listener_onGameStateChecked_calledOnJump() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        long e4Id = chessGame.getCurrentNodeId();
        chessGame.makeMoveLan("e7e5");

        List<GameResult> stateCheckedResults = new ArrayList<>();
        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onGameStateChecked(ChessGame source, GameResult result, GameOverReason reason) {
                stateCheckedResults.add(result);
            }
        });

        chessGame.jumpToNode(e4Id);
        assertEquals(1, stateCheckedResults.size(), "jumpToNode 호출 시 onGameStateChecked가 호출되어야 합니다.");

        chessGame.jumpToMainlinePly(0);
        assertEquals(2, stateCheckedResults.size(), "jumpToMainlinePly 호출 시에도 onGameStateChecked가 호출되어야 합니다.");
    }

    @Test
    @DisplayName("deleteVariation / promoteVariationLocal 호출 시에도 onGameStateChecked가 호출되어야 한다")
    void listener_onGameStateChecked_calledOnTreeEdit() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.unmakeMove();
        chessGame.makeMoveLan("d7d5");
        long variationId = chessGame.getCurrentNodeId();

        int[] deleteCheckedCount = {0};
        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onGameStateChecked(ChessGame source, GameResult result, GameOverReason reason) {
                deleteCheckedCount[0]++;
            }
        });

        chessGame.deleteVariation(variationId);
        assertTrue(deleteCheckedCount[0] >= 1,
                "deleteVariation 호출 시 onGameStateChecked가 최소 한 번은 호출되어야 합니다.");

        ChessGame promoteGame = ChessGame.startPosition();
        promoteGame.makeMoveLan("e2e4");
        promoteGame.makeMoveLan("e7e5");
        promoteGame.makeMoveLan("g1f3");
        promoteGame.unmakeMove();
        promoteGame.makeMoveLan("b1c3");
        long nc3Id = promoteGame.getCurrentNodeId();

        int[] promoteCheckedCount = {0};
        promoteGame.addChessGameListener(new NoopListener() {
            @Override
            public void onGameStateChecked(ChessGame source, GameResult result, GameOverReason reason) {
                promoteCheckedCount[0]++;
            }
        });

        promoteGame.promoteVariationLocal(nc3Id);
        assertTrue(promoteCheckedCount[0] >= 1,
                "promoteVariationLocal 호출 시 onGameStateChecked가 최소 한 번은 호출되어야 합니다.");
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
    @DisplayName("getGameResult(false)는 클레임 가능한 무승부를 결과에 포함하지 않아야 한다")
    void getGameResult_excludesClaimableDrawByDefault() {
        ChessGame chessGame = ChessGame.fromFEN("1n2k3/8/8/8/8/8/8/R3K3 w - - 100 60");

        assertTrue(chessGame.canClaimFiftyMoves());
        assertEquals(GameResult.UNKNOWN, chessGame.getGameResult());
        assertEquals(GameResult.UNKNOWN, chessGame.getGameResult(false));
        assertEquals(GameOverReason.NOTGAMEOVER, chessGame.getGameOverReason());
    }

    @Test
    @DisplayName("getGameResult(true)는 50수 클레임이 가능하면 DRAW/FIFTYMOVES_CLAIM을 반환해야 한다")
    void getGameResult_includesFiftyMovesClaim_whenRequested() {
        ChessGame chessGame = ChessGame.fromFEN("1n2k3/8/8/8/8/8/8/R3K3 w - - 100 60");

        assertEquals(GameResult.DRAW, chessGame.getGameResult(true));
        assertEquals(GameOverReason.FIFTYMOVES_CLAIM, chessGame.getGameOverReason(true));

        assertEquals(GameResult.UNKNOWN, chessGame.getGameResult());
        assertEquals(GameOverReason.NOTGAMEOVER, chessGame.getGameOverReason());
    }

    @Test
    @DisplayName("3회 반복 클레임이 가능한 상황에서 getGameResult(true)는 DRAW/THREEFOLD_CLAIM을 반환해야 한다")
    void getGameResult_includesThreefoldClaim_whenRequested() {
        ChessGame chessGame = ChessGame.startPosition();

        for (int i = 0; i < 2; i++) {
            chessGame.makeMoveLan("g1f3");
            chessGame.makeMoveLan("g8f6");
            chessGame.makeMoveLan("f3g1");
            chessGame.makeMoveLan("f6g8");
        }
        chessGame.makeMoveLan("g1f3");
        chessGame.makeMoveLan("g8f6");

        assertEquals(GameResult.UNKNOWN, chessGame.getGameResult());
        assertEquals(GameResult.DRAW, chessGame.getGameResult(true));
        assertEquals(GameOverReason.THREEFOLD_CLAIM, chessGame.getGameOverReason(true));
    }

    @Test
    @DisplayName("이미 체크메이트로 끝난 게임은 includeClaimableDraws 값과 무관하게 같은 결과를 반환해야 한다")
    void getGameResult_trueParam_doesNotChangeAlreadyFinishedGame() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveSanAll("e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#");

        assertEquals(GameResult.WHITE_WON, chessGame.getGameResult(true));
        assertEquals(GameOverReason.CHECKMATE, chessGame.getGameOverReason(true));
    }

    @Test
    @DisplayName("getGameResult(true)로 클레임 가능한 무승부를 확인해도 onGameOver는 호출되면 안 된다 (실제로 끝난 게 아니므로)")
    void getGameResult_includingClaimableDraw_doesNotFireOnGameOver() {
        ChessGame chessGame = ChessGame.fromFEN("1n2k3/8/8/8/8/8/8/R3K3 w - - 100 60");

        List<GameResult> gameOverResults = new ArrayList<>();
        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onGameOver(ChessGame source, GameResult result, GameOverReason reason) {
                gameOverResults.add(result);
            }
        });

        assertEquals(GameResult.DRAW, chessGame.getGameResult(true));
        assertTrue(gameOverResults.isEmpty(),
                "클레임 가능일 뿐 실제로 게임이 끝난 게 아니므로 onGameOver가 호출되면 안 됩니다.");
    }

    @Test
    @DisplayName("getGameResultAt: 지정한 노드의 결과를 mainline/currentNode와 무관하게 조회할 수 있어야 한다")
    void getGameResultAt_evaluatesSpecificNodeIndependently() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveSanAll("e4 e5 Qh5 Nc6 Bc4 Nf6 Qxf7#");
        long mateNodeId = chessGame.getCurrentNodeId();

        chessGame.unmakeMove();
        chessGame.unmakeMove();
        chessGame.remakeMove();
        chessGame.makeMoveSan("Qf3");
        long qf3NodeId = chessGame.getCurrentNodeId();

        assertEquals(GameResult.WHITE_WON, chessGame.getGameResult());
        assertEquals(GameOverReason.CHECKMATE, chessGame.getGameOverReason());

        assertEquals(GameResult.UNKNOWN, chessGame.getGameResultAt(qf3NodeId));
        assertEquals(GameOverReason.NOTGAMEOVER, chessGame.getGameOverReasonAt(qf3NodeId));

        assertEquals(GameResult.WHITE_WON, chessGame.getGameResultAt(mateNodeId));
        assertEquals(GameOverReason.CHECKMATE, chessGame.getGameOverReasonAt(mateNodeId));
        assertEquals(qf3NodeId, chessGame.getCurrentNodeId(),
                "getGameResultAt 호출 후에도 currentNode가 바뀌면 안 됩니다.");
    }

    @Test
    @DisplayName("getGameResultAt/getGameOverReasonAt: 존재하지 않는 nodeId면 MoveNotFoundException")
    void getGameResultAt_unknownNode_throws() {
        ChessGame chessGame = ChessGame.startPosition();

        assertThrows(MoveNotFoundException.class, () -> chessGame.getGameResultAt(999_999L));
        assertThrows(MoveNotFoundException.class, () -> chessGame.getGameOverReasonAt(999_999L));
    }

    @Test
    @DisplayName("getGameResultAt: 아직 한 번도 평가되지 않은 노드를 조회해도 onGameOver는 호출되면 안 된다")
    void getGameResultAt_doesNotFireOnGameOver_evenOnFirstEvaluation() {
        String pgn = "1.e4 e5 2.Qh5 Nc6 3.Bc4 g6 (3...Nf6 4.Qxf7#) *";

        ChessGame chessGame = ChessGame.startPosition();
        chessGame.loadPGN(pgn);

        MoveNodeDTO root = chessGame.getRootNode();
        MoveNodeDTO e4 = root.children().getFirst();
        MoveNodeDTO e5 = e4.children().getFirst();
        MoveNodeDTO qh5 = e5.children().getFirst();
        MoveNodeDTO nc6 = qh5.children().getFirst();
        MoveNodeDTO bc4 = nc6.children().getFirst();
        MoveNodeDTO nf6Variation = bc4.children().get(1);
        assertEquals("Nf6", nf6Variation.san());

        MoveNodeDTO mateNode = nf6Variation.children().getFirst();
        assertEquals("Qxf7#", mateNode.san());
        long mateNodeId = mateNode.id();

        long currentNodeIdBefore = chessGame.getCurrentNodeId();

        List<GameResult> gameOverResults = new ArrayList<>();
        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onGameOver(ChessGame source, GameResult result, GameOverReason reason) {
                gameOverResults.add(result);
            }
        });

        assertEquals(GameResult.WHITE_WON, chessGame.getGameResultAt(mateNodeId));
        assertEquals(GameOverReason.CHECKMATE, chessGame.getGameOverReasonAt(mateNodeId));

        assertTrue(gameOverResults.isEmpty(),
                "다른 variation의 노드를 조회한 것뿐이므로, 처음 평가되는 노드라도 onGameOver가 호출되면 안 됩니다.");
        assertEquals(currentNodeIdBefore, chessGame.getCurrentNodeId());
    }

    @Test
    @DisplayName("getGameResultAt(nodeId, true): 지정한 노드 위치를 기준으로 클레임 가능한 무승부를 조회할 수 있어야 한다")
    void getGameResultAt_includesClaimableDrawAtGivenNode() {
        ChessGame chessGame = ChessGame.startPosition();

        for (int i = 0; i < 2; i++) {
            chessGame.makeMoveLan("g1f3");
            chessGame.makeMoveLan("g8f6");
            chessGame.makeMoveLan("f3g1");
            chessGame.makeMoveLan("f6g8");
        }
        chessGame.makeMoveLan("g1f3");
        chessGame.makeMoveLan("g8f6");
        long repetitionNodeId = chessGame.getCurrentNodeId();

        chessGame.unmakeMove();
        chessGame.makeMoveLan("b8c6");
        long branchedNodeId = chessGame.getCurrentNodeId();

        assertEquals(GameResult.UNKNOWN, chessGame.getGameResultAt(repetitionNodeId));
        assertEquals(GameOverReason.NOTGAMEOVER, chessGame.getGameOverReasonAt(repetitionNodeId));

        assertEquals(GameResult.DRAW, chessGame.getGameResultAt(repetitionNodeId, true));
        assertEquals(GameOverReason.THREEFOLD_CLAIM, chessGame.getGameOverReasonAt(repetitionNodeId, true));

        assertEquals(branchedNodeId, chessGame.getCurrentNodeId(),
                "getGameResultAt 조회 후에도 currentNode는 변하지 않아야 합니다.");
    }

    @Test
    @DisplayName("fromChessboard: 주어진 Chessboard 의 포지션으로 ChessGame 이 생성되어야 한다")
    void fromChessboard() {
        Chessboard board = new Chessboard(SCHOLARS_MATE_FEN);

        ChessGame chessGame = ChessGame.fromChessboard(board);

        assertEquals(SCHOLARS_MATE_FEN, chessGame.getFEN());
        assertTrue(chessGame.getMoveHistory().isEmpty(), "히스토리 트리는 포함하지 않고 새로 시작해야 합니다.");
    }

    @Test
    @DisplayName("fromPGN: PGN 문자열로부터 바로 ChessGame 이 생성되어야 한다")
    void fromPGN() {
        String pgn = "1.e4 e5 2.Nf3 Nc6 *";

        ChessGame chessGame = ChessGame.fromPGN(pgn);

        assertEquals(START_FEN, chessGame.getFEN());
        assertEquals(4, chessGame.getMainlineData().size());
        assertEquals("e4", chessGame.getRootNode().children().getFirst().san());
    }

    @Test
    @DisplayName("heavyWeightCopy: 히스토리 트리(변이 포함)와 현재 위치까지 깊은 복사가 되어야 한다")
    void heavyWeightCopy() {
        ChessGame original = ChessGame.startPosition();
        original.makeMoveLan("e2e4");
        original.makeMoveLan("e7e5");
        original.unmakeMove();
        original.makeMoveLan("d7d5");
        original.makeMoveLan("e4d5");

        ChessGame copy = ChessGame.heavyWeightCopy(original);

        assertEquals(original.getFEN(), copy.getFEN());
        assertEquals(original.getTotalNodeCount(), copy.getTotalNodeCount());
        assertEquals(original.getMoveHistory().size(), copy.getMoveHistory().size());

        String copyFenBefore = copy.getFEN();
        original.makeMoveLan("g8f6");
        assertEquals(copyFenBefore, copy.getFEN());
    }

    @Test
    @DisplayName("canMakeMove: 합법수면 true, 불법수면 false 를 반환하고 실제로 두지는 않아야 한다")
    void canMakeMove() {
        ChessGame chessGame = ChessGame.startPosition();

        assertTrue(chessGame.canMakeMove(Square.e2, Square.e4));
        assertTrue(chessGame.canMakeMove(Square.e2, Square.e4, PieceType.NONE));
        assertFalse(chessGame.canMakeMove(Square.e2, Square.e5));

        assertEquals(START_FEN, chessGame.getFEN(), "canMakeMove 는 실제 수를 반영하면 안 됩니다.");
    }

    @Test
    @DisplayName("canMakeMoveLan: LAN 문자열이 합법수인지 실제로 두지 않고 판별해야 한다")
    void canMakeMoveLan() {
        ChessGame chessGame = ChessGame.startPosition();

        assertTrue(chessGame.canMakeMoveLan("e2e4"));
        assertFalse(chessGame.canMakeMoveLan("e2f3"));
        assertEquals(START_FEN, chessGame.getFEN());
    }

    @Test
    @DisplayName("canMakeMoveSan: SAN 문자열이 합법수인지 실제로 두지 않고 판별해야 한다")
    void canMakeMoveSan() {
        ChessGame chessGame = ChessGame.startPosition();

        assertTrue(chessGame.canMakeMoveSan("e4"));
        assertFalse(chessGame.canMakeMoveSan("Qh5#"));
        assertEquals(START_FEN, chessGame.getFEN());
    }

    @Test
    @DisplayName("canUndo / canRedo: 히스토리 위치에 따라 정확히 판별해야 한다")
    void canUndoAndCanRedo() {
        ChessGame chessGame = ChessGame.startPosition();

        assertFalse(chessGame.canUndo());
        assertFalse(chessGame.canRedo());

        chessGame.makeMoveLan("e2e4");
        assertTrue(chessGame.canUndo());
        assertFalse(chessGame.canRedo());

        chessGame.unmakeMove();
        assertFalse(chessGame.canUndo());
        assertTrue(chessGame.canRedo());
    }

    @Test
    @DisplayName("canRedo(variationIndex): 변이 인덱스가 존재하는지 판별해야 한다")
    void canRedoWithVariationIndex() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        chessGame.unmakeMove();
        chessGame.makeMoveLan("d7d5");
        chessGame.unmakeMove();

        assertTrue(chessGame.canRedo(0));
        assertTrue(chessGame.canRedo(1));
        assertFalse(chessGame.canRedo(2));
    }

    @Test
    @DisplayName("캐슬링 권리 조회 메서드들이 FEN 의 각 캐슬링 플래그를 정확히 반영해야 한다")
    void castlingRightsQueries() {
        ChessGame full = ChessGame.fromFEN("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertTrue(full.hasWhiteKingSideCastling());
        assertTrue(full.hasWhiteQueenSideCastling());
        assertTrue(full.hasBlackKingSideCastling());
        assertTrue(full.hasBlackQueenSideCastling());
        assertTrue(full.hasWhiteCastling());
        assertTrue(full.hasBlackCastling());
        assertTrue(full.hasCastling());

        ChessGame partial = ChessGame.fromFEN("r3k2r/8/8/8/8/8/8/R3K2R w Kq - 0 1");
        assertTrue(partial.hasWhiteKingSideCastling());
        assertFalse(partial.hasWhiteQueenSideCastling());
        assertFalse(partial.hasBlackKingSideCastling());
        assertTrue(partial.hasBlackQueenSideCastling());
        assertTrue(partial.hasWhiteCastling());
        assertTrue(partial.hasBlackCastling());
        assertTrue(partial.hasCastling());

        ChessGame none = ChessGame.fromFEN("r3k2r/8/8/8/8/8/8/R3K2R w - - 0 1");
        assertFalse(none.hasWhiteKingSideCastling());
        assertFalse(none.hasWhiteQueenSideCastling());
        assertFalse(none.hasBlackKingSideCastling());
        assertFalse(none.hasBlackQueenSideCastling());
        assertFalse(none.hasWhiteCastling());
        assertFalse(none.hasBlackCastling());
        assertFalse(none.hasCastling());
    }

    @Test
    @DisplayName("getCastlingRights: 캐슬링 권리가 변하면 반환되는 정보도 달라져야 한다")
    void getCastlingRights() {
        ChessGame chessGame = ChessGame.fromFEN("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");

        CastlingRightsInfo before = chessGame.getCastlingRights();
        assertNotNull(before);
        assertEquals(before, chessGame.getCastlingRights());

        chessGame.makeMoveLan("e1g1");

        CastlingRightsInfo after = chessGame.getCastlingRights();
        assertNotEquals(before, after, "캐슬링 이후에는 권리 정보가 달라져야 합니다.");
    }

    @Test
    @DisplayName("getChecker: 체크를 걸고 있는 기물의 위치를 정확히 반환해야 한다")
    void getChecker() {
        ChessGame chessGame = ChessGame.fromFEN(SCHOLARS_MATE_FEN);

        List<Square> checkers = chessGame.getChecker();

        assertEquals(1, checkers.size());
        assertEquals(Square.f7, checkers.getFirst());
    }

    @Test
    @DisplayName("getChecker: 체크가 아니면 빈 리스트를 반환해야 한다")
    void getChecker_noCheck_returnsEmpty() {
        ChessGame chessGame = ChessGame.startPosition();
        assertTrue(chessGame.getChecker().isEmpty());
    }

    @Test
    @DisplayName("isSquareAttacked: 지정한 사이드가 해당 칸을 공격하고 있는지 정확히 판별해야 한다")
    void isSquareAttacked() {
        ChessGame chessGame = ChessGame.fromFEN("4k3/8/8/8/8/8/8/R3K3 w - - 0 1");

        assertTrue(chessGame.isSquareAttacked(Square.a8, true), "백 룩이 a파일 전체를 공격하고 있어야 합니다.");
        assertFalse(chessGame.isSquareAttacked(Square.h8, false), "흑 킹은 h8 을 공격할 수 없습니다 (인접하지 않음).");
    }

    @Test
    @DisplayName("shouldPromotion: 프로모션 랭크로 이동하는 폰인지 정확히 판별해야 한다")
    void shouldPromotion() {
        ChessGame chessGame = ChessGame.fromFEN("8/4P3/8/8/4k3/8/8/4K3 w - - 0 1");

        assertTrue(chessGame.shouldPromotion(Square.e7, Square.e8));
        assertFalse(chessGame.shouldPromotion(Square.e1, Square.e2), "폰이 없는 칸에서는 프로모션이 아닙니다.");
    }

    @Test
    @DisplayName("getPolyglotHash: 시작 포지션의 Polyglot 해시가 잘 알려진 값과 일치해야 한다")
    void getPolyglotHash() {
        ChessGame chessGame = ChessGame.startPosition();
        assertEquals("463b96181691fc9c", Long.toHexString(chessGame.getPolyglotHash()));

        chessGame.makeMoveLan("e2e4");
        assertNotEquals("463b96181691fc9c", Long.toHexString(chessGame.getPolyglotHash()));
    }

    @Test
    @DisplayName("lanToMoveData: LAN 문자열을 MoveInfo 로 변환하고, 원본 보드는 변하지 않아야 한다")
    void lanToMoveData() {
        ChessGame chessGame = ChessGame.startPosition();

        MoveInfo moveInfo = chessGame.lanToMoveData("e2e4");

        assertEquals("e2e4", moveInfo.toLanString());
        assertEquals(START_FEN, chessGame.getFEN(), "lanToMoveData 는 실제 수를 반영하면 안 됩니다.");
    }

    @Test
    @DisplayName("sanToMoveData: SAN 문자열을 MoveInfo 로 변환하고, 원본 보드는 변하지 않아야 한다")
    void sanToMoveData() {
        ChessGame chessGame = ChessGame.startPosition();

        MoveInfo moveInfo = chessGame.sanToMoveData("e4");

        assertEquals("e2e4", moveInfo.toLanString());
        assertEquals(START_FEN, chessGame.getFEN(), "sanToMoveData 는 실제 수를 반영하면 안 됩니다.");
    }

    @Test
    @DisplayName("makeMoveRawLan: 보드는 반영되지만 히스토리는 갱신되지 않아야 한다")
    void makeMoveRawLan() {
        ChessGame chessGame = ChessGame.startPosition();

        chessGame.makeMoveRawLan("e2e4");

        assertFalse(chessGame.isEmpty(Square.e4));
        assertTrue(chessGame.getMoveHistory().isEmpty(), "raw 메서드는 히스토리를 갱신하면 안 됩니다.");
    }

    @Test
    @DisplayName("makeMoveRaw(int) / unmakeMoveRaw(int): 인코딩된 수로 반영/원복이 가능해야 한다")
    void makeMoveRawAndUnmakeMoveRawWithEncodedMove() {
        ChessGame chessGame = ChessGame.startPosition();
        int encodedMove = chessGame.getLegalMovesForSource(Square.e2).get(1).originEncodedData(); // e2e4

        chessGame.makeMoveRaw(encodedMove);
        assertFalse(chessGame.isEmpty(Square.e4));

        chessGame.unmakeMoveRaw(encodedMove);
        assertEquals(START_FEN, chessGame.getFEN());
    }

    @Test
    @DisplayName("makeMoveRaw(MoveInfo) / unmakeMoveRaw(MoveInfo): MoveInfo 로도 반영/원복이 가능해야 한다")
    void makeMoveRawAndUnmakeMoveRawWithMoveInfo() {
        ChessGame chessGame = ChessGame.startPosition();
        MoveInfo moveInfo = chessGame.getLegalMovesForSource(Square.e2).get(1); // e2e4

        chessGame.makeMoveRaw(moveInfo);
        assertFalse(chessGame.isEmpty(Square.e4));

        chessGame.unmakeMoveRaw(moveInfo);
        assertEquals(START_FEN, chessGame.getFEN());
    }

    @Test
    @DisplayName("tryMakeMoveRaw(int): 합법수면 true 를 반환하고 반영되며, 불법수면 false 를 반환해야 한다")
    void tryMakeMoveRawWithEncodedMove() {
        ChessGame chessGame = ChessGame.startPosition();
        int legalMove = chessGame.getLegalMovesForSource(Square.e2).get(1).originEncodedData(); // e2e4

        assertTrue(chessGame.tryMakeMoveRaw(legalMove));
        assertFalse(chessGame.isEmpty(Square.e4));

        assertFalse(chessGame.tryMakeMoveRaw(legalMove));
    }

    @Test
    @DisplayName("tryMakeMoveRaw(MoveInfo): 합법수면 true, 불법수면 false 를 반환해야 한다")
    void tryMakeMoveRawWithMoveInfo() {
        ChessGame chessGame = ChessGame.startPosition();
        MoveInfo legalMove = chessGame.getLegalMovesForSource(Square.e2).get(1); // e2e4

        assertTrue(chessGame.tryMakeMoveRaw(legalMove));
        assertFalse(chessGame.isEmpty(Square.e4));
        assertFalse(chessGame.tryMakeMoveRaw(legalMove));
    }

    @Test
    @DisplayName("makeMoveLanReturningSan: 수를 반영하면서 변환된 SAN 문자열을 반환해야 한다")
    void makeMoveLanReturningSan() {
        ChessGame chessGame = ChessGame.startPosition();

        String san = chessGame.makeMoveLanReturningSan("e2e4");

        assertEquals("e4", san);
        assertFalse(chessGame.isEmpty(Square.e4));
        assertEquals(1, chessGame.getMoveHistory().size(), "이 메서드는 raw 가 아니므로 히스토리가 갱신되어야 합니다.");
    }

    @Test
    @DisplayName("tryMakeMoveSanAll: 모두 합법수면 true 를 반환하고 순서대로 반영되어야 한다")
    void tryMakeMoveSanAll() {
        ChessGame chessGame = ChessGame.startPosition();

        boolean result = chessGame.tryMakeMoveSanAll("e4 e5 Nf3 Nc6");

        assertTrue(result);
        assertEquals(4, chessGame.getMoveHistory().size());
    }

    @Test
    @DisplayName("isThreeChecked: 3 Check 변형에서 3회 체크되면 true 를 반환해야 한다")
    void isThreeChecked() {
        ChessGame chessGame = ChessGame.fromFEN("r1b2k1r/p5qp/2pNpp1Q/8/2B5/8/PpP2bPP/R4R1K w - - 1+2 6 20",
                GameVariant.THREE_CHECK);
        assertFalse(chessGame.isThreeChecked());

        chessGame.makeMoveSan("Qxg7#");
        assertTrue(chessGame.isThreeChecked());
    }

    @Test
    @DisplayName("isThreeChecked: 3 Check 변형이 아니면 VariantNotMatchException 이 발생해야 한다")
    void isThreeChecked_wrongVariant_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(VariantNotMatchException.class, chessGame::isThreeChecked);
    }

    @Test
    @DisplayName("isKingGoneToHill: King of the hill 변형에서 킹이 중앙에 들어오면 true 를 반환해야 한다")
    void isKingGoneToHill() {
        ChessGame chessGame = ChessGame.fromFEN(
                "rnbq1bnr/ppppkppp/8/4p3/4K3/4P3/PPPP1PPP/RNBQ1BNR b - - 1 4",
                GameVariant.KING_OF_THE_HILL
        );
        assertTrue(chessGame.isKingGoneToHill());
    }

    @Test
    @DisplayName("isKingGoneToHill: King of the hill 변형이 아니면 VariantNotMatchException 이 발생해야 한다")
    void isKingGoneToHill_wrongVariant_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(VariantNotMatchException.class, chessGame::isKingGoneToHill);
    }

    @Test
    @DisplayName("isHordePiecesGone: Horde 변형에서 백 기물이 모두 사라지면 true 를 반환해야 한다")
    void isHordePiecesGone() {
        ChessGame chessGame = ChessGame.fromFEN("3q2k1/8/8/6P1/8/7q/8/8 b - - 0 62", GameVariant.HORDE);
        assertFalse(chessGame.isHordePiecesGone());

        chessGame.makeMoveSan("Qxg5#");
        assertTrue(chessGame.isHordePiecesGone());
    }

    @Test
    @DisplayName("isHordePiecesGone: Horde 변형이 아니면 VariantNotMatchException 이 발생해야 한다")
    void isHordePiecesGone_wrongVariant_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(VariantNotMatchException.class, chessGame::isHordePiecesGone);
    }

    @Test
    @DisplayName("canClaimThreefoldRepetition: 3회 동형 반복이 되면 true 를 반환해야 한다")
    void canClaimThreefoldRepetition() {
        ChessGame chessGame = ChessGame.startPosition();
        assertFalse(chessGame.canClaimThreefoldRepetition());

        for (int i = 0; i < 2; i++) {
            chessGame.makeMoveLan("g1f3");
            chessGame.makeMoveLan("g8f6");
            chessGame.makeMoveLan("f3g1");
            chessGame.makeMoveLan("f6g8");
        }
        chessGame.makeMoveLan("g1f3");
        chessGame.makeMoveLan("g8f6");

        assertTrue(chessGame.canClaimThreefoldRepetition());
    }

    @Test
    @DisplayName("isFivefoldRepetition: 5회 동형 반복이 되면 true 를 반환해야 한다")
    void isFivefoldRepetition() {
        ChessGame chessGame = ChessGame.startPosition();
        assertFalse(chessGame.isFivefoldRepetition());

        for (int i = 0; i < 2; i++) {
            chessGame.makeMoveLan("g1f3");
            chessGame.makeMoveLan("g8f6");
            chessGame.makeMoveLan("f3g1");
            chessGame.makeMoveLan("f6g8");
        }
        chessGame.makeMoveLan("g1f3");
        chessGame.makeMoveLan("g8f6");
        chessGame.makeMoveLan("f3g1");
        chessGame.makeMoveLan("f6g8");

        for (int i = 0; i < 3; i++) {
            chessGame.makeMoveLan("g1f3");
            chessGame.makeMoveLan("g8f6");
            chessGame.makeMoveLan("f3g1");
            chessGame.makeMoveLan("f6g8");
        }

        assertTrue(chessGame.isFivefoldRepetition());
    }

    @Test
    @DisplayName("isSeventyFiveMoves: half move 가 150 이상이면 true 를 반환해야 한다")
    void isSeventyFiveMoves() {
        ChessGame chessGame = ChessGame.fromFEN("1n2k3/8/8/8/8/8/8/R3K3 w - - 100 60");
        assertFalse(chessGame.isSeventyFiveMoves());

        chessGame = ChessGame.fromFEN("1n2k3/8/8/8/8/8/8/R3K3 w - - 150 100");
        assertTrue(chessGame.isSeventyFiveMoves());
    }

    @Test
    @DisplayName("getClaimableDrawReason: 클레임 가능한 무승부 사유를 정확히 반환해야 한다")
    void getClaimableDrawReason() {
        ChessGame ongoing = ChessGame.startPosition();
        assertEquals(GameOverReason.NOTGAMEOVER, ongoing.getClaimableDrawReason());

        ChessGame fiftyMoves = ChessGame.fromFEN("1n2k3/8/8/8/8/8/8/R3K3 w - - 100 60");
        assertEquals(GameOverReason.FIFTYMOVES_CLAIM, fiftyMoves.getClaimableDrawReason());

        ChessGame threefold = ChessGame.startPosition();
        for (int i = 0; i < 2; i++) {
            threefold.makeMoveLan("g1f3");
            threefold.makeMoveLan("g8f6");
            threefold.makeMoveLan("f3g1");
            threefold.makeMoveLan("f6g8");
        }
        threefold.makeMoveLan("g1f3");
        threefold.makeMoveLan("g8f6");
        assertEquals(GameOverReason.THREEFOLD_CLAIM, threefold.getClaimableDrawReason());
    }

    @Test
    @DisplayName("claimDraw: 클레임 가능하면 게임을 DRAW 로 종료하고 true 를 반환해야 한다")
    void claimDraw_succeedsWhenClaimable() {
        ChessGame chessGame = ChessGame.fromFEN("1n2k3/8/8/8/8/8/8/R3K3 w - - 100 60");

        boolean claimed = chessGame.claimDraw();

        assertTrue(claimed);
        assertEquals(GameResult.DRAW, chessGame.getGameResult());
        assertEquals(GameOverReason.FIFTYMOVES_CLAIM, chessGame.getGameOverReason());
    }

    @Test
    @DisplayName("claimDraw: 클레임 불가능하면 false 를 반환하고 게임은 진행 중이어야 한다")
    void claimDraw_failsWhenNotClaimable() {
        ChessGame chessGame = ChessGame.startPosition();

        assertFalse(chessGame.claimDraw());
        assertEquals(GameResult.UNKNOWN, chessGame.getGameResult());
    }

    @Test
    @DisplayName("resign: 기권한 쪽의 반대편이 승리해야 한다")
    void resign() {
        ChessGame whiteResigns = ChessGame.startPosition();
        whiteResigns.resign(true);
        assertEquals(GameResult.BLACK_WON, whiteResigns.getGameResult());
        assertEquals(GameOverReason.RESIGNATION, whiteResigns.getGameOverReason());

        ChessGame blackResigns = ChessGame.startPosition();
        blackResigns.resign(false);
        assertEquals(GameResult.WHITE_WON, blackResigns.getGameResult());
        assertEquals(GameOverReason.RESIGNATION, blackResigns.getGameOverReason());
    }

    @Test
    @DisplayName("agreeDraw: 합의 무승부로 게임이 종료되어야 한다")
    void agreeDraw() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.agreeDraw();

        assertEquals(GameResult.DRAW, chessGame.getGameResult());
        assertEquals(GameOverReason.AGREEMENT_DRAW, chessGame.getGameOverReason());
    }

    @Test
    @DisplayName("timeOver: 시간이 초과된 쪽의 반대편이 승리해야 한다")
    void timeOver() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.timeOver(true);

        assertEquals(GameResult.BLACK_WON, chessGame.getGameResult());
        assertEquals(GameOverReason.TIMEOVER, chessGame.getGameOverReason());
    }

    @Test
    @DisplayName("adjudication: 외부 판정 결과로 게임이 종료되어야 한다")
    void adjudication() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.adjudication(GameResult.DRAW);

        assertEquals(GameResult.DRAW, chessGame.getGameResult());
        assertEquals(GameOverReason.ADJUDICATION, chessGame.getGameOverReason());
    }

    @Test
    @DisplayName("forceEndGameExternal: 외부에서 임의의 result/reason 조합으로 게임을 종료할 수 있어야 한다")
    void forceEndGameExternal() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.forceEndGameExternal(GameResult.WHITE_WON, GameOverReason.RESIGNATION);

        assertEquals(GameResult.WHITE_WON, chessGame.getGameResult());
        assertEquals(GameOverReason.RESIGNATION, chessGame.getGameOverReason());
    }

    @Test
    @DisplayName("이미 끝난 게임을 다시 강제 종료하려 하면 IllegalStateException 이 발생해야 한다")
    void forceEndGame_alreadyOver_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.resign(true);

        assertThrows(IllegalStateException.class, () -> chessGame.agreeDraw());
    }

    @Test
    @DisplayName("getHeaders / setHeader: 헤더를 설정하고 조회할 수 있어야 한다")
    void headers() {
        ChessGame chessGame = ChessGame.startPosition();

        Map<String, String> defaultHeaders = chessGame.getHeaders();
        assertEquals("?", defaultHeaders.get("Event"));

        chessGame.setHeader("Event", "My Tournament");
        chessGame.setHeader("White", "Alice");

        Map<String, String> updated = chessGame.getHeaders();
        assertEquals("My Tournament", updated.get("Event"));
        assertEquals("Alice", updated.get("White"));

        updated.put("Event", "Tampered");
        assertEquals("My Tournament", chessGame.getHeaders().get("Event"));
    }

    @Test
    @DisplayName("getCurrentMoveInfo: 현재 위치한 수의 정보를 반환하고, 시작 위치에서는 예외가 발생해야 한다")
    void getCurrentMoveInfo() {
        ChessGame chessGame = ChessGame.startPosition();

        assertThrows(MoveNotFoundException.class, chessGame::getCurrentMoveInfo);

        chessGame.makeMoveLan("e2e4");
        assertEquals("e2e4", chessGame.getCurrentMoveInfo().toLanString());
    }

    @Test
    @DisplayName("getFullMove / getHalfMove: full move 와 half move 값을 정확히 반환해야 한다")
    void getFullMoveAndGetHalfMove() {
        ChessGame chessGame = ChessGame.startPosition();
        assertEquals(1, chessGame.getFullMove());
        assertEquals(0, chessGame.getHalfMove());

        chessGame.makeMoveLan("e2e4");
        assertEquals(1, chessGame.getFullMove());
        assertEquals(0, chessGame.getHalfMove(), "폰을 움직였으므로 half move 는 0으로 초기화되어야 합니다.");

        chessGame.makeMoveLan("g8f6");
        assertEquals(2, chessGame.getFullMove());
        assertEquals(1, chessGame.getHalfMove(), "기물을 움직였으므로 half move 가 증가해야 합니다.");
    }

    @Test
    @DisplayName("getGameVariant / isChess960: 게임 변형과 chess960 여부를 정확히 반환해야 한다")
    void getGameVariantAndIsChess960() {
        ChessGame standard = ChessGame.startPosition();
        assertEquals(GameVariant.STANDARD, standard.getGameVariant());
        assertFalse(standard.isChess960());

        ChessGame crazyHouse960 = ChessGame.fromFEN(START_FEN, true, GameVariant.CRAZY_HOUSE);
        assertEquals(GameVariant.CRAZY_HOUSE, crazyHouse960.getGameVariant());
        assertTrue(crazyHouse960.isChess960());
    }

    @Test
    @DisplayName("getStartPositionFEN: 시작 포지션 FEN을 그대로 반환해야 한다")
    void getStartPositionFEN() {
        ChessGame chessGame = ChessGame.fromFEN(SCHOLARS_MATE_FEN);
        assertEquals(SCHOLARS_MATE_FEN, chessGame.getStartPositionFEN());

        ChessGame fromStart = ChessGame.startPosition();
        assertEquals(START_FEN, fromStart.getStartPositionFEN());

        fromStart.makeMoveLan("e2e4");
        assertEquals(START_FEN, fromStart.getStartPositionFEN(), "수를 두어도 시작 포지션 FEN은 변하면 안 됩니다.");
    }

    @Test
    @DisplayName("getCheckCount: 3 Check 변형에서 각 진영의 체크 횟수를 배열로 반환해야 한다")
    void getCheckCount() {
        ChessGame chessGame = ChessGame.fromFEN("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 +1+1",
                GameVariant.THREE_CHECK);

        int[] checkCount = chessGame.getCheckCount();

        assertArrayEquals(new int[]{1, 1}, checkCount);
    }

    @Test
    @DisplayName("getCheckCount: 3 Check 변형이 아니면 VariantNotMatchException 이 발생해야 한다")
    void getCheckCount_wrongVariant_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(VariantNotMatchException.class, chessGame::getCheckCount);
    }

    @Test
    @DisplayName("getPieceCount / getWhitePieceCount / getBlackPieceCount: 기물 개수를 정확히 세어야 한다")
    void pieceCounts() {
        ChessGame chessGame = ChessGame.startPosition();

        assertEquals(32, chessGame.getPieceCount());
        assertEquals(16, chessGame.getWhitePieceCount());
        assertEquals(16, chessGame.getBlackPieceCount());

        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("d7d5");
        chessGame.makeMoveLan("e4d5");

        assertEquals(31, chessGame.getPieceCount());
        assertEquals(16, chessGame.getWhitePieceCount());
        assertEquals(15, chessGame.getBlackPieceCount());
    }

    @Test
    @DisplayName("getTotalNodeCount: 히스토리 트리에 존재하는 전체 노드(루트 포함) 개수를 반환해야 한다")
    void getTotalNodeCount() {
        ChessGame chessGame = ChessGame.startPosition();
        assertEquals(1, chessGame.getTotalNodeCount(), "루트 노드 하나만 있어야 합니다.");

        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");
        assertEquals(3, chessGame.getTotalNodeCount());

        chessGame.unmakeMove();
        chessGame.makeMoveLan("d7d5");
        assertEquals(4, chessGame.getTotalNodeCount(), "변이도 노드 개수에 포함되어야 합니다.");
    }

    @Test
    @DisplayName("getListeners: 등록/해제된 리스너 목록을 정확히 반영해야 한다")
    void getListeners() {
        ChessGame chessGame = ChessGame.startPosition();
        assertTrue(chessGame.getListeners().isEmpty());

        ChessGameListener listener = new NoopListener();
        chessGame.addChessGameListener(listener);

        assertEquals(1, chessGame.getListeners().size());
        assertSame(listener, chessGame.getListeners().getFirst());

        chessGame.removeChessGameListener(listener);
        assertTrue(chessGame.getListeners().isEmpty());
    }

    @Test
    @DisplayName("setListenerExceptionHandler: 리스너가 예외를 던지면 등록한 핸들러가 호출되고, 다른 리스너 호출은 계속되어야 한다")
    void setListenerExceptionHandler() {
        ChessGame chessGame = ChessGame.startPosition();

        AtomicInteger handlerCallCount = new AtomicInteger();
        BiConsumer<ChessGameListener, Throwable> handler = (listener, e) -> handlerCallCount.incrementAndGet();
        chessGame.setListenerExceptionHandler(handler);

        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onMoveMade(ChessGame source, MoveInfo moveInfo) {
                throw new RuntimeException("boom");
            }
        });

        List<String> secondListenerEvents = new ArrayList<>();
        chessGame.addChessGameListener(new NoopListener() {
            @Override
            public void onMoveMade(ChessGame source, MoveInfo moveInfo) {
                secondListenerEvents.add("made");
            }
        });

        chessGame.makeMoveLan("e2e4");

        assertEquals(1, handlerCallCount.get(), "예외를 던진 리스너에 대해 핸들러가 호출되어야 합니다.");
        assertEquals(1, secondListenerEvents.size(), "한 리스너가 예외를 던져도 다른 리스너는 계속 호출되어야 합니다.");
    }

    @Test
    @DisplayName("setListenerExceptionHandler: null 을 전달하면 NullPointerException 이 발생해야 한다")
    void setListenerExceptionHandler_null_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(NullPointerException.class, () -> chessGame.setListenerExceptionHandler(null));
    }

    @Test
    @DisplayName("samePosition: 같은 포지션이면 true, 다르면 false, null 이면 false 를 반환해야 한다")
    void samePosition() {
        ChessGame a = ChessGame.startPosition();
        ChessGame b = ChessGame.startPosition();
        a.makeMoveLan("e2e4");
        b.makeMoveLan("e2e4");

        assertTrue(a.samePosition(b));
        assertTrue(a.samePosition(a));
        assertFalse(a.samePosition(null));

        b.makeMoveLan("e7e5");
        assertFalse(a.samePosition(b));
    }

    @Test
    @DisplayName("getPurePGN: 주석/시계 등 부가 정보 없이 순수 기보만 포함해야 한다")
    void getPurePGN() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        chessGame.setCurrentMoveClock(0, 4, 55);
        chessGame.setCurrentMoveComment("Good opening move");

        String purePgn = chessGame.getPurePGN();

        assertTrue(purePgn.contains("e4"));
        assertFalse(purePgn.contains("%clk"), "getPurePGN 은 시계 데이터를 포함하면 안 됩니다.");
        assertFalse(purePgn.contains("Good opening move"), "getPurePGN 은 주석을 포함하면 안 됩니다.");
    }

    @Test
    @DisplayName("setCurrentMoveCal / setCurrentMoveCsl / setCurrentMoveEval: 현재 수에 주석 데이터를 설정할 수 있어야 한다")
    void currentMoveAnnotations() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");

        chessGame.setCurrentMoveCal("Gg1f3");
        chessGame.setCurrentMoveCsl("Ge4");
        chessGame.setCurrentMoveEval("0.35");

        MoveNodeDTO move1 = chessGame.getRootNode().children().getFirst();
        assertEquals("Gg1f3", move1.annotation().cal());
        assertEquals("Ge4", move1.annotation().csl());
        assertEquals("0.35", move1.annotation().eval());
    }

    @Test
    @DisplayName("setCurrentMoveCal / setCurrentMoveCsl / setCurrentMoveEval / setCurrentMoveComment: 루트(시작) 위치에도 주석 데이터를 설정할 수 있어야 한다")
    void currentMoveAnnotations_atRoot_isApplied() {
        ChessGame chessGame = ChessGame.startPosition();

        chessGame.setCurrentMoveCal("Gg1f3");
        chessGame.setCurrentMoveCsl("Ge4");
        chessGame.setCurrentMoveEval("0.35");
        chessGame.setCurrentMoveComment("Starting position");

        MoveNodeDTO root = chessGame.getRootNode();
        assertEquals("Gg1f3", root.annotation().cal());
        assertEquals("Ge4", root.annotation().csl());
        assertEquals("0.35", root.annotation().eval());
        assertEquals("Starting position", root.annotation().comment());
    }

    @Test
    @DisplayName("setMoveCalAt / setMoveCslAt / setMoveEvalAt / setMoveCommentAt: 루트 노드 id 를 대상으로도 주석 데이터를 설정할 수 있어야 한다")
    void moveAnnotationsAtNode_atRoot_isApplied() {
        ChessGame chessGame = ChessGame.startPosition();
        long rootId = chessGame.getRootNode().id();

        chessGame.setMoveCalAt(rootId, "Gg1f3");
        chessGame.setMoveCslAt(rootId, "Ge4");
        chessGame.setMoveEvalAt(rootId, "0.35");
        chessGame.setMoveCommentAt(rootId, "Starting position");

        MoveNodeDTO root = chessGame.getRootNode();
        assertEquals("Gg1f3", root.annotation().cal());
        assertEquals("Ge4", root.annotation().csl());
        assertEquals("0.35", root.annotation().eval());
        assertEquals("Starting position", root.annotation().comment());
    }

    @Test
    @DisplayName("setCurrentMoveClock / setTimeStamp 등: csl/cal/eval/comment 와 달리 clk/timeStamp 는 루트에서 여전히 막혀야 한다")
    void clockAndTimeStamp_atRoot_stillThrows() {
        ChessGame chessGame = ChessGame.startPosition();
        long rootId = chessGame.getRootNode().id();

        assertThrows(ClockException.class, () -> chessGame.setCurrentMoveClock(0, 4, 55));
        assertThrows(ClockException.class, () -> chessGame.setTimeStamp("00:00:12"));
        assertThrows(ClockException.class, () -> chessGame.setMoveClockAt(rootId, 0, 4, 55));
        assertThrows(ClockException.class, () -> chessGame.setTimeStampAt(rootId, "00:00:12"));
    }

    @Test
    @DisplayName("setMoveCalAt / setMoveCslAt / setMoveEvalAt / setMoveCommentAt: 특정 노드에 직접 주석 데이터를 설정할 수 있어야 한다")
    void moveAnnotationsAtNode() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        long nodeId = chessGame.getCurrentNodeId();

        chessGame.setMoveCalAt(nodeId, "Gg1f3");
        chessGame.setMoveCslAt(nodeId, "Ge4");
        chessGame.setMoveEvalAt(nodeId, "0.35");
        chessGame.setMoveCommentAt(nodeId, "Good opening move");

        MoveNodeDTO move1 = chessGame.getRootNode().children().getFirst();
        assertEquals("Gg1f3", move1.annotation().cal());
        assertEquals("Ge4", move1.annotation().csl());
        assertEquals("0.35", move1.annotation().eval());
        assertEquals("Good opening move", move1.annotation().comment());
    }

    @Test
    @DisplayName("setMoveCalAt 등: 존재하지 않는 nodeId 면 MoveNotFoundException 이 발생해야 한다")
    void moveAnnotationsAtNode_unknownNode_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(MoveNotFoundException.class, () -> chessGame.setMoveCalAt(999_999L, "Gg1f3"));
        assertThrows(MoveNotFoundException.class, () -> chessGame.setMoveCslAt(999_999L, "Ge4"));
        assertThrows(MoveNotFoundException.class, () -> chessGame.setMoveEvalAt(999_999L, "0.35"));
        assertThrows(MoveNotFoundException.class, () -> chessGame.setMoveCommentAt(999_999L, "comment"));
    }

    @Test
    @DisplayName("setCurrentMoveClockMilliSeconds: 밀리초 단위로 현재 수의 시계 데이터를 설정할 수 있어야 한다")
    void setCurrentMoveClockMilliSeconds() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");

        chessGame.setCurrentMoveClockMilliSeconds(5_000);

        MoveNodeDTO move1 = chessGame.getRootNode().children().getFirst();
        assertEquals("0:00:05.00", move1.annotation().clk());
    }

    @Test
    @DisplayName("setCurrentMoveClockMilliSeconds: 루트(시작) 위치에서는 ClockException 이 발생해야 한다")
    void setCurrentMoveClockMilliSeconds_atRoot_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(ClockException.class, () -> chessGame.setCurrentMoveClockMilliSeconds(5_000));
    }

    @Test
    @DisplayName("setMoveClockAt(시/분/초): 특정 노드에 시/분/초 단위로 시계 데이터를 설정할 수 있어야 한다")
    void setMoveClockAt_hoursMinutesSeconds() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        long nodeId = chessGame.getCurrentNodeId();

        chessGame.setMoveClockAt(nodeId, 0, 4, 55);

        MoveNodeDTO move1 = chessGame.getRootNode().children().getFirst();
        assertEquals("0:04:55", move1.annotation().clk());
    }

    @Test
    @DisplayName("setMoveClockAt(초): 특정 노드에 초 단위로 시계 데이터를 설정할 수 있어야 한다")
    void setMoveClockAt_seconds() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        long nodeId = chessGame.getCurrentNodeId();

        chessGame.setMoveClockAt(nodeId, 295);

        MoveNodeDTO move1 = chessGame.getRootNode().children().getFirst();
        assertEquals("0:04:55", move1.annotation().clk());
    }

    @Test
    @DisplayName("setMoveClockAt(문자열): 특정 노드에 문자열 형식으로 시계 데이터를 설정할 수 있어야 한다")
    void setMoveClockAt_string() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        long nodeId = chessGame.getCurrentNodeId();

        chessGame.setMoveClockAt(nodeId, "0:04:55");

        MoveNodeDTO move1 = chessGame.getRootNode().children().getFirst();
        assertEquals("0:04:55", move1.annotation().clk());
    }

    @Test
    @DisplayName("setMoveClockAt: 루트 노드를 대상으로 하면 ClockException 이 발생해야 한다")
    void setMoveClockAt_rootNode_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        long rootId = chessGame.getRootNode().id();

        assertThrows(ClockException.class, () -> chessGame.setMoveClockAt(rootId, 0, 4, 55));
    }

    @Test
    @DisplayName("setMoveClockMilliSecondsAt: 특정 노드에 밀리초 단위로 시계 데이터를 설정할 수 있어야 한다")
    void setMoveClockMilliSecondsAt() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        long nodeId = chessGame.getCurrentNodeId();

        chessGame.setMoveClockMilliSecondsAt(nodeId, 5_000);

        MoveNodeDTO move1 = chessGame.getRootNode().children().getFirst();
        assertEquals("0:00:05.00", move1.annotation().clk());
    }

    @Test
    @DisplayName("setTimeStamp / setTimeStampAt: 현재 수 및 특정 노드에 타임스탬프를 설정할 수 있어야 한다")
    void timeStamps() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        long nodeId = chessGame.getCurrentNodeId();

        chessGame.setTimeStamp("00:00:12");
        MoveNodeDTO move1 = chessGame.getRootNode().children().getFirst();
        assertEquals("00:00:12", move1.annotation().timeStamp());

        chessGame.setTimeStampAt(nodeId, "00:00:20");
        move1 = chessGame.getRootNode().children().getFirst();
        assertEquals("00:00:20", move1.annotation().timeStamp());
    }

    @Test
    @DisplayName("setTimeStamp: 루트(시작) 위치에서는 ClockException 이 발생해야 한다")
    void setTimeStamp_atRoot_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(ClockException.class, () -> chessGame.setTimeStamp("00:00:12"));
    }

    @Test
    @DisplayName("setTimeStampAt: 존재하지 않는 nodeId 면 MoveNotFoundException 이 발생해야 한다")
    void setTimeStampAt_unknownNode_throws() {
        ChessGame chessGame = ChessGame.startPosition();
        assertThrows(MoveNotFoundException.class, () -> chessGame.setTimeStampAt(999_999L, "00:00:12"));
    }

    @Test
    @DisplayName("canDropPiece: 크레이지하우스에서 드랍 가능 여부를 실제로 드랍하지 않고 판별해야 한다")
    void canDropPiece() {
        String crazyFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR[Qp] w KQkq - 0 1";
        ChessGame chessGame = ChessGame.fromFEN(crazyFen, GameVariant.CRAZY_HOUSE);

        assertTrue(chessGame.canDropPiece(PieceType.QUEEN, Square.e4));
        assertFalse(chessGame.canDropPiece(PieceType.QUEEN, Square.e2), "e2 는 이미 백 폰이 있으므로 드랍할 수 없습니다.");
        assertFalse(chessGame.canDropPiece(PieceType.PAWN, Square.e4), "백 포켓에는 폰이 없으므로 드랍할 수 없습니다.");

        assertEquals(Piece.NONE, chessGame.getPieceOnSquare(Square.e4), "canDropPiece 는 실제로 드랍하면 안 됩니다.");
    }

    @Test
    @DisplayName("toAscii: 보드를 나타내는 비어있지 않은 문자열을 반환해야 한다")
    void toAscii() {
        ChessGame chessGame = ChessGame.startPosition();
        String ascii = chessGame.toAscii();

        assertNotNull(ascii);
        assertFalse(ascii.isBlank());
    }

    @Test
    @DisplayName("toString: getFEN() 과 동일한 값을 반환해야 한다")
    void toStringReturnsFen() {
        ChessGame chessGame = ChessGame.startPosition();
        assertEquals(chessGame.getFEN(), chessGame.toString());

        chessGame.makeMoveLan("e2e4");
        assertEquals(chessGame.getFEN(), chessGame.toString());
    }

    @Test
    @DisplayName("printBoard(PrintStream): toAscii() 와 동일한 내용을 출력해야 한다")
    void printBoardToStream() {
        ChessGame chessGame = ChessGame.startPosition();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        chessGame.printBoard(new PrintStream(buffer));

        assertEquals(chessGame.toAscii() + System.lineSeparator(), buffer.toString());
    }

    @Test
    @DisplayName("printBoard(): 표준 출력으로 예외 없이 출력되어야 한다")
    void printBoardToStdout() {
        ChessGame chessGame = ChessGame.startPosition();
        assertDoesNotThrow((Executable) chessGame::printBoard);
    }

    @Test
    @DisplayName("printHistory(PrintStream): 루트와 각 수의 SAN 이 출력에 포함되어야 한다")
    void printHistoryToStream() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        chessGame.makeMoveLan("e7e5");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        chessGame.printHistory(new PrintStream(buffer));

        String output = buffer.toString();
        assertTrue(output.contains("ROOT"));
        assertTrue(output.contains("e4"));
        assertTrue(output.contains("e5"));
    }

    @Test
    @DisplayName("printHistory(showNodeId=true): 각 노드의 id 가 함께 출력되어야 한다")
    void printHistoryWithNodeId() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");
        long nodeId = chessGame.getCurrentNodeId();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        chessGame.printHistory(new PrintStream(buffer), true);

        assertTrue(buffer.toString().contains("#" + nodeId));
    }

    @Test
    @DisplayName("printHistory(maxNodeSize, PrintStream): 노드 개수 제한을 지정해서 출력할 수 있어야 한다")
    void printHistoryWithMaxNodeSize() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        chessGame.printHistory(10, new PrintStream(buffer));

        assertTrue(buffer.toString().contains("e4"));
    }

    @Test
    @DisplayName("printHistory() / printHistory(showNodeId) / printHistory(maxNodeSize) / printHistory(maxNodeSize, showNodeId): 표준 출력으로 예외 없이 출력되어야 한다")
    void printHistoryDefaultOverloads() {
        ChessGame chessGame = ChessGame.startPosition();
        chessGame.makeMoveLan("e2e4");

        assertDoesNotThrow((Executable) chessGame::printHistory);
        assertDoesNotThrow(() -> chessGame.printHistory(true));
        assertDoesNotThrow(() -> chessGame.printHistory(10));
        assertDoesNotThrow(() -> chessGame.printHistory(10, true));
    }

    @Test
    @DisplayName("perft: 시작 포지션에서 depth 1 로 실행하면 예외 없이 결과를 반환해야 한다")
    void perft() {
        ChessGame chessGame = ChessGame.startPosition();

        PerftResult result = chessGame.perft(1, 1, true);

        assertNotNull(result);
    }

    @Test
    @DisplayName("perft(depth) / perft(depth, concurrency) / perft(depth, concurrency, silent, bulkCounting): 오버로드들이 예외 없이 동작해야 한다")
    void perftOverloads() {
        ChessGame chessGame = ChessGame.startPosition();

        assertNotNull(chessGame.perft(1));
        assertNotNull(chessGame.perft(1, 1));
        assertNotNull(chessGame.perft(1, 1, true, true));
    }
}