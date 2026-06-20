package com.pepero.jcb.api.enums;

public enum GameMode {
    VARIATION, // variation mode like e4 e5 Nf3 (Nc3 Nf6) Nc6 (suitable for chess analysis program development)
    LINEAR     // linear mode like just e4 e5 Nf3 Nc6 (suitable for chess engine development) and there is no san, comment, nag)
}
