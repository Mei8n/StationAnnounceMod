package jp.me1han.sam.api;

import net.minecraft.entity.Entity;
import jp.me1han.sam.compat.TrainSnapshot;
import jp.me1han.sam.compat.TrainCompatRegistry;

public class TrainDataExtractor {

    public static String extractData(Entity entity, String key, int type) {
        TrainSnapshot train = TrainCompatRegistry.get().wrap(entity);
        return train == null ? null : train.extractData(key, type);
    }
}
