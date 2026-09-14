package jp.me1han.sam.api;

/** Arrival announcement snapshot prepared by an APPROACH script. */
public final class ArrivalPlan {
    public final int delayTicks;
    public final AnnounceData announcement;

    public ArrivalPlan(double delaySeconds, AnnounceData announcement) {
        if (announcement == null) throw new IllegalArgumentException("Arrival announcement must not be null");
        this.delayTicks = DepartureInterval.secondsToTicks(delaySeconds, true, "Arrival delay");
        this.announcement = ApproachProgram.copy(announcement);
    }

    ArrivalPlan(int delayTicks, AnnounceData announcement) {
        this.delayTicks = delayTicks;
        this.announcement = ApproachProgram.copy(announcement);
    }

    public ArrivalPlan copy() { return new ArrivalPlan(delayTicks, announcement); }
}
