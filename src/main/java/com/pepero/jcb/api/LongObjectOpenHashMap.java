package com.pepero.jcb.api;

import java.util.Arrays;

/**
 * Primitive long-key open addressing Hashmap
 */
public final class LongObjectOpenHashMap<V> {
    private static final long EMPTY = Long.MIN_VALUE;

    private long[] keys;
    private Object[] values;
    private int size;
    private int mask;
    private final float loadFactor;

    public LongObjectOpenHashMap() {
        this(16, 0.75f);
    }

    public LongObjectOpenHashMap(int initialCapacity, float loadFactor) {
        if (loadFactor <= 0f || loadFactor >= 1f) {
            throw new IllegalArgumentException("loadFactor must be in (0, 1)");
        }

        int minCap = Math.max(16, initialCapacity);
        int cap = Integer.highestOneBit(minCap - 1) << 1;
        this.loadFactor = loadFactor;
        this.keys = new long[cap];
        Arrays.fill(this.keys, EMPTY);
        this.values = new Object[cap];
        this.mask = cap - 1;
    }

    private static int mix(long key) {
        long h = key ^ (key >>> 32);
        h *= 0x9E3779B97F4A7C15L;
        return (int) (h ^ (h >>> 29));
    }

    public V put(long key, V value) {
        if (key == EMPTY) throw new IllegalArgumentException("sentinel value not allowed as key");
        if ((size + 1) > keys.length * loadFactor) resize();

        int idx = mix(key) & mask;
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key) {
                @SuppressWarnings("unchecked")
                V old = (V) values[idx];
                values[idx] = value;
                return old;
            }
            idx = (idx + 1) & mask;
        }
        keys[idx] = key;
        values[idx] = value;
        size++;
        return null;
    }

    @SuppressWarnings("unchecked")
    public V get(long key) {
        int idx = mix(key) & mask;
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key) return (V) values[idx];
            idx = (idx + 1) & mask;
        }
        return null;
    }

    public boolean containsKey(long key) {
        int idx = mix(key) & mask;
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key) return true;
            idx = (idx + 1) & mask;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public V remove(long key) {
        int idx = mix(key) & mask;
        while (keys[idx] != EMPTY) {
            if (keys[idx] == key) break;
            idx = (idx + 1) & mask;
        }
        if (keys[idx] == EMPTY) return null;

        V old = (V) values[idx];

        int hole = idx;
        int probe = (hole + 1) & mask;
        while (keys[probe] != EMPTY) {
            int ideal = mix(keys[probe]) & mask;
            if (!inCyclicRange(hole, ideal, probe)) {
                keys[hole] = keys[probe];
                values[hole] = values[probe];
                hole = probe;
            }
            probe = (probe + 1) & mask;
        }
        keys[hole] = EMPTY;
        values[hole] = null;
        size--;
        return old;
    }

    private boolean inCyclicRange(int hole, int k, int probe) {
        if (hole <= probe) {
            return hole < k && k <= probe;
        } else {
            return k > hole || k <= probe;
        }
    }

    public void clear() {
        Arrays.fill(keys, EMPTY);
        Arrays.fill(values, null);
        size = 0;
    }

    public int size() {
        return size;
    }

    public void putAll(java.util.Map<Long, ? extends V> other) {
        for (java.util.Map.Entry<Long, ? extends V> e : other.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    private void resize() {
        long[] oldKeys = keys;
        Object[] oldValues = values;

        int newCap = keys.length << 1;
        keys = new long[newCap];
        Arrays.fill(keys, EMPTY);
        values = new Object[newCap];
        mask = newCap - 1;

        int oldSize = size;
        size = 0;
        for (int i = 0; i < oldKeys.length; i++) {
            if (oldKeys[i] != EMPTY) {
                @SuppressWarnings("unchecked")
                V v = (V) oldValues[i];
                putRehash(oldKeys[i], v);
            }
        }
        size = oldSize;
    }

    private void putRehash(long key, V value) {
        int idx = mix(key) & mask;
        while (keys[idx] != EMPTY) idx = (idx + 1) & mask;
        keys[idx] = key;
        values[idx] = value;
    }
}