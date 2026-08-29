package com.pepero.jcb.api;

/**
 * Analyzed PGN token data for {@link PGNLexer}
 */
record PGNToken(TokenType type, String value) {}