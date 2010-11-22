package net.paoding.spdy.server.tomcat.impl.trap;

import net.paoding.spdy.server.subscription.Message;

class SpdyMessage {

    private int associatedId;

    private Message message;

    private int streamId;

    String uriPrefix;

    public SpdyMessage(String uriPrefix) {
        this.uriPrefix = uriPrefix;

    }

    public String getUrl() {
        String url = message.getUrl();
        if (url.indexOf("://") == -1) {
            if (url.startsWith("/")) {
                url = uriPrefix + url;
            } else {
                url = uriPrefix + "/" + url;
            }
            message.setUrl(url);
        }
        return url;
    }

    public int getAssociatedId() {
        return associatedId;
    }

    public void setAssociatedId(int associatedId) {
        this.associatedId = associatedId;
    }

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }

    public int getStreamId() {
        return streamId;
    }

    public void setStreamId(int streamId) {
        this.streamId = streamId;
    }

}
