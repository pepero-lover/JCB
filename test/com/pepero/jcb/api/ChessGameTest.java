package com.pepero.jcb.api;

import com.pepero.jcb.api.exception.EmptyMoveUndoException;
import com.pepero.jcb.api.exception.IllegalMoveException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ChessGameTest {

    // 체스 초기 포지션 FEN 상수
    private static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    // 스콜라스 메이트(4수 체크메이트) 직후의 FEN
    private static final String SCHOLARS_MATE_FEN = "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4";
    // 스테일메이트 상태의 간단한 FEN (흑 차례인데 움직일 곳이 없음)
    private static final String STALEMATE_FEN = "7k/5K2/6Q1/8/8/8/8/8 b - - 0 1";

    @Test
    @DisplayName("초기 FEN 문자열을 정확히 반환해야 한다")
    void getFEN() {
        ChessGame game = new ChessGame();

        // 내부 엔진이 FEN을 정확히 만들어내는지 검증
        assertEquals(START_FEN, game.getFEN());
    }

    @Test
    @DisplayName("LAN 문자열로 수를 두면 상태가 변해야 한다")
    void makeMove() {
        ChessGame game = new ChessGame();
        game.makeMove("e2e4"); // 백의 e폰 2칸 전진
        assertFalse(game.isEmpty(Square.e4)); // e4에 기물이 있어야 함
        assertTrue(game.isEmpty(Square.e2));  // e2는 비워져야 함
    }

    @Test
    @DisplayName("불법수를 두면 IllegalMoveException이 터져야 한다")
    void testIllegalMove() {
        ChessGame game = new ChessGame();
        // 폰이 대각선으로 이동하려 시도 (불법)
        assertThrows(IllegalMoveException.class, () -> game.makeMove("e2f3"));
    }

    @Test
    @DisplayName("수 무르기(Undo)를 하면 기보가 삭제되고 보드가 원상복구되어야 한다")
    void unmakeMove() {
        ChessGame game = new ChessGame();
        game.makeMove("e2e4");
        assertEquals(1, game.getMoveHistory().size());

        MoveInfo undoneMove = game.unmakeMove();
        assertEquals(0, game.getMoveHistory().size()); // 기보가 지워졌는지
        assertEquals(START_FEN, game.getFEN()); // 보드가 완전 초기화되었는지
        assertNotNull(undoneMove); // 방금 취소된 수 정보가 반환되는지
    }

    @Test
    @DisplayName("아무 수도 두지 않고 무르기를 하면 예외가 발생해야 한다")
    void unmakeMoveEmpty() {
        ChessGame game = new ChessGame();
        assertThrows(EmptyMoveUndoException.class, game::unmakeMove);
    }

    @Test
    @DisplayName("턴(Turn)이 번갈아가며 바뀌어야 한다")
    void getTurn() {
        ChessGame game = new ChessGame();
        assertTrue(game.getTurn()); // 처음은 백(true) 차례

        game.makeMove("e2e4");
        assertFalse(game.getTurn()); // 흑(false) 차례로 넘어가야 함
    }

    @Test
    @DisplayName("기물을 잡으면 잡힌 기물(Captured Pieces) 목록에 추가되어야 한다")
    void getCapturedPieces() {
        ChessGame game = new ChessGame();
        // 백이 흑 폰을 잡는 시나리오 (스칸디나비안 디펜스)
        game.makeMove("e2e4");
        game.makeMove("d7d5");
        game.makeMove("e4d5"); // 백의 e폰이 흑의 d폰을 잡음

        // 백(true)이 잡은 기물 확인
        Map<PieceType, Integer> capturedByWhite = game.getCapturedPieces(true);
        assertEquals(1, capturedByWhite.get(PieceType.PAWN)); // 폰 1개를 잡았어야 함
    }

    @Test
    @DisplayName("기물 점수(Piece Score)가 기물 이득에 따라 변해야 한다")
    void getPieceScore() {
        ChessGame game = new ChessGame();
        assertEquals(0, game.getPieceScore()); // 시작은 0점 (동점)

        game.makeMove("e2e4");
        game.makeMove("d7d5");
        game.makeMove("e4d5"); // 백이 폰 1개 이득 (+1점)

        assertEquals(1, game.getPieceScore());
    }

    @Test
    @DisplayName("특정 칸에 있는 기물을 정확히 가져와야 한다")
    void getPieceOnSquare() {
        ChessGame game = new ChessGame();
        assertEquals(Piece.WHITE_QUEEN, game.getPieceOnSquare(Square.d1));
        assertEquals(Piece.BLACK_ROOK, game.getPieceOnSquare(Square.h8));
        assertEquals(Piece.NONE, game.getPieceOnSquare(Square.e4));
    }

    @Test
    @DisplayName("초기 상태의 전체 합법수(Legal Moves)는 정확히 20개여야 한다")
    void getLegalMoves() {
        ChessGame game = new ChessGame();
        List<MoveInfo> moves = game.getLegalMoves();
        assertEquals(20, moves.size()); // 백 폰 16개 + 나이트 4개 = 20개
    }

    @Test
    @DisplayName("특정 기물(e2 폰)의 합법수만 필터링해서 가져와야 한다")
    void getLegalMovesForPiece() {
        ChessGame game = new ChessGame();
        List<MoveInfo> e2Moves = game.getLegalMovesForPiece(Square.e2);

        assertEquals(2, e2Moves.size()); // e3, e4 두 칸 갈 수 있음
        assertEquals(Square.e3, e2Moves.get(0).getTargetSquare());
        assertEquals(Square.e4, e2Moves.get(1).getTargetSquare());
    }

    @Test
    @DisplayName("초기 보드의 기물 개수는 정확히 32개여야 한다 (Map 테스트)")
    void getBoardStateMap() {
        ChessGame game = new ChessGame();
        Map<Square, Piece> boardMap = game.getBoardStateMap();
        assertEquals(32, boardMap.size()); // 백 16개 + 흑 16개
    }

    @Test
    @DisplayName("체크메이트 상태를 정확히 판별해야 한다")
    void isCheckmate() {
        // 스콜라스 메이트 FEN 주입
        ChessGame game = new ChessGame(SCHOLARS_MATE_FEN);
        assertTrue(game.isCheck());
        assertTrue(game.isCheckmate());
        assertEquals(GameOverReason.CHECKMATE, game.isGameOver());
    }

    @Test
    @DisplayName("스테일메이트(무승부) 상태를 정확히 판별해야 한다")
    void isStalemate() {
        ChessGame game = new ChessGame(STALEMATE_FEN);
        assertFalse(game.isCheck()); // 킹이 공격받고 있지 않음
        assertTrue(game.isStalemate()); // 하지만 둘 곳이 없음
        assertEquals(GameOverReason.STALEMATE, game.isGameOver());
    }

    @Test
    @DisplayName("3회 동형 반복 무승부를 판별해야 한다")
    void isThreefoldRepetition() {
        ChessGame game = new ChessGame();

        // 나이트가 나갔다 들어왔다를 3번 반복
        for (int i = 0; i < 2; i++) {
            game.makeMove("g1f3");
            game.makeMove("g8f6");
            game.makeMove("f3g1");
            game.makeMove("f6g8");
        }
        game.makeMove("g1f3");
        game.makeMove("g8f6"); // 이 시점에서 3번 반복 달성

        assertTrue(game.isThreefoldRepetition());
        assertEquals(GameOverReason.THREEFOLD, game.isGameOver());
    }

    @Test
    @DisplayName("LAN 기보를 SAN(표준 기보)으로 정상 변환해야 한다")
    void toSan() {
        ChessGame game = new ChessGame();
        // 폰 전진 변환
        assertEquals("e4", game.toSan("e2e4"));
        // 나이트 전진 변환
        assertEquals("Nf3", game.toSan("g1f3"));
    }

    @Test
    @DisplayName("디버깅용: 폰 승급(Promotion) 로직이 정상 작동해야 한다")
    void testPromotionDebugging() {
        // [디버그 세팅]
        // 백 폰이 e7에 있어서 한 칸만 전진(e8)하면 퀸으로 승급하는 아주 단순한 엔드게임 FEN
        String PROMOTION_DEBUG_FEN = "8/4P3/8/8/8/8/8/4K2k w - - 0 1";
        ChessGame game = new ChessGame(PROMOTION_DEBUG_FEN);

        System.out.println("===============================");
        System.out.println("[DEBUG] 승급 전 보드 상태");
        System.out.println("===============================");
        System.out.println(game); // 직접 만드신 훌륭한 toString() 활용!

        // [행동] e7 폰을 e8로 이동하며 퀸으로 승급 시도
        try {
            // 방법 1: API 객체지향 메서드 사용
            game.makeMove(Square.e7, Square.e8, PieceType.QUEEN);

            // 방법 2: 만약 LAN 문자열 처리가 의심된다면 아래 코드로 테스트해보세요.
            // game.makeMove("e7e8q");

            System.out.println("===============================");
            System.out.println("[DEBUG] 승급 후 보드 상태 (성공!)");
            System.out.println("===============================");
            System.out.println(game.toString());

        } catch (Exception e) {
            System.err.println("===============================");
            System.err.println("🚨 [DEBUG] 승급 로직에서 예외 발생 🚨");
            System.err.println("에러 메시지: " + e.getMessage());
            System.err.println("===============================");
            e.printStackTrace();
            fail("프로모션 처리 중 백엔드에서 에러가 발생했습니다.");
        }

        // [검증]
        // 1. e7 칸은 비워져야 한다
        assertEquals(Piece.NONE, game.getPieceOnSquare(Square.e7), "승급 후 출발지(e7)는 비워져야 합니다.");

        // 2. e8 칸에는 폰이 아니라 '백 퀸'이 있어야 한다
        assertEquals(Piece.WHITE_QUEEN, game.getPieceOnSquare(Square.e8), "승급 후 목적지(e8)에는 백 퀸이 존재해야 합니다.");
    }
}