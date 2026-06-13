package com.pepero.jcb.api;

import com.pepero.jcb.api.tablebase.TablebaseUtils;
import com.pepero.jcb.core.chess960.Chess960Utils;

public class MainTest {
    public static void main(String[] args) {
        System.out.println(Chess960Utils.generateRandom960Fen());
    }
}
