package jp.me1han.sam.compat;

/** A train view that does not leak optional-mod classes into SAM core. */
public interface TrainSnapshot {
    int getEntityId();

    boolean isControlCar();

    long getFormationId();

    String extractData(String key, int type);
}
