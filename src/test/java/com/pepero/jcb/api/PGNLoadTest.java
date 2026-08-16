package com.pepero.jcb.api;

public class PGNLoadTest {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.fromPGN("""
                [Result "1-0"]
                [Variant "Three-check"]
                
                1. Nc3 d5 2. e4 c6 3. Nf3 dxe4 4. Nxe4 Nf6 5. Nxf6+ exf6 6. Bc4 Qe7+ 7. Kf1 Be6 8. d3 Bxc4 9. dxc4 Qe6 10. b3 Be7 11. Be3 Nd7 12. Nd4 Qe4 13. Qf3 Qxf3 14. gxf3 g6 15. Kg2 h5 16. Rad1 Rd8 17. Bh6 Rb8 18. Nb5 cxb5 19. Rxd7 b4 20. Rxe7+ Kd8 21. Rd1# 1-0
                
                """);
        System.out.println(chessGame.getGameVariants());
        System.out.println(chessGame.getPGN());
    }
}
