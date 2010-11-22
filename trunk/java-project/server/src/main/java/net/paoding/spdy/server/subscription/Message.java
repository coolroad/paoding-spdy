package net.paoding.spdy.server.subscription;

import java.io.InputStream;

public class Message {

    private String url;

    private InputStream in;

    public Message(String url) {
        this.url = url;
    }

    public Message(String url, InputStream in) {
        this.url = url;
        this.in = in;
    }

    public void setInputStream(InputStream in) {
        this.in = in;
    }

    public InputStream getInputStream() {
        return in;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
