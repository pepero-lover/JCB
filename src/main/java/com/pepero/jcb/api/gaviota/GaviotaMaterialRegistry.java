package com.pepero.jcb.api.gaviota;

import java.util.HashMap;
import java.util.Map;

import static com.pepero.jcb.api.gaviota.GaviotaConstants.*;
import static com.pepero.jcb.api.gaviota.GaviotaPieceIndexers.*;

/**
 * Ported 1:1 from gaviota.py's EGKEY dict. Maps a material string (white
 * pieces then black pieces, lowercase, king-first descending order, e.g.
 * "kqk" for KQvK, "kabpk" for K+bishop+knight+pawn vs K) to the
 * GaviotaEndgameKey describing how to compute its probe index.
 */
final class GaviotaMaterialRegistry {

    private GaviotaMaterialRegistry() {}

    static final Map<String, GaviotaEndgameKey> EGKEY = new HashMap<>();

    private static void put(String key, long maxIndex, int sliceN, GaviotaPcToIndex fn) {
        EGKEY.put(key, new GaviotaEndgameKey(maxIndex, sliceN, fn));
    }

    static {
        put("kqk", MAX_KXK, 1, GaviotaPieceIndexers::kxkPctoindex);
        put("krk", MAX_KXK, 1, GaviotaPieceIndexers::kxkPctoindex);
        put("kbk", MAX_KXK, 1, GaviotaPieceIndexers::kxkPctoindex);
        put("knk", MAX_KXK, 1, GaviotaPieceIndexers::kxkPctoindex);
        put("kpk", MAX_kpk, 24, GaviotaPieceIndexers::kpkPctoindex);

        put("kqkq", MAX_kakb, 1, GaviotaPieceIndexers::kakbPctoindex);
        put("kqkr", MAX_kakb, 1, GaviotaPieceIndexers::kakbPctoindex);
        put("kqkb", MAX_kakb, 1, GaviotaPieceIndexers::kakbPctoindex);
        put("kqkn", MAX_kakb, 1, GaviotaPieceIndexers::kakbPctoindex);

        put("krkr", MAX_kakb, 1, GaviotaPieceIndexers::kakbPctoindex);
        put("krkb", MAX_kakb, 1, GaviotaPieceIndexers::kakbPctoindex);
        put("krkn", MAX_kakb, 1, GaviotaPieceIndexers::kakbPctoindex);

        put("kbkb", MAX_kakb, 1, GaviotaPieceIndexers::kakbPctoindex);
        put("kbkn", MAX_kakb, 1, GaviotaPieceIndexers::kakbPctoindex);

        put("knkn", MAX_kakb, 1, GaviotaPieceIndexers::kakbPctoindex);

        put("kqqk", MAX_kaak, 1, GaviotaPieceIndexers::kaakPctoindex);
        put("kqrk", MAX_kabk, 1, GaviotaPieceIndexers::kabkPctoindex);
        put("kqbk", MAX_kabk, 1, GaviotaPieceIndexers::kabkPctoindex);
        put("kqnk", MAX_kabk, 1, GaviotaPieceIndexers::kabkPctoindex);

        put("krrk", MAX_kaak, 1, GaviotaPieceIndexers::kaakPctoindex);
        put("krbk", MAX_kabk, 1, GaviotaPieceIndexers::kabkPctoindex);
        put("krnk", MAX_kabk, 1, GaviotaPieceIndexers::kabkPctoindex);

        put("kbbk", MAX_kaak, 1, GaviotaPieceIndexers::kaakPctoindex);
        put("kbnk", MAX_kabk, 1, GaviotaPieceIndexers::kabkPctoindex);

        put("knnk", MAX_kaak, 1, GaviotaPieceIndexers::kaakPctoindex);

        put("kqkp", MAX_kakp, 24, GaviotaPieceIndexers::kakpPctoindex);
        put("krkp", MAX_kakp, 24, GaviotaPieceIndexers::kakpPctoindex);
        put("kbkp", MAX_kakp, 24, GaviotaPieceIndexers::kakpPctoindex);
        put("knkp", MAX_kakp, 24, GaviotaPieceIndexers::kakpPctoindex);

        put("kqpk", MAX_kapk, 24, GaviotaPieceIndexers::kapkPctoindex);
        put("krpk", MAX_kapk, 24, GaviotaPieceIndexers::kapkPctoindex);
        put("kbpk", MAX_kapk, 24, GaviotaPieceIndexers::kapkPctoindex);
        put("knpk", MAX_kapk, 24, GaviotaPieceIndexers::kapkPctoindex);

        put("kppk", MAX_kppk, MAX_PPINDEX, GaviotaPieceIndexers::kppkPctoindex);

        put("kpkp", MAX_kpkp, MAX_PpINDEX, GaviotaPieceIndexers::kpkpPctoindex);

        put("kppkp", MAX_kppkp, 24 * MAX_PP48_INDEX, GaviotaPieceIndexers::kppkpPctoindex);

        put("kbbkr", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("kbbkb", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("knnkb", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("knnkn", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);

        put("kqqqk", MAX_kaaak, 1, GaviotaPieceIndexers::kaaakPctoindex);
        put("kqqrk", MAX_kaabk, 1, GaviotaPieceIndexers::kaabkPctoindex);
        put("kqqbk", MAX_kaabk, 1, GaviotaPieceIndexers::kaabkPctoindex);
        put("kqqnk", MAX_kaabk, 1, GaviotaPieceIndexers::kaabkPctoindex);
        put("kqrrk", MAX_kabbk, 1, GaviotaPieceIndexers::kabbkPctoindex);
        put("kqrbk", MAX_kabck, 1, GaviotaPieceIndexers::kabckPctoindex);
        put("kqrnk", MAX_kabck, 1, GaviotaPieceIndexers::kabckPctoindex);
        put("kqbbk", MAX_kabbk, 1, GaviotaPieceIndexers::kabbkPctoindex);
        put("kqbnk", MAX_kabck, 1, GaviotaPieceIndexers::kabckPctoindex);
        put("kqnnk", MAX_kabbk, 1, GaviotaPieceIndexers::kabbkPctoindex);
        put("krrrk", MAX_kaaak, 1, GaviotaPieceIndexers::kaaakPctoindex);
        put("krrbk", MAX_kaabk, 1, GaviotaPieceIndexers::kaabkPctoindex);
        put("krrnk", MAX_kaabk, 1, GaviotaPieceIndexers::kaabkPctoindex);
        put("krbbk", MAX_kabbk, 1, GaviotaPieceIndexers::kabbkPctoindex);
        put("krbnk", MAX_kabck, 1, GaviotaPieceIndexers::kabckPctoindex);
        put("krnnk", MAX_kabbk, 1, GaviotaPieceIndexers::kabbkPctoindex);
        put("kbbbk", MAX_kaaak, 1, GaviotaPieceIndexers::kaaakPctoindex);
        put("kbbnk", MAX_kaabk, 1, GaviotaPieceIndexers::kaabkPctoindex);
        put("kbnnk", MAX_kabbk, 1, GaviotaPieceIndexers::kabbkPctoindex);
        put("knnnk", MAX_kaaak, 1, GaviotaPieceIndexers::kaaakPctoindex);

        put("kqqkq", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("kqqkr", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("kqqkb", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("kqqkn", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("kqrkq", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kqrkr", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kqrkb", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kqrkn", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kqbkq", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kqbkr", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kqbkb", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kqbkn", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kqnkq", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kqnkr", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kqnkb", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kqnkn", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("krrkq", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("krrkr", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("krrkb", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("krrkn", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("krbkq", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("krbkr", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("krbkb", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("krbkn", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("krnkq", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("krnkr", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("krnkb", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("krnkn", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kbbkq", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("kbbkn", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("kbnkq", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kbnkr", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kbnkb", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("kbnkn", MAX_kabkc, 1, GaviotaPieceIndexers::kabkcPctoindex);
        put("knnkq", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);
        put("knnkr", MAX_kaakb, 1, GaviotaPieceIndexers::kaakbPctoindex);

        put("kqqpk", MAX_kaapk, 24, GaviotaPieceIndexers::kaapkPctoindex);
        put("kqrpk", MAX_kabpk, 24, GaviotaPieceIndexers::kabpkPctoindex);
        put("kqbpk", MAX_kabpk, 24, GaviotaPieceIndexers::kabpkPctoindex);
        put("kqnpk", MAX_kabpk, 24, GaviotaPieceIndexers::kabpkPctoindex);
        put("krrpk", MAX_kaapk, 24, GaviotaPieceIndexers::kaapkPctoindex);
        put("krbpk", MAX_kabpk, 24, GaviotaPieceIndexers::kabpkPctoindex);
        put("krnpk", MAX_kabpk, 24, GaviotaPieceIndexers::kabpkPctoindex);
        put("kbbpk", MAX_kaapk, 24, GaviotaPieceIndexers::kaapkPctoindex);
        put("kbnpk", MAX_kabpk, 24, GaviotaPieceIndexers::kabpkPctoindex);
        put("knnpk", MAX_kaapk, 24, GaviotaPieceIndexers::kaapkPctoindex);

        put("kqppk", MAX_kappk, MAX_PPINDEX, GaviotaPieceIndexers::kappkPctoindex);
        put("krppk", MAX_kappk, MAX_PPINDEX, GaviotaPieceIndexers::kappkPctoindex);
        put("kbppk", MAX_kappk, MAX_PPINDEX, GaviotaPieceIndexers::kappkPctoindex);
        put("knppk", MAX_kappk, MAX_PPINDEX, GaviotaPieceIndexers::kappkPctoindex);

        put("kqpkq", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("kqpkr", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("kqpkb", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("kqpkn", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("krpkq", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("krpkr", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("krpkb", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("krpkn", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("kbpkq", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("kbpkr", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("kbpkb", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("kbpkn", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("knpkq", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("knpkr", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("knpkb", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("knpkn", MAX_kapkb, 24, GaviotaPieceIndexers::kapkbPctoindex);
        put("kppkq", MAX_kppka, MAX_PPINDEX, GaviotaPieceIndexers::kppkaPctoindex);
        put("kppkr", MAX_kppka, MAX_PPINDEX, GaviotaPieceIndexers::kppkaPctoindex);
        put("kppkb", MAX_kppka, MAX_PPINDEX, GaviotaPieceIndexers::kppkaPctoindex);
        put("kppkn", MAX_kppka, MAX_PPINDEX, GaviotaPieceIndexers::kppkaPctoindex);

        put("kqqkp", MAX_kaakp, 24, GaviotaPieceIndexers::kaakpPctoindex);
        put("kqrkp", MAX_kabkp, 24, GaviotaPieceIndexers::kabkpPctoindex);
        put("kqbkp", MAX_kabkp, 24, GaviotaPieceIndexers::kabkpPctoindex);
        put("kqnkp", MAX_kabkp, 24, GaviotaPieceIndexers::kabkpPctoindex);
        put("krrkp", MAX_kaakp, 24, GaviotaPieceIndexers::kaakpPctoindex);
        put("krbkp", MAX_kabkp, 24, GaviotaPieceIndexers::kabkpPctoindex);
        put("krnkp", MAX_kabkp, 24, GaviotaPieceIndexers::kabkpPctoindex);
        put("kbbkp", MAX_kaakp, 24, GaviotaPieceIndexers::kaakpPctoindex);
        put("kbnkp", MAX_kabkp, 24, GaviotaPieceIndexers::kabkpPctoindex);
        put("knnkp", MAX_kaakp, 24, GaviotaPieceIndexers::kaakpPctoindex);

        put("kqpkp", MAX_kapkp, MAX_PpINDEX, GaviotaPieceIndexers::kapkpPctoindex);
        put("krpkp", MAX_kapkp, MAX_PpINDEX, GaviotaPieceIndexers::kapkpPctoindex);
        put("kbpkp", MAX_kapkp, MAX_PpINDEX, GaviotaPieceIndexers::kapkpPctoindex);
        put("knpkp", MAX_kapkp, MAX_PpINDEX, GaviotaPieceIndexers::kapkpPctoindex);

        put("kpppk", MAX_kpppk, MAX_PPP48_INDEX, GaviotaPieceIndexers::kpppkPctoindex);
    }
}