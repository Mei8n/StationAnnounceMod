package jp.me1han.sam;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import java.lang.reflect.Method;

public class RTMCompat {
    // 1.7.10でのRTM車両クラス名
    private static final String TRAIN_CLASS = "jp.ngt.rtm.entity.train.EntityTrainBase";

    public static boolean isTrain(Object entity) {
        if (entity == null) return false;
        try {
            Class<?> clazz = Class.forName(TRAIN_CLASS);
            return clazz.isInstance(entity);
        } catch (ClassNotFoundException e) {
            return false; // RTMが入っていない
        }
    }

    public static String getDataMapValue(Object entity, String key, int type) {
        try {
            // EntityTrainBase#getResourceState() を取得
            Method getResourceState = entity.getClass().getMethod("getResourceState");
            Object resourceState = getResourceState.invoke(entity);
            if (resourceState == null) return null;

            // ResourceState#getDataMap() を取得
            Method getDataMap = resourceState.getClass().getMethod("getDataMap");
            NBTTagCompound nbt = (NBTTagCompound) getDataMap.invoke(resourceState);

            if (nbt == null || !nbt.hasKey(key)) return null;

            switch (type) {
                case 1: return String.valueOf(nbt.getInteger(key));
                case 2: return String.valueOf(nbt.getBoolean(key));
                case 3: return String.valueOf(nbt.getDouble(key));
                default: return nbt.getString(key);
            }
        } catch (Exception e) {
            return null;
        }
    }

    // RTMCompat.java 内に追加
    public static boolean isCrowbar(ItemStack stack) {
        if (stack == null || stack.getItem() == null) return false;
        try {
            // クラス名で判定することで、RTMがなくてもコンパイル・実行エラーを防ぐ
            Class<?> clazz = Class.forName("jp.ngt.rtm.item.ItemCrowbar");
            return clazz.isInstance(stack.getItem());
        } catch (ClassNotFoundException e) {
            return false;
        }
    }
}
