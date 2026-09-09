package jp.me1han.sam.link;

/** Implemented by server-side SAM devices participating in logical link routing. */
public interface SamLinkedTile {
    String getLinkKey();
    void setLinkKey(String key);
}
