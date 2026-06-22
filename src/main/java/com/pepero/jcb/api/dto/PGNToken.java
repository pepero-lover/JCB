package com.pepero.jcb.api.dto;

import com.pepero.jcb.api.parse.pgn.TokenType;

public record PGNToken(TokenType type, String value) {}