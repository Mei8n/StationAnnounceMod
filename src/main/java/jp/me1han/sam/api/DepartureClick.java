package jp.me1han.sam.api;

/** JS records an action; the device applies it only after the callback succeeds. */
public final class DepartureClick {
    public enum Action { NONE, PRESS, ON, OFF }
    private final boolean on;
    private final boolean alternate;
    private Action action = Action.NONE;

    public DepartureClick(boolean on, boolean alternate) { this.on = on; this.alternate = alternate; }
    public boolean isOn() { return on; }
    public boolean isAlternate() { return alternate; }
    public void press() { action = Action.PRESS; }
    public void on() { action = Action.ON; }
    public void off() { action = Action.OFF; }
    public void toggle() { action = !alternate ? Action.PRESS : on ? Action.OFF : Action.ON; }
    public Action getAction() { return action; }
}
