package jp.me1han.sam.api;

/** Tick-exact sequence shared by server state and client playback. */
public final class DepartureSequence {
    public enum Phase { MELODY, INTERVAL, DOOR_CLOSE, FINISHED }
    public enum Channel { MELODY, DOOR_CLOSE }
    public interface Output {
        void play(Channel channel, String sound);
        void stop(Channel channel);
        void finished();
    }

    private final DepartureProgram program;
    private final Output output;
    private Phase phase = Phase.MELODY;
    private int melodyRemaining;
    private int closingRemaining;
    private int closingIndex;
    private boolean melodyPlaying = true;
    private boolean closingPlaying;
    private boolean closingDone;
    private boolean on;

    public DepartureSequence(DepartureProgram program, Output output) {
        this.program = program;
        this.output = output;
        on = program.alternate;
        melodyRemaining = program.melodyTicks;
        output.play(Channel.MELODY, program.melody);
    }

    /** Restore a late participant without replaying the sound already in progress. */
    public DepartureSequence(DepartureProgram program, Output output, long elapsedTicks, long releaseElapsedTicks) {
        this.program = program;
        this.output = output;
        restore(Math.max(0, elapsedTicks), releaseElapsedTicks);
    }

    private void restore(long elapsed, long releaseElapsed) {
        if (program.alternate && releaseElapsed < 0) {
            on = true;
            phase = Phase.MELODY;
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

        if (elapsed < closingStart) {
            phase = Phase.MELODY;
            return;
        }
        long closingElapsed = elapsed - closingStart;
        if (closingElapsed < program.intervalTicks) {
            phase = Phase.INTERVAL;
            closingRemaining = safeInt(program.intervalTicks - closingElapsed);
            return;
        }
        closingElapsed -= program.intervalTicks;
        phase = Phase.DOOR_CLOSE;
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
        closingDone = true;
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

    private static int safeInt(long value) { return (int)Math.min(Integer.MAX_VALUE, Math.max(0, value)); }

    public boolean isOn() { return on; }
    public boolean isFinished() { return phase == Phase.FINISHED; }
    public Phase getPhase() { return phase; }

    public void release() {
        if (!program.alternate || !on || phase != Phase.MELODY) return;
        on = false;
        if (!program.finishChorus) stopMelody();
        beginInterval();
        finishIfDone();
    }

    public void tick() { tick(false); }

    /** A release this tick starts the closing timer without extending the current chorus. */
    public void tick(boolean releasedThisTick) {
        if (isFinished()) return;
        boolean advanceClosing = phase != Phase.MELODY && !releasedThisTick;
        if (melodyPlaying && --melodyRemaining <= 0) {
            stopMelody();
            if (on) {
                melodyPlaying = true;
                melodyRemaining = program.melodyTicks;
                output.play(Channel.MELODY, program.melody);
            } else if (phase == Phase.MELODY) beginInterval();
        }
        if (advanceClosing && !closingDone && --closingRemaining <= 0) {
            if (phase == Phase.INTERVAL) beginDoorClose();
            else if (phase == Phase.DOOR_CLOSE) {
                if (closingPlaying) output.stop(Channel.DOOR_CLOSE);
                closingPlaying = false;
                closingIndex++;
                beginDoorClose();
            }
        }
        finishIfDone();
    }

    private void stopMelody() {
        if (!melodyPlaying) return;
        melodyPlaying = false;
        output.stop(Channel.MELODY);
    }

    private void beginInterval() {
        phase = Phase.INTERVAL;
        closingRemaining = program.intervalTicks;
        if (closingRemaining == 0) beginDoorClose();
    }

    private void beginDoorClose() {
        phase = Phase.DOOR_CLOSE;
        if (closingIndex >= program.doorCloseSounds.size()) closingDone = true;
        else {
            closingRemaining = program.doorCloseDurations.get(closingIndex);
            String sound = program.doorCloseSounds.get(closingIndex);
            closingPlaying = !sound.isEmpty();
            if (closingPlaying) output.play(Channel.DOOR_CLOSE, sound);
        }
    }

    private void finishIfDone() {
        if (!isFinished() && !melodyPlaying && closingDone) {
            phase = Phase.FINISHED;
            output.finished();
        }
    }

    /** Emergency stop never emits the normal completion callback. */
    public void cancel() {
        if (isFinished()) return;
        on = false;
        stopMelody();
        if (phase == Phase.DOOR_CLOSE && closingPlaying) output.stop(Channel.DOOR_CLOSE);
        phase = Phase.FINISHED;
    }
}
