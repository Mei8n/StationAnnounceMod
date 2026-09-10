package jp.me1han.sam.compat.rtm;

import cpw.mods.fml.common.Loader;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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
    private static final Object[] NO_ARGUMENTS = new Object[0];

    private final ConcurrentMap<Class<?>, Accessors> accessorCache =
        new ConcurrentHashMap<Class<?>, Accessors>();
    private final Class<? extends Entity> trainClass;
    private final Accessors trainAccessors;
    private final Accessors formationAccessors;
    private final boolean available;

    public RtmTrainCompat() {
        boolean rtmLoaded = Loader.isModLoaded(RTM_MOD_ID);
        this.trainClass = rtmLoaded ? resolveTrainClass() : null;
        this.available = this.trainClass != null;
        if (this.trainClass != null) {
            // Resolve the stable RTM base API once during compat initialization.
            this.trainAccessors = this.accessorsFor(this.trainClass);
            Method getFormation = this.trainAccessors.getFormation;
            this.formationAccessors = getFormation == null
                ? null : this.accessorsFor(getFormation.getReturnType());
        } else {
            this.trainAccessors = null;
            this.formationAccessors = null;
        }
    }

    @Override
    public String getId() {
        return "RTM";
    }

    @Override
    public boolean isAvailable() {
        return this.available;
    }

    @Override
    public TrainSnapshot findFirstTrain(World world, AxisAlignedBB bounds, boolean controlCarOnly) {
        Entity entity = this.findFirstEntity(world, bounds, controlCarOnly);
        return entity == null ? null : new RtmTrainSnapshot(entity);
    }

    @Override
    public long findFirstFormationId(World world, AxisAlignedBB bounds, boolean controlCarOnly) {
        Entity entity = this.findFirstEntity(world, bounds, controlCarOnly);
        return entity == null ? -1L : this.getFormationId(entity);
    }

    @Override
    public TrainSnapshot wrap(Entity entity) {
        if (this.trainClass == null || !this.trainClass.isInstance(entity)) return null;
        return new RtmTrainSnapshot(entity);
    }

    @Override
    public boolean isInspectionTool(ItemStack stack) {
        return this.available && stack != null
            && stack.getItem().getClass().getName().contains("ItemCrowbar");
    }

    private Entity findFirstEntity(World world, AxisAlignedBB bounds, boolean controlCarOnly) {
        if (!this.available || world == null || bounds == null) return null;

        final List<?> entities;
        try {
            entities = world.getEntitiesWithinAABB(this.trainClass, bounds);
        } catch (LinkageError error) {
            return null;
        }

        for (int i = 0, size = entities.size(); i < size; i++) {
            Object candidate = entities.get(i);
            if (!(candidate instanceof Entity)) continue;
            Entity entity = (Entity) candidate;
            if (!controlCarOnly || this.isControlCar(entity)) return entity;
        }
        return null;
    }

    private boolean isControlCar(Entity entity) {
        Method method = this.trainAccessors.isControlCar;
        if (method == null) method = this.accessorsFor(entity.getClass()).isControlCar;
        Object value = invokeNoArgs(entity, method);
        return value instanceof Boolean && (Boolean) value;
    }

    private long getFormationId(Entity entity) {
        Method method = this.trainAccessors.getFormation;
        if (method == null) method = this.accessorsFor(entity.getClass()).getFormation;
        Object formation = invokeNoArgs(entity, method);
        if (formation != null) {
            Field idField = this.formationAccessors == null ? null : this.formationAccessors.id;
            if (idField == null) idField = this.accessorsFor(formation.getClass()).id;
            return readNumericField(formation, idField, entity.getEntityId());
        }
        return entity.getEntityId();
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
        } catch (SecurityException ignored) {
            // A restricted loader makes this integration unavailable.
        } catch (LinkageError ignored) {
            // Treat an incomplete or incompatible RTM installation as unavailable.
        }
        return null;
    }

    private Accessors accessorsFor(Class<?> type) {
        Accessors cached = this.accessorCache.get(type);
        if (cached != null) return cached;

        Accessors resolved = new Accessors(type);
        Accessors raced = this.accessorCache.putIfAbsent(type, resolved);
        return raced == null ? resolved : raced;
    }

    private final class RtmTrainSnapshot implements TrainSnapshot {
        private final Entity entity;
        private boolean dataMapResolved;
        private Object dataMap;
        private NBTTagCompound savedData;

        private RtmTrainSnapshot(Entity entity) {
            this.entity = entity;
        }

        @Override
        public int getEntityId() {
            return this.entity.getEntityId();
        }

        @Override
        public boolean isControlCar() {
            return RtmTrainCompat.this.isControlCar(this.entity);
        }

        @Override
        public long getFormationId() {
            return RtmTrainCompat.this.getFormationId(this.entity);
        }

        @Override
        public String extractData(String key, int type) {
            if (key == null) return null;

            try {
                String value = getValueFromDataMap(this.getDataMap(), key, type);
                if (isUseful(value)) return value;

                NBTTagCompound saved = this.getSavedData();
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
                // One unsupported value must not break tile ticking.
            } catch (LinkageError ignored) {
                // Allow SAM to keep running with a partially incompatible RTM.
            }
            return null;
        }

        private Object getDataMap() {
            if (!this.dataMapResolved) {
                this.dataMap = extractDataMap(this.entity);
                this.dataMapResolved = true;
            }
            return this.dataMap;
        }

        private NBTTagCompound getSavedData() {
            if (this.savedData == null) {
                this.savedData = new NBTTagCompound();
                this.entity.writeToNBT(this.savedData);
            }
            return this.savedData;
        }
    }

    private Object extractDataMap(Object train) {
        Accessors runtimeAccess = null;

        Method method = this.trainAccessors.getResourceState;
        if (method == null) {
            runtimeAccess = this.accessorsFor(train.getClass());
            method = runtimeAccess.getResourceState;
        }
        Object state = invokeNoArgs(train, method);
        Object dataMap = this.getDataMap(state);
        if (dataMap != null) return dataMap;

        method = this.trainAccessors.getTrainStateData;
        if (method == null) {
            if (runtimeAccess == null) runtimeAccess = this.accessorsFor(train.getClass());
            method = runtimeAccess.getTrainStateData;
        }
        state = invokeNoArgs(train, method);
        dataMap = this.getDataMap(state);
        if (dataMap != null) return dataMap;

        method = this.trainAccessors.getDataMap;
        if (method == null) {
            if (runtimeAccess == null) runtimeAccess = this.accessorsFor(train.getClass());
            method = runtimeAccess.getDataMap;
        }
        dataMap = invokeNoArgs(train, method);
        if (dataMap != null) return dataMap;

        Field field = this.trainAccessors.dataMap;
        if (field == null) {
            if (runtimeAccess == null) runtimeAccess = this.accessorsFor(train.getClass());
            field = runtimeAccess.dataMap;
        }
        return readField(train, field);
    }

    private Object getDataMap(Object state) {
        if (state == null) return null;
        Accessors stateAccess = this.accessorsFor(state.getClass());
        Object dataMap = invokeNoArgs(state, stateAccess.getDataMap);
        return dataMap != null ? dataMap : readField(state, stateAccess.dataMap);
    }

    private String getValueFromDataMap(Object dataMap, String key, int type) {
        if (dataMap == null) return null;
        if (dataMap instanceof Map) {
            Object value = ((Map<?, ?>) dataMap).get(key);
            if (value != null) return String.valueOf(value);
        }

        Accessors access = this.accessorsFor(dataMap.getClass());
        Object value = invoke(dataMap, access.getObject, key);
        if (value == null) value = invoke(dataMap, access.getStringKey, key);
        if (value != null) return String.valueOf(value);

        Method typedGetter;
        switch (type) {
            case 0: typedGetter = access.getString; break;
            case 1: typedGetter = access.getBoolean; break;
            case 2: typedGetter = access.getInteger; break;
            case 3: typedGetter = access.getDouble; break;
            default: return null;
        }
        value = invoke(dataMap, typedGetter, key);
        if (value == null && type == 2) value = invoke(dataMap, access.getInt, key);
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

    private static Object invokeNoArgs(Object target, Method method) {
        if (target == null || method == null) return null;
        try {
            return method.invoke(target, NO_ARGUMENTS);
        } catch (IllegalAccessException ignored) {
            return null;
        } catch (InvocationTargetException error) {
            rethrowFatal(error.getCause());
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, Method method, Object argument) {
        if (target == null || method == null) return null;
        try {
            return method.invoke(target, argument);
        } catch (IllegalAccessException ignored) {
            return null;
        } catch (InvocationTargetException error) {
            rethrowFatal(error.getCause());
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static Object readField(Object target, Field field) {
        if (target == null || field == null) return null;
        try {
            return field.get(target);
        } catch (IllegalAccessException ignored) {
            return null;
        } catch (IllegalArgumentException ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static long readNumericField(Object target, Field field, long fallback) {
        if (target == null || field == null) return fallback;
        try {
            if (field.getType().isPrimitive()) return field.getLong(target);
            Object value = field.get(target);
            return value instanceof Number ? ((Number)value).longValue() : fallback;
        } catch (IllegalAccessException ignored) {
            return fallback;
        } catch (IllegalArgumentException ignored) {
            return fallback;
        } catch (LinkageError ignored) {
            return fallback;
        }
    }

    private static void rethrowFatal(Throwable cause) {
        if (cause instanceof VirtualMachineError) throw (VirtualMachineError)cause;
        if (cause instanceof ThreadDeath) throw (ThreadDeath)cause;
    }

    private static Method findNoArgMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (SecurityException ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static Method findStringMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name, String.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (SecurityException ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static Method findObjectMethod(Class<?> type, String name) {
        try {
            return type.getMethod(name, Object.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (SecurityException ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    private static Field findField(Class<?> type, String name) {
        try {
            return type.getField(name);
        } catch (NoSuchFieldException ignored) {
            return null;
        } catch (SecurityException ignored) {
            return null;
        } catch (LinkageError ignored) {
            return null;
        }
    }

    /** Immutable reflection metadata, resolved at most once for each encountered runtime class. */
    private static final class Accessors {
        private final Method isControlCar;
        private final Method getFormation;
        private final Method getResourceState;
        private final Method getTrainStateData;
        private final Method getDataMap;
        private final Method getObject;
        private final Method getStringKey;
        private final Method getString;
        private final Method getBoolean;
        private final Method getInteger;
        private final Method getInt;
        private final Method getDouble;
        private final Field id;
        private final Field dataMap;

        private Accessors(Class<?> type) {
            this.isControlCar = findNoArgMethod(type, "isControlCar");
            this.getFormation = findNoArgMethod(type, "getFormation");
            this.getResourceState = findNoArgMethod(type, "getResourceState");
            this.getTrainStateData = findNoArgMethod(type, "getTrainStateData");
            this.getDataMap = findNoArgMethod(type, "getDataMap");
            this.getObject = findObjectMethod(type, "get");
            this.getStringKey = findStringMethod(type, "get");
            this.getString = findStringMethod(type, "getString");
            this.getBoolean = findStringMethod(type, "getBoolean");
            this.getInteger = findStringMethod(type, "getInteger");
            this.getInt = findStringMethod(type, "getInt");
            this.getDouble = findStringMethod(type, "getDouble");
            this.id = findField(type, "id");
            this.dataMap = findField(type, "dataMap");
        }
    }
}
