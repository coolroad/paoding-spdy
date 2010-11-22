package net.paoding.spdy.common.supports;

import java.util.HashMap;
import java.util.Map;

public class RegistryImpl<T> implements Registry<T>, Runnable {

    static class Entry<T> {

        int id;

        T value;

        long datesOfDeath;
    }

    private final Entry<T>[] firstEntries;

    private final Map<Integer, Entry<T>> secondary = new HashMap<Integer, Entry<T>>();
    

    @SuppressWarnings("unchecked")
    public RegistryImpl(int capacityOfFirstEntries) {
        firstEntries = new Entry[capacityOfFirstEntries];
    }

    @Override
    public void put(int id, T value, int expireSeconds) {
        if (expireSeconds <= 0) {
            throw new IllegalArgumentException("expireSeconds");
        }
        Entry<T> entry = firstEntries[indexOf(id)];
        if (entry != null) {
            if (entry.datesOfDeath > System.currentTimeMillis()) {
                secondary.put(entry.id, entry);
            }
        }
        entry = new Entry<T>();
        entry.id = id;
        entry.value = value;
        entry.datesOfDeath = System.currentTimeMillis() + expireSeconds * 1000;
        firstEntries[indexOf(id)] = entry;
    }

    private int indexOf(int id) {
        return id;
    }

    @Override
    public void remove(int id) {
        Entry<T> entry = firstEntries[indexOf(id)];
        if (entry != null && entry.id == id) {
            firstEntries[indexOf(id)] = null;
        } else {
            secondary.remove(id);
        }
    }

    @Override
    public T get(int id) {
        Entry<T> entry = firstEntries[indexOf(id)];
        if (entry == null || entry.id != id) {
            entry = secondary.get(id);
        }
        if (entry != null && entry.datesOfDeath > System.currentTimeMillis()) {
            return entry.value;
        } else {
            return null;
        }
    }

    @Override
    public boolean contains(int id) {
        return get(id) != null;
    }

    @Override
    public void run() {
        // 
    }
}
