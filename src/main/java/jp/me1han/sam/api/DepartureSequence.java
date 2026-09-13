package jp.me1han.sam.api;

/** Tick-exact sequence shared by server state and client playback. */
public final class DepartureSequence {
    public enum Phase { MELODY, INTERVAL, DOOR_CLOSE, FINISHED }
    public enum TailPhase {
        NONE(0), INTERVAL(1), DOOR_CLOSE(2), DONE(3);
        public final int id;
        TailPhase(int id) { this.id = id; }
        public static TailPhase fromId(int id) {
            for (TailPhase phase : values()) if (phase.id == id) return phase;
            throw new IllegalArgumentException("Invalid departure tail phase");
        }
    }
    public enum Channel { MELODY, DOOR_CLOSE }
    public interface Output {
        void play(Channel channel, String sound);
        void stop(Channel channel);
        void finished();
    }

    /** Bounded late-join state captured from the authoritative server sequence. */
    public static final class Snapshot {
        public final boolean on;
        public final boolean melodyPlaying;
        public final int melodyRemaining;
        public final TailPhase tailPhase;
        public final int closingIndex;
        public final int closingRemaining;

        public Snapshot(boolean on, boolean melodyPlaying, int melodyRemaining,
                TailPhase tailPhase, int closingIndex, int closingRemaining) {
            this.on = on;
            this.melodyPlaying = melodyPlaying;
            this.melodyRemaining = melodyRemaining;
            this.tailPhase = tailPhase;
            this.closingIndex = closingIndex;
            this.closingRemaining = closingRemaining;
        }
    }

    private final DepartureProgram program;
    private final Output output;
    private TailPhase tailPhase = TailPhase.NONE;
    private int melodyRemaining;
    private int closingRemaining;
    private int closingIndex;
    private boolean melodyPlaying;
    private boolean closingPlaying;
    private boolean on;
    private boolean finished;
    private boolean muted;

    public DepartureSequence(DepartureProgram program, Output output) {
        this.program = program;
        this.output = output;
        on = program.alternate;
        startMelody();
    }

    /** Restore the original one-release wall-clock representation. */
    public DepartureSequence(DepartureProgram program, Output output, long elapsedTicks, long releaseElapsedTicks) {
        this.program = program;
        this.output = output;
        restoreLegacy(Math.max(0, elapsedTicks), releaseElapsedTicks);
    }

    /** Restore a server snapshot, advancing only the routing delay without replaying past sounds. */
    public DepartureSequence(DepartureProgram program, Output output, Snapshot snapshot, long elapsedAfterSnapshot) {
        this.program = program;
        this.output = output;
        muted = true;
        restoreSnapshot(snapshot);
        for (long i = 0; i < elapsedAfterSnapshot && !finished; i++) tick();
        muted = false;
    }

    private void restoreSnapshot(Snapshot snapshot) {
        if (snapshot == null || snapshot.tailPhase == null
            || snapshot.melodyRemaining < 0 || snapshot.melodyRemaining > program.melodyTicks
            || snapshot.closingIndex < 0 || snapshot.closingIndex > program.doorCloseSounds.size()
            || snapshot.closingRemaining < 0 || snapshot.closingRemaining > 72000
            || (snapshot.on && !program.alternate)
            || (snapshot.melodyPlaying && snapshot.melodyRemaining == 0)
            || (snapshot.tailPhase == TailPhase.NONE
                && (snapshot.closingIndex != 0 || snapshot.closingRemaining != 0))
            || (snapshot.tailPhase == TailPhase.INTERVAL
                && (snapshot.closingIndex != 0 || snapshot.closingRemaining == 0
                    || snapshot.closingRemaining > program.intervalTicks))
            || (snapshot.tailPhase == TailPhase.DOOR_CLOSE
                && (snapshot.closingIndex >= program.doorCloseDurations.size()
                    || snapshot.closingRemaining == 0
                    || snapshot.closingRemaining > program.doorCloseDurations.get(snapshot.closingIndex)))
            || (snapshot.tailPhase == TailPhase.DONE
                && (snapshot.closingIndex != program.doorCloseSounds.size()
                    || snapshot.closingRemaining != 0))) {
            throw new IllegalArgumentException("Invalid departure sequence snapshot");
        }
        on = snapshot.on;
        melodyPlaying = snapshot.melodyPlaying;
        melodyRemaining = snapshot.melodyRemaining;
        tailPhase = snapshot.tailPhase;
        closingIndex = snapshot.closingIndex;
        closingRemaining = snapshot.closingRemaining;
        closingPlaying = tailPhase == TailPhase.DOOR_CLOSE
            && closingIndex < program.doorCloseSounds.size()
            && !program.doorCloseSounds.get(closingIndex).isEmpty();
        finishIfDone();
    }

    private void restoreLegacy(long elapsed, long releaseElapsed) {
        if (program.alternate && releaseElapsed < 0) {
            on = true;
            melodyPlaying = true;
            melodyRemaining = remainingInCycle(elapsed, program.melodyTicks);
            return;
        }
        on = false;
        long closingStart = program.alternate ? Math.max(0, releaseElapsed) : program.melodyTicks;
        if (program.alternate) {
            melodyPlaying = program.finishChorus && elapsed < nextBoundary(closingStart, program.melodyTicks);
            if (melodyPlaying) melodyRemaining = safeInt(nextBoundary(closingStart, program.melodyTicks) - elapsed);
        } else {
            melodyPlaying = elapsed < program.melodyTicks;
            if (melodyPlaying) melodyRemaining = safeInt(program.melodyTicks - elapsed);
        }
        if (elapsed < closingStart) return;
        long closingElapsed = elapsed - closingStart;
        if (closingElapsed < program.intervalTicks) {
            tailPhase = TailPhase.INTERVAL;
            closingRemaining = safeInt(program.intervalTicks - closingElapsed);
            return;
        }
        closingElapsed -= program.intervalTicks;
        tailPhase = TailPhase.DOOR_CLOSE;
        while (closingIndex < program.doorCloseDurations.size()) {
            int duration = program.doorCloseDurations.get(closingIndex);
            if (closingElapsed < duration) {
                closingRemaining = safeInt(duration - closingElapsed);
                closingPlaying = !program.doorCloseSounds.get(closingIndex).isEmpty();
                return;
            }
            closingElapsed -= duration;
            closingIndex++;
        }
        tailPhase = TailPhase.DONE;
        finishIfDone();
    }

    private static int remainingInCycle(long elapsed, int cycle) {
        long offset = elapsed % cycle;
        return offset == 0 ? cycle : safeInt(cycle - offset);
    }

    private static long nextBoundary(long elapsed, int cycle) {
        long remainder = elapsed % cycle;
        return elapsed + (remainder == 0 ? cycle : cycle - remainder);
    }

    private static int safeInt(long value) {
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0, value));
    }

    public Snapshot snapshot() {
        return new Snapshot(on, melodyPlaying, melodyRemaining, tailPhase, closingIndex, closingRemaining);
    }

    public boolean isOn() { return on; }
    public boolean isMelodyPlaying() { return melodyPlaying; }
    public boolean isFinished() { return finished; }
    public TailPhase getTailPhase() { return tailPhase; }
    public int getMelodyRemaining() { return melodyRemaining; }
    public int getClosingRemaining() { return closingRemaining; }
    public int getClosingIndex() { return closingIndex; }
    public Phase getPhase() {
        if (finished) return Phase.FINISHED;
        if (tailPhase == TailPhase.INTERVAL) return Phase.INTERVAL;
        if (tailPhase == TailPhase.DOOR_CLOSE) return Phase.DOOR_CLOSE;
        return Phase.MELODY;
    }

    public boolean release() {
        if (!program.alternate || !on || finished) return false;
        on = false;
        if (!program.finishChorus) stopMelody();
        if (tailPhase == TailPhase.NONE || tailPhase == TailPhase.DONE) beginInterval();
        finishIfDone();
        return true;
    }

    /** Returns true only when reengaging had to restart the melody from its beginning. */
    public boolean reengage() {
        if (!program.alternate || on || finished) return false;
        on = true;
        if (melodyPlaying) return false;
        startMelody();
        return true;
    }

    public void tick() { tick(false); }

    /** A release this tick starts the closing timer without consuming its first tick. */
    public void tick(boolean releasedThisTick) {
        if (finished) return;
        boolean advanceClosing = !releasedThisTick
            && (tailPhase == TailPhase.INTERVAL || tailPhase == TailPhase.DOOR_CLOSE);
        if (melodyPlaying && --melodyRemaining <= 0) {
            stopMelody();
            if (on) startMelody();
            else if (!program.alternate && tailPhase == TailPhase.NONE) beginInterval();
        }
        if (advanceClosing && --closingRemaining <= 0) {
            if (tailPhase == TailPhase.INTERVAL) beginDoorClose();
            else {
                if (closingPlaying) stop(Channel.DOOR_CLOSE);
                closingPlaying = false;
                closingIndex++;
                beginDoorClose();
            }
        }
        finishIfDone();
    }

    private void startMelody() {
        melodyPlaying = true;
        melodyRemaining = program.melodyTicks;
        play(Channel.MELODY, program.melody);
    }

    private void stopMelody() {
        if (!melodyPlaying) return;
        melodyPlaying = false;
        stop(Channel.MELODY);
    }

    private void beginInterval() {
        tailPhase = TailPhase.INTERVAL;
        closingIndex = 0;
        closingRemaining = program.intervalTicks;
        if (closingRemaining == 0) beginDoorClose();
    }

    private void beginDoorClose() {
        tailPhase = TailPhase.DOOR_CLOSE;
        if (closingIndex >= program.doorCloseSounds.size()) {
            tailPhase = TailPhase.DONE;
            closingRemaining = 0;
        } else {
            closingRemaining = program.doorCloseDurations.get(closingIndex);
            String sound = program.doorCloseSounds.get(closingIndex);
            closingPlaying = !sound.isEmpty();
            if (closingPlaying) play(Channel.DOOR_CLOSE, sound);
        }
    }

    private void finishIfDone() {
        if (!finished && !on && !melodyPlaying && tailPhase == TailPhase.DONE) {
            finished = true;
            if (!muted) output.finished();
        }
    }

    private void play(Channel channel, String sound) { if (!muted) output.play(channel, sound); }
    private void stop(Channel channel) { if (!muted) output.stop(channel); }

    /** Emergency stop never emits the normal completion callback. */
    public void cancel() {
        if (finished) return;
        on = false;
        stopMelody();
        if (tailPhase == TailPhase.DOOR_CLOSE && closingPlaying) stop(Channel.DOOR_CLOSE);
        closingPlaying = false;
        finished = true;
    }
}
