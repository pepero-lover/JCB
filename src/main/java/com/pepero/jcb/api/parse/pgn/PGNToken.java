package com.pepero.jcb.api.parse.pgn;

/**
 * Analyzed PGN token data for {@link PGNLexer}
 */
public record PGNToken(TokenType type, String value) {}