package jp.me1han.sam;

import java.util.List;

public class AnnounceData {
    public final String startMelo;
    public final List<String> bodySounds;
    public final String arrMelo;

    public AnnounceData(String start, List<String> body, String loop) {
        this.startMelo = start;
        this.bodySounds = body;
        this.arrMelo = loop;
    }
}
