package com.pepero.jcb.api.syzygy;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;

import static com.pepero.jcb.api.syzygy.SyzygyFile.readHeader;

public class SyzygyTest {
    public static void main(String[] args) throws IOException {
        String syzygyFileName = "KPvK";

        Path KRvKPath = Path.of("syzygy/" + syzygyFileName +".rtbw");
        SyzygyFile KRvK_WDL = SyzygyFile.open(KRvKPath);
        SyzygyMaterial KRvK_Material = SyzygyMaterial.parse("KRRvK");

        SyzygyPairsHeadersResult headersResult = KRvK_Material.parsePairsHeaders(readHeader(KRvKPath),
                KRvK_Material.computePairsHeaderStartOffset(),
                KRvK_WDL.isSplit(), SyzygyType.WDL);

        SyzygyPairsHeader header = headersResult.headers()[0][0];

        System.out.println(KRvK_Material.isKkEnc());

        System.out.println(header.minLen());
        System.out.println(header.maxLen());
        System.out.println(Arrays.toString(header.huffmanTable().getOffset()));
        System.out.println(Arrays.toString(header.huffmanTable().getBase()));
        System.out.println(headersResult.nextOffset());
    }
}
