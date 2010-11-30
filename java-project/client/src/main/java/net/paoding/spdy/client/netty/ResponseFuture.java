/*
 * Copyright 2010-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License i distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.paoding.spdy.client.netty;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import net.paoding.spdy.client.Connector;
import net.paoding.spdy.client.Future;
import net.paoding.spdy.client.FutureListener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jboss.netty.util.internal.IoWorkerRunnable;

/**
 * The default {@link HttpFuture2} implementation.
 * 
 * @author <a href="http://www.jboss.org/netty/">The Netty Project</a>
 * @author <a href="http://gleamynode.net/">Trustin Lee</a>
 * 
 * @version $Rev: 2201 $, $Date: 2010-02-23 14:45:53 +0900 (Tue, 23 Feb
 *          2010) $
 */
class ResponseFuture<Request, Response> implements Future<Response> {

    private static final Log logger = LogFactory.getLog(ResponseFuture.class);

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
            logger.debug("The dead lock checker in " + ResponseFuture.class.getSimpleName()
                    + " has been disabled as requested at your own risk.");
        }
        ResponseFuture.useDeadLockChecker = useDeadLockChecker;
    }

    private Request request;

    private Response response;

    private final boolean cancellable;

    private FutureListener<Response> firstListener;

    private List<FutureListener<Response>> otherListeners;

    private boolean done;

    private Throwable cause;

    private int waiters;

    private Connector connector;

    /**
     * 创建一个请求或订阅的响应future
     * 
     */
    public ResponseFuture(Connector connection, Request request) {
        this.connector = connection;
        this.request = request;
        this.cancellable = false;
    }

    public Request getRequest() {
        return request;
    }

    @Override
    public Connector getConnector() {
        return connector;
    }

    @Override
    public Response getResponse() {
        return response;
    }

    public void setResponse(Response response) {
        this.response = response;
    }

    public synchronized boolean isDone() {
        return done;
    }

    public synchronized boolean isSuccess() {
        return done && cause == null;
    }

    public synchronized Throwable getCause() {
        if (cause != CANCELLED) {
            return cause;
        } else {
            return null;
        }
    }

    public synchronized boolean isCancelled() {
        return cause == CANCELLED;
    }

    public void addListener(FutureListener<Response> listener) {
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
                        otherListeners = new ArrayList<FutureListener<Response>>(1);
                    }
                    otherListeners.add(listener);
                }
            }
        }

        if (notifyNow) {
            notifyListener(listener);
        }
    }

    public void removeListener(FutureListener<Response> listener) {
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

    public ResponseFuture<Request, Response> await() throws InterruptedException {
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
        return this;
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return await0(unit.toNanos(timeout), true);
    }

    public boolean await(long timeoutMillis) throws InterruptedException {
        return await0(MILLISECONDS.toNanos(timeoutMillis), true);
    }

    public ResponseFuture<Request, Response> awaitUninterruptibly() {
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

        return this;
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
                for (FutureListener<Response> l : otherListeners) {
                    notifyListener(l);
                }
                otherListeners = null;
            }
        }
    }

    private void notifyListener(FutureListener<Response> l) {
        try {
            l.operationComplete(this);
        } catch (Throwable t) {
            logger.warn("An exception was thrown by " + ResponseFuture.class.getSimpleName() + ".",
                    t);
        }
    }

}
