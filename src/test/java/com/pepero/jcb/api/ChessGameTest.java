package com.pepero.jcb.api;

import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.dto.PGNGame;
import com.pepero.jcb.api.enums.*;
import com.pepero.jcb.api.exception.EmptyMoveUndoException;
import com.pepero.jcb.api.exception.IllegalMoveException;
import com.pepero.jcb.api.parse.ConvertStringMoveUtils;
import com.pepero.jcb.constant.BoardSquares;
import com.pepero.jcb.core.*;
import com.pepero.jcb.encode.EncodeMove;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ChessGameTest {
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String SCHOLARS_MATE_FEN = "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4";
    private static final String STALEMATE_FEN = "7k/5K2/6Q1/8/8/8/8/8 b - - 0 1";

    @Test
    @DisplayName("초기 FEN 문자열을 정확히 반환해야 한다")
    void getFEN() {
        ChessGame chessGame = new ChessGame();

        assertEquals(START_FEN, chessGame.getFEN());
    }

    @Test
    @DisplayName("LAN 문자열로 수를 두면 상태가 변해야 한다")
    void makeMove() {
        ChessGame chessGame = new ChessGame();
        chessGame.makeMove("e2e4");
        assertFalse(chessGame.isEmpty(Square.e4));
        assertTrue(chessGame.isEmpty(Square.e2));
    }

    @Test
    @DisplayName("불법수를 두면 IllegalMoveException 이 터져야 한다")
    void testIllegalMove() {
        ChessGame chessGame = new ChessGame();

        assertThrows(IllegalMoveException.class, () -> chessGame.makeMove("e2f3"));
    }

    @Test
    @DisplayName("Undo 를 하면 기보가 삭제되고 보드가 원상복구되어야 한다")
    void unmakeMove() {
        ChessGame chessGame = new ChessGame();
        chessGame.makeMove("e2e4");
        assertEquals(1, chessGame.getMoveHistory().size());

        MoveInfo undoneMove = chessGame.unmakeMove();
        assertEquals(0, chessGame.getMoveHistory().size());
        assertEquals(START_FEN, chessGame.getFEN());
        assertNotNull(undoneMove);
    }

    @Test
    @DisplayName("아무 수도 두지 않고 무르기를 하면 예외가 발생해야 한다")
    void unmakeMoveEmpty() {
        ChessGame chessGame = new ChessGame();
        assertThrows(EmptyMoveUndoException.class, chessGame::unmakeMove);
    }

    @Test
    @DisplayName("턴이 번갈아가며 바뀌어야 한다")
    void getTurn() {
        ChessGame chessGame = new ChessGame();
        assertTrue(chessGame.getTurn());

        chessGame.makeMove("e2e4");
        assertFalse(chessGame.getTurn());
    }

    @Test
    @DisplayName("기물을 잡으면 잡힌 기물 목록에 추가되어야 한다")
    void getCapturedPieces() {
        ChessGame chessGame = new ChessGame();

        chessGame.makeMove("e2e4");
        chessGame.makeMove("d7d5");
        chessGame.makeMove("e4d5");

        Map<PieceType, Integer> capturedByWhite = chessGame.getCapturedPieces(true);
        assertEquals(1, capturedByWhite.get(PieceType.PAWN));
    }

    @Test
    @DisplayName("기물 점수가 기물 이득에 따라 변해야 한다")
    void getPieceScore() {
        ChessGame chessGame = new ChessGame();
        assertEquals(0, chessGame.getPieceScore());

        chessGame.makeMove("e2e4");
        chessGame.makeMove("d7d5");
        chessGame.makeMove("e4d5");

        assertEquals(1, chessGame.getPieceScore());
    }

    @Test
    @DisplayName("특정 칸에 있는 기물을 정확히 가져와야 한다")
    void getPieceOnSquare() {
        ChessGame chessGame = new ChessGame();
        assertEquals(Piece.WHITE_QUEEN, chessGame.getPieceOnSquare(Square.d1));
        assertEquals(Piece.BLACK_ROOK, chessGame.getPieceOnSquare(Square.h8));
        assertEquals(Piece.NONE, chessGame.getPieceOnSquare(Square.e4));
    }

    @Test
    @DisplayName("초기 상태의 전체 합법수는 정확히 20개여야 한다")
    void getLegalMoves() {
        ChessGame chessGame = new ChessGame();
        List<MoveInfo> moves = chessGame.getLegalMoves();
        assertEquals(20, moves.size());
    }

    @Test
    @DisplayName("Source Square 에서 합법수만 필터링해서 가져와야 한다")
    void getLegalMovesForSource() {
        ChessGame chessGame = new ChessGame();
        List<MoveInfo> e2Moves = chessGame.getLegalMovesForSource(Square.e2);

        assertEquals(2, e2Moves.size());
        assertEquals(Square.e3, e2Moves.get(0).targetSquare());
        assertEquals(Square.e4, e2Moves.get(1).targetSquare());
    }

    @Test
    @DisplayName("Target Square 에서 합법수만 필터링해서 가져와야 한다")
    void getLegalMovesForTarget() {
        ChessGame chessGame = new ChessGame();
        List<MoveInfo> e2Moves = chessGame.getLegalMovesForTarget(Square.e4);

        assertEquals(1, e2Moves.size());
        assertEquals(Square.e4, e2Moves.getFirst().targetSquare());
    }

    @Test
    @DisplayName("초기 보드의 기물 개수는 정확히 32개여야 한다")
    void getBoardStateMap() {
        ChessGame chessGame = new ChessGame();
        Map<Square, Piece> boardMap = chessGame.getBoardStateMap();
        assertEquals(32, boardMap.size());
    }

    @Test
    @DisplayName("체크메이트 상태를 정확히 판별해야 한다")
    void isCheckmate() {
        ChessGame chessGame = new ChessGame(SCHOLARS_MATE_FEN);
        assertTrue(chessGame.isCheck());
        assertTrue(chessGame.isCheckmate());
        assertEquals(GameOverReason.CHECKMATE, chessGame.isGameOver());
    }

    @Test
    @DisplayName("체크 하는 기물을 정확히 봐야 한다.")
    void getChecker() {
        Chessboard chessboard = new Chessboard(SCHOLARS_MATE_FEN);

        int val = ChessboardUtils.getChecker(chessboard);

        assertEquals(13, val & 0x3f);
        assertEquals(1, val >>> 12 & 3);
    }

    @Test
    @DisplayName("스테일메이트 상태를 정확히 판별해야 한다")
    void isStalemate() {
        ChessGame chessGame = new ChessGame(STALEMATE_FEN);
        assertFalse(chessGame.isCheck());
        assertTrue(chessGame.isStalemate());
        assertEquals(GameOverReason.STALEMATE, chessGame.isGameOver());
    }

    @Test
    @DisplayName("3회 동형 반복 무승부를 판별해야 한다")
    void isThreefoldRepetition() {
        ChessGame chessGame = new ChessGame();

        for (int i = 0; i < 2; i++) {
            chessGame.makeMove("g1f3");
            chessGame.makeMove("g8f6");
            chessGame.makeMove("f3g1");
            chessGame.makeMove("f6g8");
        }
        chessGame.makeMove("g1f3");
        chessGame.makeMove("g8f6");

        assertTrue(chessGame.isThreefoldRepetition());
        assertEquals(GameOverReason.THREEFOLD, chessGame.isGameOver());
    }

    @Test
    @DisplayName("LAN 을 SAN 으로 정상 변환해야 한다")
    void toSan() {
        ChessGame chessGame = new ChessGame();
        assertEquals("e4", chessGame.toSan("e2e4"));
        assertEquals("Nf3", chessGame.toSan("g1f3"));
    }

    @Test
    @DisplayName("SAN 을 LAN 으로 정상 변환해야 한다")
    void toLan() {
        ChessGame chessGame = new ChessGame();

        assertEquals("e2e4", chessGame.toLanString("e4"));
        assertEquals("g1f3", chessGame.toLanString("Nf3"));

        chessGame.makeMove("e2e4");
        chessGame.makeMove("d7d5");

        assertEquals("e4d5", chessGame.toLanString("exd5"));
    }

    @Test
    @DisplayName("되돌리기와 다시 되돌리기가 정상 작동 해야 한다.")
    void undoRedo() {
        ChessGame chessGame = new ChessGame();

        String start = chessGame.getFEN();

        chessGame.makeMove("e2e4");
        chessGame.makeMove("e7e5");
        chessGame.makeMove("g1f3");

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
        ChessGame chessGame = new ChessGame();
        chessGame.makeMove("e2e4");
        chessGame.makeMove("e7e5");
        chessGame.unmakeMove();

        chessGame.makeMove("d7d5");
        chessGame.unmakeMove();

        chessGame.remakeMove(1);

        ChessGame test = new ChessGame();
        test.makeMove("e2e4");
        test.makeMove("d7d5");

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
        ChessGame chessGame = new ChessGame();
        chessGame.makeMove("e2e4");
        chessGame.makeMove("e7e5");
        chessGame.makeMove("g1f3");
        chessGame.makeMove("g8f6");
        chessGame.makeMove("b1c3");
        chessGame.makeMove("b8c6");
        chessGame.makeMove("f1c4");
        chessGame.makeMove("f8c5");
        chessGame.makeMove("e1g1");
        chessGame.makeMove("e8g8");

        assertEquals("r1bq1rk1/pppp1ppp/2n2n2/2b1p3/2B1P3/2N2N2/PPPP1PPP/R1BQ1RK1 w - - 8 6",
                chessGame.getFEN());
    }

    @Test
    @DisplayName("PGN 을 정확히 파싱해야 한다.")
    void pgnAdvanced() {
        ChessGame chessGame = new ChessGame();
        String pgnString = """
                [Event "?"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]
                [Link "https://www.chess.com/analysis/game/pgn/2wCTfp3KJn/analysis"]
                
                1. e4 e5 2. Nf3 Nc6 3. Bc4 (3. Nc3 Nf6 4. d3 (4. Bb5) 4... d6 $6) 3... Bc5 *
                """;

        PGNGame pgnGame = chessGame.loadPGN(pgnString);

        assertNotNull(pgnGame, "파싱 결과는 null 이 아니어야 합니다.");
        assertEquals("https://www.chess.com/analysis/game/pgn/2wCTfp3KJn/analysis", pgnGame.headers().get("Link"));
        assertEquals("?", pgnGame.headers().get("White"));
        assertEquals(GameResult.UNKNOWN, pgnGame.matchResult(), "UNKNOWN 이어야 한다");

        ChessGame.MoveNodeDTO root = pgnGame.rootNode();

        ChessGame.MoveNodeDTO e4 = root.children().getFirst();
        assertEquals("e4", e4.san());

        ChessGame.MoveNodeDTO e5 = e4.children().getFirst();
        ChessGame.MoveNodeDTO nf3 = e5.children().getFirst();
        ChessGame.MoveNodeDTO nc6 = nf3.children().getFirst();
        assertEquals("Nc6", nc6.san());

        assertEquals(2, nc6.children().size(), "총 2개의 자식이 있어야 한다");

        ChessGame.MoveNodeDTO bc4 = nc6.children().getFirst();
        assertEquals("Bc4", bc4.san());
        ChessGame.MoveNodeDTO bc5 = bc4.children().getFirst();
        assertEquals("Bc5", bc5.san());

        ChessGame.MoveNodeDTO nc3_var = nc6.children().get(1);
        assertEquals("Nc3", nc3_var.san());

        ChessGame.MoveNodeDTO nf6_var = nc3_var.children().getFirst();

        assertEquals(2, nf6_var.children().size(), "Nf6 이후 변화수 내에서 또 한 번 분기되어야 한다.");

        ChessGame.MoveNodeDTO d3_var = nf6_var.children().getFirst();
        assertEquals("d3", d3_var.san());

        ChessGame.MoveNodeDTO d6_var = d3_var.children().getFirst();
        assertEquals("d6", d6_var.san());
        assertEquals("$6", d6_var.nag(), "d6 수에는 $6 평가 기호가 정확히 들어가야 한다.");

        ChessGame.MoveNodeDTO bb5_var = nf6_var.children().get(1);
        assertEquals("Bb5", bb5_var.san());
    }

    @Test
    @DisplayName("PGN을 정확히 파싱하고 다시 동일한 텍스트로 내보낼 수 있어야 한다.")
    void pgnConvert() {
        ChessGame chessGame = new ChessGame();
        String pgnString = """
                [Event "?"]
                [Site "?"]
                [Date "????.??.??"]
                [Round "?"]
                [White "?"]
                [Black "?"]
                [Result "*"]
                [Link "https://www.chess.com/analysis/game/pgn/2wCTfp3KJn/analysis"]
                
                1. e4 e5 2. Nf3 Nc6 3. Bc4 ( 3. Nc3 Nf6 4. d3 ( 4. Bb5 ) 4... d6 $6 ) 3... Bc5 *
                """;

        chessGame.loadPGN(pgnString);

        assertEquals("https://www.chess.com/analysis/game/pgn/2wCTfp3KJn/analysis", chessGame.getHeaders().get("Link"));
        assertEquals("*", chessGame.getHeaders().get("Result"));

        String exportedPgn = chessGame.getPGN();

        System.out.println("=== Exported PGN Result ===");
        System.out.println(exportedPgn);
        System.out.println("===========================");

        assertTrue(exportedPgn.contains("1. e4 e5 2. Nf3 Nc6 3. Bc4"), "초반 메인 라인이 깨졌습니다.");

        assertTrue(exportedPgn.contains("( 3. Nc3 Nf6 4. d3"), "1차 변화수 파싱에 실패했습니다.");
        assertTrue(exportedPgn.contains("4... d6 $6"), "흑의 턴 번호 복구 또는 NAG 파싱에 실패했습니다.");

        assertTrue(exportedPgn.contains("( 4. Bb5 )"), "2차 중첩 변화수 파싱에 실패했습니다.");

        assertTrue(exportedPgn.contains("3... Bc5"), "변화수가 끝난 후 흑의 턴 번호(3...)가 정상적으로 강제 출력되지 않았습니다.");

        assertTrue(exportedPgn.endsWith("*"), "PGN 마지막의 게임 결과 심볼이 누락되었습니다.");
    }

    @Test
    @DisplayName("e1g1 (킹사이드 캐슬링) 입력 시 e1h1 으로 변환해야 한다.")
    public void testCastling() {
        Initializer.init();

        Chessboard board = new Chessboard("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1");

        int encodedMove = ConvertStringMoveUtils.parseLanToEncodedMove(board, "e1g1");

        assertTrue(EncodeMove.getMoveCastling(encodedMove), "캐슬링 플래그가 켜져 있어야 합니다.");

        assertEquals(BoardSquares.h1, EncodeMove.getMoveTarget(encodedMove), "갈 위치가 h1이여야 합니다.");

        MoveGenerator.makeMove(board, encodedMove);

        encodedMove = ConvertStringMoveUtils.parseLanToEncodedMove(board, "e8g8");

        assertTrue(EncodeMove.getMoveCastling(encodedMove), "캐슬링 플래그가 켜져 있어야 합니다.");

        assertEquals(BoardSquares.h8, EncodeMove.getMoveTarget(encodedMove), "갈 위치가 h1이여야 합니다.");
    }

    @Test
    @DisplayName("Chess 960 에서 e1h1 입력시 정상적으로 파싱되어야 한다")
    public void testChess960Castling() {
        Chessboard board = new Chessboard("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1",
                GameVariants.CHESS960);

        int encodedMove = ConvertStringMoveUtils.parseLanToEncodedMove(board, "e1h1");

        assertTrue(EncodeMove.getMoveCastling(encodedMove));
        assertEquals(BoardSquares.h1, EncodeMove.getMoveTarget(encodedMove));
    }

    @Test
    @DisplayName("메인 라인 노드 및 노드 아이디를 통한 체스 보드가 잘 불러와져야 한다.")
    public void jumpToNode() {
        ChessGame chessGame = new ChessGame();
        chessGame.makeMove("e2e4");
        chessGame.makeMove("e7e5");
        chessGame.makeMove("g1f3");
        String fen1 = chessGame.getFEN();
        String uuid1 = chessGame.getCurrentNodeId();

        chessGame.makeMove("b8c6");
        chessGame.unmakeMove();
        chessGame.makeMove("g8f6");
        String fen2 = chessGame.getFEN();
        String uuid2 = chessGame.getCurrentNodeId();

        // e4 e5 Nf3 Nc6 (Nf6)

        chessGame.jumpToNode(uuid1);
        assertEquals(fen1, chessGame.getFEN());

        chessGame.jumpToNode(uuid2);
        assertEquals(fen2, chessGame.getFEN());
    }
}