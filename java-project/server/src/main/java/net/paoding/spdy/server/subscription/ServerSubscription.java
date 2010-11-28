package net.paoding.spdy.server.subscription;

import java.util.Map;

public interface ServerSubscription {

    public static final String REQUEST_ATTR = ServerSubscription.class.getName();

    public void setAttribute(String name, Object value);

    public void removeAttribute(String name);

    public Map<String, Object> getAttributes();

    public void push(Message mouse);

    public boolean isClosed();

    public void accept();

    public void setClosed();

    public void addListener(SubscriptionListener listener);

    boolean isAccepted();
}
