package net.paoding.spdy.common.supports;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jboss.netty.logging.InternalLogger;
import org.jboss.netty.logging.InternalLoggerFactory;
import org.jboss.netty.util.internal.IoWorkerRunnable;

public abstract class Listeners<T> {

    private static final InternalLogger logger = InternalLoggerFactory.getInstance(Listeners.class);

    private static final Throwable CANCELLED = new Throwable();

    private static volatile boolean useDeadLockChecker = true;

    private static boolean disabledDeadLockCheckerOnce;

    /**
     * Returns {@code true} if and only if the dead lock checker is
     * enabled.
     */
    public static boolean isUseDeadLockChecker() {
        return useDeadLockChecker;
    }

    /**
     * Enables or disables the dead lock checker. It is not recommended to
     * disable the dead lock checker. Disable it at your own risk!
     */
    public static void setUseDeadLockChecker(boolean useDeadLockChecker) {
        if (!useDeadLockChecker && !disabledDeadLockCheckerOnce) {
            disabledDeadLockCheckerOnce = true;
            logger.debug("The dead lock checker in " + Listeners.class.getSimpleName()
                    + " has been disabled as requested at your own risk.");
        }
        Listeners.useDeadLockChecker = useDeadLockChecker;
    }

    private T firstListener;

    private List<T> otherListeners;

    private final boolean cancellable;

    private boolean done;

    private Throwable cause;

    private int waiters;

    public Listeners(boolean cancellable) {
        this.cancellable = cancellable;
    }

    public synchronized boolean isDone() {
        return done;
    }

    public synchronized boolean isSuccess() {
        return done && cause == null;
    }

    public Throwable getCause() {
        return cause;
    }

    public void addListener(T listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        boolean notifyNow = false;
        synchronized (this) {
            if (done) {
                notifyNow = true;
            } else {
                if (firstListener == null) {
                    firstListener = listener;
                } else {
                    if (otherListeners == null) {
                        otherListeners = new ArrayList<T>(1);
                    }
                    otherListeners.add(listener);
                }
            }
        }

        if (notifyNow) {
            notifyListener(listener);
        }
    }

    public void removeListener(T listener) {
        if (listener == null) {
            throw new NullPointerException("listener");
        }

        synchronized (this) {
            if (!done) {
                if (listener == firstListener) {
                    if (otherListeners != null && !otherListeners.isEmpty()) {
                        firstListener = otherListeners.remove(0);
                    } else {
                        firstListener = null;
                    }
                } else if (otherListeners != null) {
                    otherListeners.remove(listener);
                }

            }
        }
    }

    public void await() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }

        synchronized (this) {
            while (!done) {
                checkDeadLock();
                waiters++;
                try {
                    this.wait();
                } finally {
                    waiters--;
                }
            }
        }
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(MILLISECONDS.toNanos(timeoutMillis), true);
    }

    public void awaitUninterruptibly() {
        boolean interrupted = false;
        synchronized (this) {
            while (!done) {
                checkDeadLock();
                waiters++;
                try {
                    this.wait();
                } catch (InterruptedException e) {
                    interrupted = true;
                } finally {
                    waiters--;
                }
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

    }

    public boolean awaitUninterruptibly(long timeout, TimeUnit unit) {
        try {
            return await0(unit.toNanos(timeout), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    public boolean awaitUninterruptibly(long timeoutMillis) {
        try {
            return await0(MILLISECONDS.toNanos(timeoutMillis), false);
        } catch (InterruptedException e) {
            throw new InternalError();
        }
    }

    private boolean await0(long timeoutNanos, boolean interruptable) throws InterruptedException {
        if (interruptable && Thread.interrupted()) {
            throw new InterruptedException();
        }

        long startTime = timeoutNanos <= 0 ? 0 : System.nanoTime();
        long waitTime = timeoutNanos;
        boolean interrupted = false;

        try {
            synchronized (this) {
                if (done) {
                    return done;
                } else if (waitTime <= 0) {
                    return done;
                }

                checkDeadLock();
                waiters++;
                try {
                    for (;;) {
                        try {
                            this.wait(waitTime / 1000000, (int) (waitTime % 1000000));
                        } catch (InterruptedException e) {
                            if (interruptable) {
                                throw e;
                            } else {
                                interrupted = true;
                            }
                        }

                        if (done) {
                            return true;
                        } else {
                            waitTime = timeoutNanos - (System.nanoTime() - startTime);
                            if (waitTime <= 0) {
                                return done;
                            }
                        }
                    }
                } finally {
                    waiters--;
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkDeadLock() {
        if (isUseDeadLockChecker() && IoWorkerRunnable.IN_IO_THREAD.get()) {
            throw new IllegalStateException("await*() in I/O thread causes a dead lock or "
                    + "sudden performance drop. Use addListener() instead or "
                    + "call await*() from a different thread.");
        }
    }

    public boolean setSuccess() {
        synchronized (this) {
            // Allow only once.
            if (done) {
                return false;
            }

            done = true;
            if (waiters > 0) {
                notifyAll();
            }
        }

        notifyListeners();
        return true;
    }

    public boolean setFailure(Throwable cause) {
        synchronized (this) {
            // Allow only once.
            if (done) {
                return false;
            }

            this.cause = cause;
            done = true;
            if (waiters > 0) {
                notifyAll();
            }
        }

        notifyListeners();
        return true;
    }

    public boolean cancel() {
        if (!cancellable) {
            return false;
        }

        synchronized (this) {
            // Allow only once.
            if (done) {
                return false;
            }

            cause = CANCELLED;
            done = true;
            if (waiters > 0) {
                notifyAll();
            }
        }

        notifyListeners();
        return true;
    }

    private void notifyListeners() {
        // This method doesn't need synchronization because:
        // 1) This method is always called after synchronized (this) block.
        //    Hence any listener list modification happens-before this method.
        // 2) This method is called only when 'done' is true.  Once 'done'
        //    becomes true, the listener list is never modified - see add/removeListener()
        if (firstListener != null) {
            notifyListener(firstListener);
            firstListener = null;

            if (otherListeners != null) {
                for (T l : otherListeners) {
                    notifyListener(l);
                }
                otherListeners = null;
            }
        }
    }

    public abstract void notifyListener(T listener);
}
