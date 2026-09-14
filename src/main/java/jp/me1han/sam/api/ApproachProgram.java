package jp.me1han.sam.api;

import java.util.ArrayList;

/** Result of one APPROACH samMain invocation, including its optional post-stop arrival. */
public final class ApproachProgram {
    public final AnnounceData approach;
    public final ArrivalPlan arrival;

    public ApproachProgram(AnnounceData approach, ArrivalPlan arrival) {
        if (approach == null) throw new IllegalArgumentException("Approach announcement must not be null");
        this.approach = copy(approach);
        this.arrival = arrival == null ? null : arrival.copy();
    }

    static AnnounceData copy(AnnounceData data) {
        return new AnnounceData(data.startMelo, new ArrayList<String>(data.bodySounds),
            new ArrayList<Integer>(data.bodyIntervalTicks), data.arrMelo, data.repeatCount);
    }
}
