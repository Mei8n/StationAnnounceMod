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
            Object dataMapObj = extractDataMapFromTrain(train);
            if (dataMapObj != null) {
                String val = getValueFromDataMap(dataMapObj, key, type);

                if (val != null && !val.isEmpty() && !val.equals("null")) {
                    return val;
                }
            }

            NBTTagCompound nbt = new NBTTagCompound();
            train.writeToNBT(nbt);
            if (key.equalsIgnoreCase("ModelName")) {
                if (nbt.hasKey("trainName")) return nbt.getString("trainName");
                if (nbt.hasKey("ModelName")) return nbt.getString("ModelName");
            }

            NBTTagCompound customData = train.getEntityData();
            if (customData != null && customData.hasKey(key)) {
                return getValueFromNBT(customData, key, type);
            }

            if (nbt.hasKey(key)) {
                return getValueFromNBT(nbt, key, type);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        return null;
    }

    private static Object extractDataMapFromTrain(EntityTrainBase train) {
        Class<?> clazz = train.getClass();

        try {
            Method mState = clazz.getMethod("getResourceState");
            Object state = mState.invoke(train);
            if (state != null) {
                Method mDataMap = state.getClass().getMethod("getDataMap");
                return mDataMap.invoke(state);
            }
        } catch (Exception e) {}

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

    private static String getValueFromDataMap(Object dataMap, String key, int type) {
        if (dataMap instanceof Map) {
            Object val = ((Map<?, ?>) dataMap).get(key);
            if (val != null) return String.valueOf(val);
        }

        try {
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
