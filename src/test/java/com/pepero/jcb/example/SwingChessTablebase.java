package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.CombinedAnalyzer;
import com.pepero.jcb.api.dto.CombinedMoveDTO;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.api.gaviota.GaviotaTablebase;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SwingChessTablebase extends JFrame {
    // syzygy directory
    private final SyzygyTablebase syzygy =
            new SyzygyTablebase(Path.of("path/to/syzygy"));

    // gaviota directory
    private final GaviotaTablebase gaviota =
            new GaviotaTablebase(Path.of("path/to/gaviota"));

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new SwingChessTablebase(ChessGame.fromFEN(
                // paste endgame fen here
                "8/2B5/8/1k6/8/3K4/4N1R1/qn6 w - -"
        )));
    }

    private static final Map<Character, String> UNICODE_PIECES = Map.ofEntries(
            Map.entry('K', "\u2654"), Map.entry('Q', "\u2655"), Map.entry('R', "\u2656"),
            Map.entry('B', "\u2657"), Map.entry('N', "\u2658"), Map.entry('P', "\u2659"),
            Map.entry('k', "\u265A"), Map.entry('q', "\u265B"), Map.entry('r', "\u265C"),
            Map.entry('b', "\u265D"), Map.entry('n', "\u265E"), Map.entry('p', "\u265F")
    );

    private static final int SQUARE_SIZE = 72;

    private final ChessGame chessGame;
    private final BoardPanel boardPanel;
    private final JTextArea analysisArea = new JTextArea(24, 32);

    private Integer selectedRow = null;
    private Integer selectedCol = null;
    private List<MoveInfo> selectedLegalMoves = new ArrayList<>();
    private List<int[]> legalTargets = new ArrayList<>();

    public SwingChessTablebase(ChessGame chessGame) {
        this.chessGame = chessGame;
        this.boardPanel = new BoardPanel();

        analysisArea.setEditable(false);
        analysisArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));

        add(boardPanel, BorderLayout.CENTER);
        add(new JScrollPane(analysisArea), BorderLayout.EAST);

        setTitle("JCB Swing Board");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        pack();
        setResizable(false);
        setLocationRelativeTo(null);
        setVisible(true);

        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                super.keyPressed(e);

                if (e.getKeyCode() == KeyEvent.VK_LEFT) {
                    chessGame.tryUnmakeMove();
                    repaint();
                } else if(e.getKeyCode() == KeyEvent.VK_RIGHT) {
                    chessGame.tryRemakeMove();
                    repaint();
                }
            }
        });

        refreshAnalysis();
    }

    private long analysisVersion = 0;

    private void refreshAnalysis() {
        long version = ++analysisVersion;
        ChessGame snapshot = ChessGame.lightWeightCopy(chessGame);

        analysisArea.setText("Analyzing...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                synchronized (syzygy) {
                    List<CombinedMoveDTO> moves =
                            CombinedAnalyzer.findRankedMoves(
                                    snapshot, syzygy, gaviota);

                    StringBuilder text = new StringBuilder();
                    text.append("Move       WDL    DTZ    DTM\n");

                    for (CombinedMoveDTO move : moves) {
                        text.append(String.format(
                                "%-7s %4d %6d %6s%n",
                                snapshot.toSan(move.move().toLanString()),
                                move.wdl(),
                                move.dtz(),
                                move.dtm() == null ? "—" : move.dtm()
                        ));
                    }

                    return moves.isEmpty()
                            ? "There is no legal move to play."
                            : text.toString();
                }
            }

            @Override
            protected void done() {
                if (version != analysisVersion) return;

                try {
                    analysisArea.setText(get());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    analysisArea.setText("분석이 중단되었습니다.");
                } catch (java.util.concurrent.ExecutionException e) {
                    analysisArea.setText(
                            "분석 실패: " + e.getCause().getMessage());
                }
            }
        }.execute();
    }

    private char[][] parseBoard() {
        String fen = chessGame.getFEN();
        String boardPart = fen.split(" ")[0].split("\\[")[0];
        String[] ranks = boardPart.split("/");

        char[][] board = new char[8][8];
        for (int r = 0; r < 8; r++) {
            int col = 0;
            for (char c : ranks[r].toCharArray()) {
                if (Character.isDigit(c)) {
                    int empty = Character.getNumericValue(c);
                    for (int i = 0; i < empty; i++) board[r][col++] = '.';
                } else if (c != '~') {
                    board[r][col++] = c;
                }
            }
        }
        return board;
    }

    private class BoardPanel extends JPanel {
        BoardPanel() {
            setPreferredSize(new Dimension(SQUARE_SIZE * 8, SQUARE_SIZE * 8));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    handleClick(e.getX() / SQUARE_SIZE, e.getY() / SQUARE_SIZE);
                }
            });
            setBackground(Color.DARK_GRAY);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            char[][] board = parseBoard();

            for (int r = 0; r < 8; r++) {
                for (int c = 0; c < 8; c++) {
                    boolean light = (r + c) % 2 == 0;
                    boolean isSelected = selectedRow != null && selectedRow == r && selectedCol == c;

                    g2.setColor(isSelected ? new Color(246, 246, 105)
                            : light ? new Color(240, 217, 181) : new Color(181, 136, 99));
                    g2.fillRect(c * SQUARE_SIZE, r * SQUARE_SIZE, SQUARE_SIZE, SQUARE_SIZE);

                    char piece = board[r][c];
                    if (piece != '.') {
                        String symbol = UNICODE_PIECES.get(piece);
                        g2.setColor(Character.isUpperCase(piece) ? Color.WHITE : Color.BLACK);
                        g2.setFont(new Font("Serif", Font.PLAIN, SQUARE_SIZE - 10));
                        g2.drawString(symbol, c * SQUARE_SIZE + 8, r * SQUARE_SIZE + SQUARE_SIZE - 10);
                    }
                }
            }

            g2.setColor(new Color(0, 0, 0, 90));
            for (int[] target : legalTargets) {
                int cx = target[1] * SQUARE_SIZE + SQUARE_SIZE / 2;
                int cy = target[0] * SQUARE_SIZE + SQUARE_SIZE / 2;
                int rad = SQUARE_SIZE / 6;
                g2.fillOval(cx - rad, cy - rad, rad * 2, rad * 2);
            }
        }
    }

    private void handleClick(int col, int row) {
        if (selectedRow == null) {
            Square source = rowColToSquare(row, col);
            List<MoveInfo> legalMoves = chessGame.getLegalMovesForSource(source);

            if (legalMoves.isEmpty()) return;

            selectedLegalMoves = legalMoves;
            legalTargets = legalMoves.stream()
                    .map(m -> algebraicToRowCol(m.toLanString().substring(2, 4)))
                    .distinct()
                    .toList();

            selectedRow = row;
            selectedCol = col;
            boardPanel.repaint();
            return;
        }

        List<MoveInfo> candidates = selectedLegalMoves.stream()
                .filter(m -> {
                    int[] rc = algebraicToRowCol(m.toLanString().substring(2, 4));
                    return rc[0] == row && rc[1] == col;
                })
                .toList();

        selectedRow = null;
        selectedCol = null;
        legalTargets = new ArrayList<>();
        selectedLegalMoves = new ArrayList<>();

        if (candidates.isEmpty()) {
            boardPanel.repaint();
            return;
        }

        MoveInfo chosen = candidates.getFirst().isPromotion()
                ? pickPromotionMove(candidates)
                : candidates.getFirst();

        if(chosen == null) {
            boardPanel.repaint();
            return;
        }

        chessGame.makeMove(chosen);

        refreshAnalysis();

        boardPanel.repaint();
    }

    private MoveInfo pickPromotionMove(List<MoveInfo> candidates) {
        String[] options = candidates.stream()
                .map(m -> m.toLanString().substring(4))
                .toArray(String[]::new);

        int choice = JOptionPane.showOptionDialog(this, "Promote to:", "Promotion",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);

        if(!(choice >= 0 && choice <= 4)) return null;

        return candidates.get(choice);
    }

    private Square rowColToSquare(int row, int col) {
        int rank = 8 - row;
        int index = (rank - 1) * 8 + col;
        return Square.fromIndex(index);
    }

    private int[] algebraicToRowCol(String algebraic) {
        int c = algebraic.charAt(0) - 'a';
        int rank = algebraic.charAt(1) - '0';
        int r = 8 - rank;
        return new int[]{r, c};
    }
}