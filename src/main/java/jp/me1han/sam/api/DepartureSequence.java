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
    private boolean melodyPlaying = true;
    private boolean closingDone;
    private boolean on;

    public DepartureSequence(DepartureProgram program, Output output) {
        this.program = program;
        this.output = output;
        on = program.alternate;
        melodyRemaining = program.melodyTicks;
        output.play(Channel.MELODY, program.melody);
    }

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
                output.stop(Channel.DOOR_CLOSE);
                closingDone = true;
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
        if (program.doorClose.isEmpty()) closingDone = true;
        else {
            closingRemaining = program.doorCloseTicks;
            output.play(Channel.DOOR_CLOSE, program.doorClose);
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
        if (phase == Phase.DOOR_CLOSE && !closingDone) output.stop(Channel.DOOR_CLOSE);
        phase = Phase.FINISHED;
    }
}
