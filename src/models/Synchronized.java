package models;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Synchronized<T> {
    protected T instance;
    protected final Lock lock = new ReentrantLock();

    public interface SynchronizedGet<T> {
        Object onLock(final T instance);
    }
    public interface SynchronizedSet<T> {
        T onLock(final T instance);
    }

    public Synchronized(final T instance) {
        this.instance = instance;
    }

    public Object get(final SynchronizedGet<T> callback) {
        if (lock.tryLock()) {
            try {
                return callback.onLock(instance);
            } finally {
                lock.unlock();
            }
        }

        return null;
    }
    public void set(final SynchronizedSet<T> callback) {
        if (lock.tryLock()) {
            try {
                instance = callback.onLock(instance);
            } finally {
                lock.unlock();
            }
        }
    }
}
