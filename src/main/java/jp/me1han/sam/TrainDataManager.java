package jp.me1han.sam;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 列車選別装置から届いたデータをリンクキーごとに保持するクラス
 */
public class TrainDataManager {
    public static final TrainDataManager INSTANCE = new TrainDataManager();

    // リンクキー(String) と 車両データ(String) のマップ
    private final Map<String, String> dataMap = new ConcurrentHashMap<>();

    private TrainDataManager() {}

    /**
     * パケット受信時にデータを保存
     */
    public void putData(String linkKey, String value) {
        if (linkKey == null || linkKey.isEmpty()) return;
        dataMap.put(linkKey, value);
        StationAnnounceMod.logger.info("[SAM] Train Data Updated: " + linkKey + " = " + value);
    }

    /**
     * 放送装置が実行される時にデータを取得
     */
    public String getData(String linkKey) {
        return dataMap.getOrDefault(linkKey, "");
    }

    /**
     * 放送終了後などにデータをクリアしたい場合（任意）
     */
    public void clearData(String linkKey) {
        dataMap.remove(linkKey);
    }
}
