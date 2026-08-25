package com.pepero.jcb.api.analyze;

/**
 * If this tactic should be solved right now, the severity is {@link TacticSeverity#IMMEDIATE},
 * otherwise, the severity is {@link TacticSeverity#LATENT}
 */
public enum TacticSeverity { IMMEDIATE, LATENT }