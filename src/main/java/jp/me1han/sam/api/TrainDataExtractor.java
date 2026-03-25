package jp.me1han.sam.api;

import net.minecraft.entity.Entity;
import net.minecraft.nbt.NBTTagCompound;
import jp.ngt.rtm.entity.train.EntityTrainBase;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

public class TrainDataExtractor {

    public static String extractData(Entity entity, String key, int type) {
        if (!(entity instanceof EntityTrainBase)) {
            return null;
        }

        EntityTrainBase train = (EntityTrainBase) entity;

        try {
            // ① 【ATSAssistMod 互換】 dataMap からの取得
            // 車両スクリプト(JS)の変数はここに入ります
            Object dataMapObj = extractDataMapFromTrain(train);
            if (dataMapObj != null) {
                String val = getValueFromDataMap(dataMapObj, key, type);
                // System.out.println("[SAM-DEBUG] Found in dataMap! " + key + " = " + val);

                // 取得できたら、その値を返す (nullや "null" 文字列は弾く)
                if (val != null && !val.isEmpty() && !val.equals("null")) {
                    return val;
                }
            }

            // ② RTM標準のNBT (前回追加した ModelName などの保険用)
            NBTTagCompound nbt = new NBTTagCompound();
            train.writeToNBT(nbt);
            if (key.equalsIgnoreCase("ModelName")) {
                if (nbt.hasKey("trainName")) return nbt.getString("trainName");
                if (nbt.hasKey("ModelName")) return nbt.getString("ModelName");
            }

            // ③ Forgeの拡張データを確認
            NBTTagCompound customData = train.getEntityData();
            if (customData != null && customData.hasKey(key)) {
                return getValueFromNBT(customData, key, type);
            }

            // ④ 通常のNBTから取得
            if (nbt.hasKey(key)) {
                return getValueFromNBT(nbt, key, type);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * リフレクションを用いて、EntityTrainBase のあらゆる場所から dataMap を探し出す
     */
    private static Object extractDataMapFromTrain(EntityTrainBase train) {
        Class<?> clazz = train.getClass();

        // パターンA: getResourceState().getDataMap() (RTMの最も標準的な場所)
        try {
            Method mState = clazz.getMethod("getResourceState");
            Object state = mState.invoke(train);
            if (state != null) {
                Method mDataMap = state.getClass().getMethod("getDataMap");
                return mDataMap.invoke(state);
            }
        } catch (Exception e) {}

        // パターンB: getTrainStateData() 内部の dataMap (KaizPatchXなどで拡張されている場合)
        try {
            Method mState = clazz.getMethod("getTrainStateData");
            Object state = mState.invoke(train);
            if (state != null) {
                try {
                    Method mDataMap = state.getClass().getMethod("getDataMap");
                    return mDataMap.invoke(state);
                } catch (Exception e) {}
                try {
                    Field fDataMap = state.getClass().getField("dataMap");
                    return fDataMap.get(state);
                } catch (Exception e) {}
            }
        } catch (Exception e) {}

        // パターンC: 直接 train.getDataMap() / train.dataMap がある場合
        try {
            Method m = clazz.getMethod("getDataMap");
            return m.invoke(train);
        } catch (Exception e) {}
        try {
            Field f = clazz.getField("dataMap");
            return f.get(train);
        } catch (Exception e) {}

        return null;
    }

    /**
     * 取得した dataMap オブジェクトから、型に合わせて安全に値を取り出す
     */
    private static String getValueFromDataMap(Object dataMap, String key, int type) {
        // 標準的な Map インターフェース (HashMap等) の場合
        if (dataMap instanceof Map) {
            Object val = ((Map<?, ?>) dataMap).get(key);
            if (val != null) return String.valueOf(val);
        }

        // RTM独自の NGTObjectMap などの場合
        try {
            // まずは汎用的な get("key") メソッドを試す
            try {
                Method mGet = dataMap.getClass().getMethod("get", Object.class);
                Object val = mGet.invoke(dataMap, key);
                if (val != null) return String.valueOf(val);
            } catch (Exception e) {}

            try {
                Method mGet = dataMap.getClass().getMethod("get", String.class);
                Object val = mGet.invoke(dataMap, key);
                if (val != null) return String.valueOf(val);
            } catch (Exception e) {}

            // getString, getInteger などの型専用メソッドを試す
            String methodName = "";
            switch(type) {
                case 0: methodName = "getString"; break;
                case 1: methodName = "getBoolean"; break;
                case 2: methodName = "getInteger"; break;
                case 3: methodName = "getDouble"; break;
            }
            if (!methodName.isEmpty()) {
                try {
                    Method mTypeGet = dataMap.getClass().getMethod(methodName, String.class);
                    Object val = mTypeGet.invoke(dataMap, key);
                    if (val != null) return String.valueOf(val);
                } catch (Exception e) {
                    // getInteger が無ければ getInt も試す
                    if (type == 2) {
                        try {
                            Method mTypeGetInt = dataMap.getClass().getMethod("getInt", String.class);
                            Object val = mTypeGetInt.invoke(dataMap, key);
                            if (val != null) return String.valueOf(val);
                        } catch (Exception ex) {}
                    }
                }
            }
        } catch (Exception e) {}

        return null;
    }

    private static String getValueFromNBT(NBTTagCompound nbt, String key, int type) {
        switch (type) {
            case 0: return nbt.getString(key);
            case 1: return String.valueOf(nbt.getBoolean(key));
            case 2: return String.valueOf(nbt.getInteger(key));
            case 3: return String.valueOf(nbt.getDouble(key));
            default: return nbt.getString(key);
        }
    }
}
