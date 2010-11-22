package net.paoding.spdy.common.supports;

public interface Registry<T> {

    public void put(int id, T t, int expireSeconds);

    public void remove(int id);

    public T get(int id);

    public boolean contains(int id);

}
