package jp.me1han.sam.network;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import io.netty.buffer.ByteBuf;
import jp.me1han.sam.api.AnnounceData;
import java.util.ArrayList;
import java.util.List;

public class PacketAnnounce implements IMessage {
    public static class SpeakerData {
        public int x;
        public int y;
        public int z;
        public int range;
        public float volume;

        public SpeakerData() {}

        public SpeakerData(int x, int y, int z, int range, float volume) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.range = range;
            this.volume = volume;
        }
    }

    public String startMelo;
    public List<String> bodySounds;
    public String arrMelo;
    public String linkKey;
    public boolean stopCommand;
    public boolean playLocalSound;
    public int x, y, z;
    public List<SpeakerData> speakers;
    public int serverTotalSpeakers;
    public String serverSampleKeys;

    public static final String GLOBAL_STOP_KEY = "__SAM_STOP_ALL_SIGNAL__";

    public PacketAnnounce() {}

    public PacketAnnounce(AnnounceData data, String linkKey, boolean playLocalSound, int x, int y, int z) {
        this(data, linkKey, playLocalSound, x, y, z, new ArrayList<SpeakerData>());
    }

    public PacketAnnounce(AnnounceData data, String linkKey, boolean playLocalSound, int x, int y, int z, List<SpeakerData> speakers) {
        this.startMelo = data.startMelo != null ? data.startMelo : "";
        this.bodySounds = data.bodySounds;
        this.arrMelo = data.arrMelo != null ? data.arrMelo : "";
        this.linkKey = linkKey != null ? linkKey : "";
        this.stopCommand = false;
        this.playLocalSound = playLocalSound;
        this.x = x; this.y = y; this.z = z;
        this.speakers = speakers != null ? speakers : new ArrayList<SpeakerData>();
        this.serverTotalSpeakers = 0;
        this.serverSampleKeys = "";
    }

    public PacketAnnounce(AnnounceData data, String linkKey, boolean playLocalSound, int x, int y, int z, List<SpeakerData> speakers, int serverTotalSpeakers, String serverSampleKeys) {
        this(data, linkKey, playLocalSound, x, y, z, speakers);
        this.serverTotalSpeakers = serverTotalSpeakers;
        this.serverSampleKeys = serverSampleKeys != null ? serverSampleKeys : "";
    }

    public PacketAnnounce(boolean stop, String linkKey) {
        this.stopCommand = stop;
        this.linkKey = (linkKey == null || linkKey.isEmpty()) ? GLOBAL_STOP_KEY : linkKey;
        this.bodySounds = new ArrayList<String>();
        this.startMelo = "";
        this.arrMelo = "";
        this.playLocalSound = false;
        this.x = 0; this.y = 0; this.z = 0;
        this.speakers = new ArrayList<SpeakerData>();
        this.serverTotalSpeakers = 0;
        this.serverSampleKeys = "";
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.stopCommand = buf.readBoolean();
        this.linkKey = ByteBufUtils.readUTF8String(buf);
        this.playLocalSound = buf.readBoolean();
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.speakers = new ArrayList<SpeakerData>();
        this.serverTotalSpeakers = 0;
        this.serverSampleKeys = "";

        if (stopCommand) return;

        this.startMelo = ByteBufUtils.readUTF8String(buf);
        this.arrMelo = ByteBufUtils.readUTF8String(buf);
        int size = buf.readInt();
        this.bodySounds = new ArrayList<String>();
        for (int i = 0; i < size; i++) {
            this.bodySounds.add(ByteBufUtils.readUTF8String(buf));
        }

        int speakerSize = buf.readInt();
        this.speakers = new ArrayList<SpeakerData>();
        for (int i = 0; i < speakerSize; i++) {
            SpeakerData speaker = new SpeakerData();
            speaker.x = buf.readInt();
            speaker.y = buf.readInt();
            speaker.z = buf.readInt();
            speaker.range = buf.readInt();
            speaker.volume = buf.readFloat();
            this.speakers.add(speaker);
        }

        this.serverTotalSpeakers = buf.readInt();
        this.serverSampleKeys = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(this.stopCommand);
        ByteBufUtils.writeUTF8String(buf, this.linkKey != null ? this.linkKey : "");
        buf.writeBoolean(this.playLocalSound);
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);

        if (stopCommand) return;

        ByteBufUtils.writeUTF8String(buf, this.startMelo != null ? this.startMelo : "");
        ByteBufUtils.writeUTF8String(buf, this.arrMelo != null ? this.arrMelo : "");
        buf.writeInt(this.bodySounds != null ? this.bodySounds.size() : 0);
        if (this.bodySounds != null) {
            for (String s : this.bodySounds) {
                ByteBufUtils.writeUTF8String(buf, s != null ? s : "");
            }
        }

        int speakerCount = 0;
        if (this.speakers != null) {
            for (SpeakerData speaker : this.speakers) {
                if (speaker != null) {
                    speakerCount++;
                }
            }
        }

        buf.writeInt(speakerCount);
        if (this.speakers != null) {
            for (SpeakerData speaker : this.speakers) {
                if (speaker == null) {
                    continue;
                }
                buf.writeInt(speaker.x);
                buf.writeInt(speaker.y);
                buf.writeInt(speaker.z);
                buf.writeInt(speaker.range);
                buf.writeFloat(speaker.volume);
            }
        }

        buf.writeInt(this.serverTotalSpeakers);
        ByteBufUtils.writeUTF8String(buf, this.serverSampleKeys != null ? this.serverSampleKeys : "");
    }
}
