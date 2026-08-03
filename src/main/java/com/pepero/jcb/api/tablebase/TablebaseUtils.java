package com.pepero.jcb.api.tablebase;

import com.pepero.jcb.api.ChessGame;
import com.pepero.jcb.api.dto.TablebaseResult;
import com.pepero.jcb.api.exception.TablebaseException;
import com.pepero.jcb.core.GameVariants;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Get Lichess Table base (Not local)
 */
public class TablebaseUtils {
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    /**
     * Get tablebase result ( lichess syzygy api <a href="https://tablebase.lichess.ovh/">...</a>)
     *
     * @param chessGame chess game
     * @return parsed tablebase result
     *
     * @throws TablebaseException - when could not get the tablebase data
     */
    public static TablebaseResult probeTablebase(ChessGame chessGame) {
        if(chessGame.getGameVariants() != GameVariants.STANDARD)
            throw new TablebaseException("the ChessGame is not Standard mode!");

        return probeTablebase(chessGame.getFEN());
    }

    /**
     * Get tablebase result ( lichess syzygy api <a href="https://tablebase.lichess.ovh/">...</a>)
     *
     * @param fen fen
     * @return parsed tablebase result
     */
    public static TablebaseResult probeTablebase(String fen) {
        try {
            String encodedFen = URLEncoder.encode(fen, StandardCharsets.UTF_8);
            String url = "https://tablebase.lichess.ovh/standard?fen=" + encodedFen;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                return parseTablebaseResponse(body);
            } else if (response.statusCode() == 400) {
                throw new TablebaseException("Incorrect fen or the piece count is greater than 7!");
            } else {
                throw new TablebaseException("Could not get the response of tablebase. (error code : "
                        + response.statusCode() + ")");
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            throw new TablebaseException("Unable to connect table base!");
        }
    }

    /**
     * Get table base response
     *
     * @param json json data
     * @return parsed tablebase result
     */
    private static TablebaseResult parseTablebaseResponse(String json) {
        // wdl
        String wdl = extractJsonValue(json, "\"category\":\"");

        // dtz
        String dtzStr = extractJsonValue(json, "\"dtz\":");
        int dtz = dtzStr != null && !dtzStr.isEmpty() ? Integer.parseInt(dtzStr.replaceAll("[^0-9-]", "")) : 0;

        // best move san
        String bestMoveSan = null;
        if (json.contains("\"moves\":[{")) {
            bestMoveSan = extractJsonValue(json, "\"san\":\"");
        }

        // best move lan
        String bestMoveLan = null;
        if (json.contains("\"moves\":[{")) {
            bestMoveLan = extractJsonValue(json, "\"uci\":\"");
        }

        return new TablebaseResult(wdl, dtz, bestMoveSan, bestMoveLan);
    }

    /**
     * Get JSON value
     *
     * @param json json
     * @param key key
     * @return value
     */
    private static String extractJsonValue(String json, String key) {
        int keyIdx = json.indexOf(key);
        if (keyIdx == -1) return null;

        int startIdx = keyIdx + key.length();
        int endIdx = json.indexOf("\"", startIdx);

        if (key.endsWith(":")) {
            endIdx = json.indexOf(",", startIdx);
            if (endIdx == -1) endIdx = json.indexOf("}", startIdx);
        }

        if (endIdx != -1 && endIdx > startIdx) {
            return json.substring(startIdx, endIdx).trim();
        }

        return null;
    }
}
