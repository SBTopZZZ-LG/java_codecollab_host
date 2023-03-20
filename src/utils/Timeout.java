package utils;

import models.Synchronized;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class Timeout {
    public static void main(String[] args) {
        final Boolean value = withCallable(didTimeout -> {
            for (int i = 0; i < 10; i++) {
                final AtomicBoolean shouldBreak = new AtomicBoolean(false);
                didTimeout.get(_didTimeout -> {
                    shouldBreak.set(_didTimeout);
                    return null;
                });

                if (shouldBreak.get())
                    break;

                i--;
            }

            return false;
        }, 1000);

        System.out.println("Done! Returned " + value);
    }

    public interface Callable<T> {
        T run(final Synchronized<Boolean> didTimeout);
    }

    public static <T> T withCallable(final Callable<T> callable, final long millis) {
        final ExecutorService executor = Executors.newSingleThreadExecutor();

        final AtomicReference<Synchronized<Boolean>> didTimeoutFlag = new AtomicReference<>(new Synchronized<>(false));
        final Future<T> future = executor.submit(() -> callable.run(didTimeoutFlag.get()));
        executor.shutdown();

        try {
            return future.get(millis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException | ExecutionException | InterruptedException e) {
            e.printStackTrace();

            didTimeoutFlag.get().set(instance -> true);
            future.cancel(true);
            executor.shutdown();
        }

        return null;
    }
}
