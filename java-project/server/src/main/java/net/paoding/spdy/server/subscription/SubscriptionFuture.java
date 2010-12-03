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
package net.paoding.spdy.server.subscription;

import java.util.concurrent.TimeUnit;

/**
 * 
 * @author qieqie.wang@gmail.com
 * 
 */
public interface SubscriptionFuture {

    /**
     * 
     * 返回该future所属的订阅对象
     * 
     * @return
     */
    public Subscription getSubscription();

    /**
     * Returns {@code true} if and only if this future is complete,
     * regardless of whether the operation was successful, failed, or
     * cancelled.
     */
    boolean isDone();

    /**
     * Returns {@code true} if and only if the I/O operation was completed
     * successfully.
     */
    boolean isSuccess();

    /**
     * Returns the cause of the failed I/O operation if the I/O operation
     * has failed.
     * 
     * @return the cause of the failure. {@code null} if succeeded or this
     *         future is not completed yet.
     */
    Throwable getCause();

    /**
     * Adds the specified listener to this future. The specified listener
     * is notified when this future is {@linkplain #isDone() done}. If this
     * future is already completed, the specified listener is notified
     * immediately.
     */
    void addListener(SubscriptionFutureListener listener);

    /**
     * Removes the specified listener from this future. The specified
     * listener is no longer notified when this future is
     * {@linkplain #isDone() done}. If the specified listener is not
     * associated with this future, this method does nothing and returns
     * silently.
     */
    void removeListener(SubscriptionFutureListener listener);

    /**
     * Waits for this future to be completed.
     * 
     * @throws InterruptedException if the current thread was interrupted
     */
    SubscriptionFuture await() throws InterruptedException;

    /**
     * Waits for this future to be completed without interruption. This
     * method catches an {@link InterruptedException} and discards it
     * silently.
     */
    SubscriptionFuture awaitUninterruptibly();

    /**
     * Waits for this future to be completed within the specified time
     * limit.
     * 
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     * 
     * @throws InterruptedException if the current thread was interrupted
     */
    boolean await(long timeout, TimeUnit unit) throws InterruptedException;

    /**
     * Waits for this future to be completed within the specified time
     * limit.
     * 
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     * 
     * @throws InterruptedException if the current thread was interrupted
     */
    boolean await(long timeoutMillis) throws InterruptedException;

    /**
     * Waits for this future to be completed within the specified time
     * limit without interruption. This method catches an
     * {@link InterruptedException} and discards it silently.
     * 
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeout, TimeUnit unit);

    /**
     * Waits for this future to be completed within the specified time
     * limit without interruption. This method catches an
     * {@link InterruptedException} and discards it silently.
     * 
     * @return {@code true} if and only if the future was completed within
     *         the specified time limit
     */
    boolean awaitUninterruptibly(long timeoutMillis);
}
