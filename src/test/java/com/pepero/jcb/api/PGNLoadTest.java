package com.pepero.jcb.api;

public class PGNLoadTest {
    public static void main(String[] args) {
        ChessGame chessGame = ChessGame.fromPGN("""
                [Result "1/2-1/2"]
                [Variant "Racing Kings"]
                
                1. Nxc2 Nxf2 2. Rxf2 Rxc2 3. Kg3 Rb6 4. Rf7 Rf6 5. Rb7 Nc4 6. Qf3 Be3 7. Rh1 Ka3 8. Nf4 Nd2 9. Qe4 Rc1 10. Bd3 Rxh1 11. Qxe3 Rb1 12. Rxb1 Qxb1 13. Qxd2 Qb4 14. Qe3 Rf5 15. Kg4 Ra5 16. Qe6 Qd6 17. Qe4 Kb3 18. Bf1 Qf6 19. Bg2 Rc5 20. Nh5 Qe5 21. Ng3 Rc4 22. Nf1 Qc5 23. Qd4 Rb4 24. Ne3 Qe5 25. Qe4 Ka4 26. Nc2 Rc4 27. Ne3 Rb4 28. Nc2 Rc4 29. Ne3 Rb4 1/2-1/2
                
                """);
        System.out.println(chessGame.getGameVariants());
        System.out.println(chessGame.getPGN());
    }
}
