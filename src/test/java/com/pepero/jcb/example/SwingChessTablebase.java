package com.pepero.jcb.example;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.CombinedAnalyzer;
import com.pepero.jcb.api.dto.CombinedMoveDTO;
import com.pepero.jcb.api.dto.MoveInfo;
import com.pepero.jcb.api.enums.Square;
import com.pepero.jcb.api.gaviota.GaviotaTablebase;
import com.pepero.jcb.api.syzygy.SyzygyTablebase;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
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
                "4k3/8/8/8/8/8/4P3/4K3 w - -"
        )));
    }

    private static final int SQUARE_SIZE = 72;
    private static final int PIECE_IMAGE_SIZE = 144;
    private static final String PIECE_ORDER = "KQRBNPkqrbnp";

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
                        int pieceIndex = PIECE_ORDER.indexOf(piece);
                        if (pieceIndex >= 0) {
                            g2.drawImage(PIECE_IMAGES[pieceIndex], c * SQUARE_SIZE, r * SQUARE_SIZE,
                                    SQUARE_SIZE, SQUARE_SIZE, null);
                        }
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




    private static BufferedImage[] loadPieceImages() {
        try (var input = new ByteArrayInputStream(
                Base64.getMimeDecoder().decode(PIECE_ATLAS_PNG))) {
            BufferedImage atlas = ImageIO.read(input);
            if (atlas == null || atlas.getWidth() != PIECE_IMAGE_SIZE * 6
                    || atlas.getHeight() != PIECE_IMAGE_SIZE * 2) {
                throw new IllegalStateException("Invalid embedded chess piece atlas");
            }
            BufferedImage[] images = new BufferedImage[PIECE_ORDER.length()];
            for (int i = 0; i < images.length; i++) {
                images[i] = atlas.getSubimage((i % 6) * PIECE_IMAGE_SIZE,
                        (i / 6) * PIECE_IMAGE_SIZE, PIECE_IMAGE_SIZE, PIECE_IMAGE_SIZE);
            }
            return images;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load embedded chess pieces", e);
        }
    }

    /*
     * Embedded artwork: Green Chess standard chess pieces, Uray M. Janos
     * (2013-2018), derived from Cburnett's Wikimedia Commons chess pieces.
     * Source: https://greenchess.net/info.php?item=downloads
     * Originals: https://commons.wikimedia.org/wiki/Category:SVG_chess_pieces
     * Artwork license: CC BY-SA 3.0, https://creativecommons.org/licenses/by-sa/3.0/
     * Changes: SVGs rasterized to 144px transparent PNG tiles and packed into
     * a 6-by-2 atlas (white, then black; king, queen, rook, bishop, knight, pawn).
     * The embedded artwork and its adaptations remain under CC BY-SA 3.0.
     * Retain these credits and license links when redistributing the artwork.
     */
    private static final String PIECE_ATLAS_PNG = String.join("", """
            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
            i30XLK1hBLZTiTOTpdib9+CDD0b7bkm/8MILgy11qHYeyZFBcM455+RdLn06+4rXWGON4Ouv8eOUIV6iJ2ZSLWEwENhCjVyQbAg/
            CRzuPvrobFttnrQCGh3k/eyzmZ0Z3OgXNl+zlSwjAkdIKL4mqqwT3JseyVM2WVclTBtGjSHwY2WfXvyk2IcpafuJW6UPVEG1/kkm
            mSScYIIJqvHEtVmbaChzYOZdd90Vwol64zB5GyVkistHv8iODk76+41W7MkP1m69cZw+oq/os16ig6VMrGP1Vx+t4dVXX930M6Jb
            C6IzuvswURpYGbWOAKvMfG2mcGbVSQOfpm8dVr7cOklrlpAlZyUM2dtlnaCq+pZYSf5QnOq3X/7yl9UuwxOz51mfyo8pX7fTnXfe
            6Tv/Ez0x719ZbDT4CIytJj8Wp+63448/vuXbjTrceitt0abRICLwf2prIfGa4uFizK3wuLSeeGnxtOIfiVuhO1Q41eFPPfVU0zcR
            Zd36FKeNZmgsFZpNvLx4Q/GW4q3EG4lXEs8p/om4l+kPUs6HKWn/Fs/SovKfJOvXCmaY4177mCbaeS1ZN3sEOfsE9uwXJG+jhEwp
            bJAdHZx0dGyFwBis3Xrj+OmtVF7ysgwsYj2rv/02CLPB16DcpTzLmfat3meE2cvDpE0rdMYZZ6TqpF7SWiFkQjZXXsXRodffS1Jx
            QOkk1Z7ClsFW8iicffbZJ3XdzU9cK0itdHFpyqJHjjkigwAm/4wGFwG+Te8Sp+7Bn/zkJ2Gr38/U4dZbaYs2jQYQgXFUN4OL88Wv
            i30d4aYx43avmKXo1cTU0QiNVOZqnZx78v333zf94GEDqefsFNooQgwotxezzPqimBmeqmw54e+V/oIYO4NNxAxae4m2ljK1MLhH
            11sZhH+ZrP+nP/1peP/99/vae0/5MMMrSpMoY6qeOeecs3pfEXavK06ZooQsyJSq54EHHgjRwUlHx2YJbMHYrTMZp496mfp6EGaD
            r0G7tTFBTv5fReFzzz23+txoNjAQAzBkQTafzEprxpx60IAueUOzSr7MXts//OEP1e7XNoe8VaFqf0w77bQh3hF7hXTcQigT8Kp+
            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
            """, """
            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
            Syyb3wP8v6xWYtsIAYFiHbClS5faceL9kyXiMyUUDoGKY8cXY1poIQS9q4Z3gNsVfAJ4O3CaiH+ok8CPgh8B/wAWCo8AOxDbg88A
            s2O+LfhvYH5MCK1CgB9tM8EzwB+AB4OzuPQKajkSdRcqDwJpxJr/LVPB7ITt7AHTR4gbCR4LnghO4ywMP0K/qWE4FSWuIeN7+wBw
            Z3ALsBt9goh+4GqfsWeHWwgI1K9f3xUHHvWDM1at8dNxwe+vLFHFOxEpBrPi2AUZeUoqzvzYPqSG28PlCz1pNB8CsaPwUo3LEUmh
            0iLAN3Nj8NbgrWp4S7gc6eaZanQ5GpsFYkeKHXla9qE7B/xtDfNDazaY92A1E0f2eXhp22oGoQy6v4k6DgNzpiaNxP/EPcDtwJyl
            WR3MZ+dT8PtgzjQJlQ4Bzsy1AXMP+Cb6/MiMAAACxklEQVRgzqR9D34D/AE4ax/QUCkUDUSuM0PlzFCmbbbZRh144IGOGi1atEiN
            GDFCx3EA/zhw1jrvLaETB/SFgiPQClkmB88WXw7+2WSJODW/C5h/oK1rmFau+CdaLuIyEY7U8c9a8+fwyx8HQEgYrQ15+CdfD8xl
            ONalOOyccRaNvB64DphrhumSmZczyFymQ9eNEZVbtsOlO3bmEB3DloH50Urmugm6i8GLapidLPoXgH+uYfp/AjOvUHEE+L5rCq5b
            PKmkCIHAb8gj77oQwEkWQSAEAo2Qh/9fQt4I8P/1CzD/V7NG7ERwJl4oOAK7I0tFO69Z64A5NcFaCOQsGZcqcqkaXY6yNQBvCuZM
            CT+m/dKvSDjPwpxh4MP9ZQ3Phps3741rIUFAEBAEBAFBQBAQBAQBQSAuBDjBMCyuwqqsnK7Qd3olda6GDpgffNdFIj2rwQ4bmbNp
            K8Cc0eIMA112vrI4igK1hAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQEAUFAEBAEBAFB
            QBAQBAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQE
            AUFAEBAEBAFBQBAQBAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQEAUFAEBAEBAFBQBAQ
            BAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQEAUFA
            EBAEBAFBQBAQBAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQEAUFAEBAEBAFBQBAQBAQBQUAQEAQEgWpF4P8B9VZUwVN9nYwA
            AAAASUVORK5CYII=
            """);

    private static final BufferedImage[] PIECE_IMAGES = loadPieceImages();
}