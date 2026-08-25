package com.pepero.jcb.api.dto;

/**
 * Move annotation data DTO
 *
 * @param comment comment data on this move
 * @param nag nag data like "$1" "$4"
 * @param clk clock data string
 * @param timeStamp used time stamp data
 * @param eval evaluation data
 * @param csl square color data
 * @param cal arrow drawing data
 */
public record MoveAnnotationDTO(
            String comment, String nag, String clk, String timeStamp,
            String eval, String csl, String cal
) {}