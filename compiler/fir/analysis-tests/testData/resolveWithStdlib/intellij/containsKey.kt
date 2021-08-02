// MODULE: m1
// FILE: Function.java

public interface Function<K,V> extends java.util.function.Function<K,V> {
    @Override
    default V apply(final K key) {
        return get(key);
    }

    default V put(final K key, final V value) {
        throw new UnsupportedOperationException();
    }

    V get(Object key);

    default V getOrDefault(final Object key, final V defaultValue) {
        final V value = get(key);
        return (value != null || containsKey(key)) ? value : defaultValue;
    }

    default boolean containsKey(final Object key) {
        return true;
    }

    default V remove(final Object key) {
        throw new UnsupportedOperationException();
    }

    default int size() {
        return -1;
    }

    default void clear() {
        throw new UnsupportedOperationException();
    }
}

// FILE: Int2IntFunction.java

public interface Int2IntFunction extends Function<Integer, Integer>, java.util.function.IntUnaryOperator {
    @Override
    default int applyAsInt(int operand) {
        return get(operand);
    }

    default int put(final int key, final int value) {
        throw new UnsupportedOperationException();
    }

    int get(int key);

    default int getOrDefault(final int key, final int defaultValue) {
        final int v;
        return ((v = get(key)) != defaultReturnValue() || containsKey(key)) ? v : defaultValue;
    }

    default int remove(final int key) {
        throw new UnsupportedOperationException();
    }

    @Deprecated
    @Override
    default Integer put(final Integer key, final Integer value) {
        final int k = (key).intValue();
        final boolean containsKey = containsKey(k);
        final int v = put(k, (value).intValue());
        return containsKey ? Integer.valueOf(v) : null;
    }

    @Deprecated
    @Override
    default Integer get(final Object key) {
        if (key == null)
            return null;
        final int k = ((Integer) (key)).intValue();
        final int v;
        return ((v = get(k)) != defaultReturnValue() || containsKey(k)) ? Integer.valueOf(v) : null;
    }

    @Deprecated
    @Override
    default Integer getOrDefault(final Object key, Integer defaultValue) {
        if (key == null)
            return defaultValue;
        final int k = ((Integer) (key)).intValue();
        final int v = get(k);
        return (v != defaultReturnValue() || containsKey(k)) ? Integer.valueOf(v) : defaultValue;
    }

    @Deprecated
    @Override
    default Integer remove(final Object key) {
        if (key == null)
            return null;
        final int k = ((Integer) (key)).intValue();
        return containsKey(k) ? Integer.valueOf(remove(k)) : null;
    }

    default boolean containsKey(int key) {
        return true;
    }

    @Deprecated
    @Override
    default boolean containsKey(final Object key) {
        return key == null ? false : containsKey(((Integer) (key)).intValue());
    }
}

// FILE: Int2IntMap.java

public interface Int2IntMap extends Int2IntFunction, Map<Integer, Integer> {
    interface FastEntrySet extends ObjectSet<Int2IntMap.Entry> {
        ObjectIterator<Int2IntMap.Entry> fastIterator();
        default void fastForEach(final Consumer<? super Int2IntMap.Entry> consumer) {
            forEach(consumer);
        }
    }
    @Override
    int size();

    @Override
    default void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    void defaultReturnValue(int rv);

    @Override
    int defaultReturnValue();

    ObjectSet<Int2IntMap.Entry> int2IntEntrySet();

    @Deprecated
    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    default ObjectSet<Map.Entry<Integer, Integer>> entrySet() {
        return (ObjectSet) int2IntEntrySet();
    }

    @Deprecated
    @Override
    default Integer put(final Integer key, final Integer value) {
        return Int2IntFunction.super.put(key, value);
    }

    @Deprecated
    @Override
    default Integer get(final Object key) {
        return Int2IntFunction.super.get(key);
    }

    @Deprecated
    @Override
    default Integer remove(final Object key) {
        return Int2IntFunction.super.remove(key);
    }

    @Override
    IntSet keySet();

    @Override
    IntCollection values();

    @Override
    boolean containsKey(int key);

    @Deprecated
    @Override
    default boolean containsKey(final Object key) {
        return Int2IntFunction.super.containsKey(key);
    }

    boolean containsValue(int value);

    @Deprecated
    @Override
    default boolean containsValue(final Object value) {
        return value == null ? false : containsValue(((Integer) (value)).intValue());
    }

    @Override
    default void forEach(final java.util.function.BiConsumer<? super Integer, ? super Integer> consumer) {
        final ObjectSet<Int2IntMap.Entry> entrySet = int2IntEntrySet();
        final Consumer<Int2IntMap.Entry> wrappingConsumer = (entry) -> consumer
        .accept(Integer.valueOf(entry.getIntKey()), Integer.valueOf(entry.getIntValue()));
        if (entrySet instanceof FastEntrySet) {
            ((FastEntrySet) entrySet).fastForEach(wrappingConsumer);
        } else {
            entrySet.forEach(wrappingConsumer);
        }
    }

    @Override
    default int getOrDefault(final int key, final int defaultValue) {
        final int v;
        return ((v = get(key)) != defaultReturnValue() || containsKey(key)) ? v : defaultValue;
    }

    default int putIfAbsent(final int key, final int value) {
        final int v = get(key), drv = defaultReturnValue();
        if (v != drv || containsKey(key))
            return v;
        put(key, value);
        return drv;
    }

    default boolean remove(final int key, final int value) {
        final int curValue = get(key);
        if (!((curValue) == (value)) || (curValue == defaultReturnValue() && !containsKey(key)))
            return false;
        remove(key);
        return true;
    }

    default boolean replace(final int key, final int oldValue, final int newValue) {
        final int curValue = get(key);
        if (!((curValue) == (oldValue)) || (curValue == defaultReturnValue() && !containsKey(key)))
            return false;
        put(key, newValue);
        return true;
    }

    default int replace(final int key, final int value) {
        return containsKey(key) ? put(key, value) : defaultReturnValue();
    }

    default int computeIfAbsent(final int key, final java.util.function.IntUnaryOperator mappingFunction) {
        java.util.Objects.requireNonNull(mappingFunction);
        final int v = get(key);
        if (v != defaultReturnValue() || containsKey(key))
            return v;
        int newValue = mappingFunction.applyAsInt(key);
        put(key, newValue);
        return newValue;
    }

    default int computeIfAbsentNullable(final int key,
                                        final java.util.function.IntFunction<? extends Integer> mappingFunction) {
        java.util.Objects.requireNonNull(mappingFunction);
        final int v = get(key), drv = defaultReturnValue();
        if (v != drv || containsKey(key))
            return v;
        Integer mappedValue = mappingFunction.apply(key);
        if (mappedValue == null)
            return drv;
        int newValue = (mappedValue).intValue();
        put(key, newValue);
        return newValue;
    }

    default int computeIfAbsent(final int key, final Int2IntFunction mappingFunction) {
        java.util.Objects.requireNonNull(mappingFunction);
        final int v = get(key), drv = defaultReturnValue();
        if (v != drv || containsKey(key))
            return v;
        if (!mappingFunction.containsKey(key))
            return drv;
        int newValue = mappingFunction.get(key);
        put(key, newValue);
        return newValue;
    }

    @Deprecated
    default int computeIfAbsentPartial(final int key, final Int2IntFunction mappingFunction) {
        return computeIfAbsent(key, mappingFunction);
    }

    default int computeIfPresent(final int key,
                                 final java.util.function.BiFunction<? super Integer, ? super Integer, ? extends Integer> remappingFunction) {
        java.util.Objects.requireNonNull(remappingFunction);
        final int oldValue = get(key), drv = defaultReturnValue();
        if (oldValue == drv && !containsKey(key))
            return drv;
        final Integer newValue = remappingFunction.apply(Integer.valueOf(key), Integer.valueOf(oldValue));
        if (newValue == null) {
            remove(key);
            return drv;
        }
        int newVal = (newValue).intValue();
        put(key, newVal);
        return newVal;
    }

    default int compute(final int key,
                        final java.util.function.BiFunction<? super Integer, ? super Integer, ? extends Integer> remappingFunction) {
        java.util.Objects.requireNonNull(remappingFunction);
        final int oldValue = get(key), drv = defaultReturnValue();
        final boolean contained = oldValue != drv || containsKey(key);
        final Integer newValue = remappingFunction.apply(Integer.valueOf(key),
                                                         contained ? Integer.valueOf(oldValue) : null);
        if (newValue == null) {
            if (contained)
                remove(key);
            return drv;
        }
        final int newVal = (newValue).intValue();
        put(key, newVal);
        return newVal;
    }

    default int merge(final int key, final int value,
                      final java.util.function.BiFunction<? super Integer, ? super Integer, ? extends Integer> remappingFunction) {
        java.util.Objects.requireNonNull(remappingFunction);
        final int oldValue = get(key), drv = defaultReturnValue();
        final int newValue;
        if (oldValue != drv || containsKey(key)) {
            final Integer mergedValue = remappingFunction.apply(Integer.valueOf(oldValue), Integer.valueOf(value));
            if (mergedValue == null) {
                remove(key);
                return drv;
            }
            newValue = (mergedValue).intValue();
        } else {
            newValue = value;
        }
        put(key, newValue);
        return newValue;
    }

    default int mergeInt(final int key, final int value, final java.util.function.IntBinaryOperator remappingFunction) {
        java.util.Objects.requireNonNull(remappingFunction);
        final int oldValue = get(key), drv = defaultReturnValue();
        final int newValue = oldValue != drv || containsKey(key)
        ? remappingFunction.applyAsInt(oldValue, value)
        : value;
        put(key, newValue);
        return newValue;
    }

    default int mergeInt(final int key, final int value,
                         final it.unimi.dsi.fastutil.ints.IntBinaryOperator remappingFunction) {
        return mergeInt(key, value, (java.util.function.IntBinaryOperator) remappingFunction);
    }
    @Deprecated
    @Override
    default Integer getOrDefault(final Object key, final Integer defaultValue) {
        return Map.super.getOrDefault(key, defaultValue);
    }

    @Deprecated
    @Override
    default Integer putIfAbsent(final Integer key, final Integer value) {
        return Map.super.putIfAbsent(key, value);
    }

    @Deprecated
    @Override
    default boolean remove(final Object key, final Object value) {
        return Map.super.remove(key, value);
    }

    @Deprecated
    @Override
    default boolean replace(final Integer key, final Integer oldValue, final Integer newValue) {
        return Map.super.replace(key, oldValue, newValue);
    }

    @Deprecated
    @Override
    default Integer replace(final Integer key, final Integer value) {
        return Map.super.replace(key, value);
    }

    @Deprecated
    @Override
    default Integer computeIfAbsent(final Integer key,
                                    final java.util.function.Function<? super Integer, ? extends Integer> mappingFunction) {
        return Map.super.computeIfAbsent(key, mappingFunction);
    }

    @Deprecated
    @Override
    default Integer computeIfPresent(final Integer key,
                                     final java.util.function.BiFunction<? super Integer, ? super Integer, ? extends Integer> remappingFunction) {
        return Map.super.computeIfPresent(key, remappingFunction);
    }

    @Deprecated
    @Override
    default Integer compute(final Integer key,
                            final java.util.function.BiFunction<? super Integer, ? super Integer, ? extends Integer> remappingFunction) {
        return Map.super.compute(key, remappingFunction);
    }

    @Deprecated
    @Override
    default Integer merge(final Integer key, final Integer value,
                          final java.util.function.BiFunction<? super Integer, ? super Integer, ? extends Integer> remappingFunction) {
        return Map.super.merge(key, value, remappingFunction);
    }

    interface Entry extends Map.Entry<Integer, Integer> {
        int getIntKey();
        @Deprecated
        @Override
        default Integer getKey() {
            return Integer.valueOf(getIntKey());
        }
        int getIntValue();
        int setValue(final int value);
        @Deprecated
        @Override
        default Integer getValue() {
            return Integer.valueOf(getIntValue());
        }
        @Deprecated
        @Override
        default Integer setValue(final Integer value) {
            return Integer.valueOf(setValue((value).intValue()));
        }
    }
}

// MODULE: m2(m1)
// FILE: use.kt

fun use(map: Int2IntMap, arg: Int): Boolean {
    if (map.containsKey(arg)) {
        return true
    }
    return map.containsKey(arg)
}