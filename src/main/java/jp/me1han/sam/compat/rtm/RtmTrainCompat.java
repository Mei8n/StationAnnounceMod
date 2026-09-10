package jp.me1han.sam.compat.rtm;

import cpw.mods.fml.common.Loader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jp.me1han.sam.compat.TrainCompat;
import jp.me1han.sam.compat.TrainSnapshot;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/** RTM adapter. RTM classes are resolved by name so the base mod builds and loads without RTM. */
public final class RtmTrainCompat implements TrainCompat {
    private static final String RTM_MOD_ID = "RTM";
    private static final String TRAIN_CLASS_NAME = "jp.ngt.rtm.entity.train.EntityTrainBase";

    private final Class<? extends Entity> trainClass;

    public RtmTrainCompat() {
        this.trainClass = resolveTrainClass();
    }

    @Override
    public String getId() {
        return "RTM";
    }

    @Override
    public boolean isAvailable() {
        return Loader.isModLoaded(RTM_MOD_ID) && this.trainClass != null;
    }

    @Override
    public List<TrainSnapshot> findTrains(World world, AxisAlignedBB bounds) {
        if (!this.isAvailable() || world == null) return Collections.emptyList();

        List<?> entities = world.getEntitiesWithinAABB(this.trainClass, bounds);
        List<TrainSnapshot> trains = new ArrayList<TrainSnapshot>(entities.size());
        for (Object entity : entities) {
            if (entity instanceof Entity) {
                TrainSnapshot train = this.wrap((Entity) entity);
                if (train != null) trains.add(train);
            }
        }
        return trains;
    }

    @Override
    public TrainSnapshot wrap(Entity entity) {
        if (this.trainClass == null || !this.trainClass.isInstance(entity)) return null;
        return new RtmTrainSnapshot(entity);
    }

    @Override
    public boolean isInspectionTool(ItemStack stack) {
        return this.isAvailable() && stack != null
            && stack.getItem().getClass().getName().contains("ItemCrowbar");
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Entity> resolveTrainClass() {
        try {
            Class<?> candidate = Class.forName(TRAIN_CLASS_NAME, false, RtmTrainCompat.class.getClassLoader());
            if (Entity.class.isAssignableFrom(candidate)) {
                return (Class<? extends Entity>) candidate;
            }
        } catch (ClassNotFoundException ignored) {
            // RTM is optional.
        } catch (LinkageError ignored) {
            // Treat an incomplete or incompatible RTM installation as unavailable.
        }
        return null;
    }

    private static final class RtmTrainSnapshot implements TrainSnapshot {
        private final Entity entity;

        private RtmTrainSnapshot(Entity entity) {
            this.entity = entity;
        }

        @Override
        public int getEntityId() {
            return this.entity.getEntityId();
        }

        @Override
        public boolean isControlCar() {
            Object value = invokeNoArgs(this.entity, "isControlCar");
            return value instanceof Boolean && (Boolean) value;
        }

        @Override
        public long getFormationId() {
            Object formation = invokeNoArgs(this.entity, "getFormation");
            if (formation != null) {
                Object id = readField(formation, "id");
                if (id instanceof Number) return ((Number) id).longValue();
            }
            return this.getEntityId();
        }

        @Override
        public String extractData(String key, int type) {
            if (key == null) return null;

            try {
                Object dataMap = extractDataMap(this.entity);
                String value = getValueFromDataMap(dataMap, key, type);
                if (isUseful(value)) return value;

                NBTTagCompound saved = new NBTTagCompound();
                this.entity.writeToNBT(saved);
                if (key.equalsIgnoreCase("ModelName")) {
                    if (saved.hasKey("trainName")) return saved.getString("trainName");
                    if (saved.hasKey("ModelName")) return saved.getString("ModelName");
                }

                NBTTagCompound customData = this.entity.getEntityData();
                if (customData != null && customData.hasKey(key)) {
                    return getValueFromNbt(customData, key, type);
                }
                if (saved.hasKey(key)) return getValueFromNbt(saved, key, type);
            } catch (RuntimeException ignored) {
                // A single unsupported RTM version or value must not break tile ticking.
            }
            return null;
        }
    }

    private static Object extractDataMap(Object train) {
        Object state = invokeNoArgs(train, "getResourceState");
        Object dataMap = invokeNoArgs(state, "getDataMap");
        if (dataMap != null) return dataMap;

        state = invokeNoArgs(train, "getTrainStateData");
        dataMap = invokeNoArgs(state, "getDataMap");
        if (dataMap != null) return dataMap;
        dataMap = readField(state, "dataMap");
        if (dataMap != null) return dataMap;

        dataMap = invokeNoArgs(train, "getDataMap");
        return dataMap != null ? dataMap : readField(train, "dataMap");
    }

    private static String getValueFromDataMap(Object dataMap, String key, int type) {
        if (dataMap == null) return null;
        if (dataMap instanceof Map) {
            Object value = ((Map<?, ?>) dataMap).get(key);
            if (value != null) return String.valueOf(value);
        }

        Object value = invoke(dataMap, "get", Object.class, key);
        if (value == null) value = invoke(dataMap, "get", String.class, key);
        if (value != null) return String.valueOf(value);

        String methodName;
        switch (type) {
            case 0: methodName = "getString"; break;
            case 1: methodName = "getBoolean"; break;
            case 2: methodName = "getInteger"; break;
            case 3: methodName = "getDouble"; break;
            default: return null;
        }
        value = invoke(dataMap, methodName, String.class, key);
        if (value == null && type == 2) value = invoke(dataMap, "getInt", String.class, key);
        return value == null ? null : String.valueOf(value);
    }

    private static String getValueFromNbt(NBTTagCompound nbt, String key, int type) {
        switch (type) {
            case 0: return nbt.getString(key);
            case 1: return String.valueOf(nbt.getBoolean(key));
            case 2: return String.valueOf(nbt.getInteger(key));
            case 3: return String.valueOf(nbt.getDouble(key));
            default: return nbt.getString(key);
        }
    }

    private static boolean isUseful(String value) {
        return value != null && !value.isEmpty() && !value.equals("null");
    }

    private static Object invokeNoArgs(Object target, String methodName) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName);
            return method.invoke(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName, Class<?> parameterType, Object argument) {
        if (target == null) return null;
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            return method.invoke(target, argument);
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static Object readField(Object target, String fieldName) {
        if (target == null) return null;
        try {
            Field field = target.getClass().getField(fieldName);
            return field.get(target);
        } catch (ReflectiveOperationException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
