/*
 * Copyright 2009 Red Hat, Inc.
 *
 * Red Hat licenses this file to you under the Apache License, version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License.  You may obtain a copy of the License at:
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package net.paoding.spdy.client;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.handler.codec.http.HttpMethod;
import org.jboss.netty.handler.codec.http.HttpRequest;

public class HttpParameters {

    public void copyTo(HttpRequest request) {
        ChannelBuffer buffer = toQueryString(ChannelBuffers.dynamicBuffer());
        if (request.getMethod() == HttpMethod.POST) {
            if (request.getContent().readable()) {
                throw new IllegalArgumentException("content should be empty for post parameters");
            }
            request.setContent(buffer);
            /** @see org.apache.catalina.connector.Request#parseParameters */
            request.setHeader("content-type", "application/x-www-form-urlencoded");
//            System.out.println("HttpParameters.copyTo: postBody='"
//                    + new String(buffer.array(), buffer.readerIndex(), buffer.readableBytes())
//                    + "'");
        } else {
            String query = new String(buffer.array(), buffer.readerIndex(), buffer.readableBytes());
            String uri = request.getUri();
            if (uri.indexOf('?') == -1) {
                uri = uri + "?" + query;
            } else {
                uri = uri + '&' + query;
            }
//            System.out.println("HttpParameters.copyTo: queryString='" + query + "'");

            request.setUri(uri);
        }
    }

    protected ChannelBuffer toQueryString(ChannelBuffer buffer) {
        Entry e = head.after;
        boolean first = true;
        while (e != head) {
            try {
                String name = e.getKey();
                String value = e.getValue();
                if (first) {
                    first = false;
                } else {
                    buffer.writeByte('&');
                }
                buffer.writeBytes(URLEncoder.encode(name, "utf8").getBytes());
                buffer.writeByte('=');
                buffer.writeBytes(URLEncoder.encode(value, "utf8").getBytes());
                e = e.after;
            } catch (UnsupportedEncodingException e1) {
                throw new Error(e1);
            }
        }
        return buffer;
    }

    private final Entry[] entries = new Entry[BUCKET_SIZE];

    private final Entry head = new Entry(-1, null, null);

    public HttpParameters() {
        head.before = head.after = head;
    }

    public void addParameter(final String name, final Object value) {
        String strVal = toString(value);
        int h = hash(name);
        int i = index(h);
        addParameter0(h, i, name, strVal);
    }

    public void removeParameter(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }
        int h = hash(name);
        int i = index(h);
        removeParameter0(h, i, name);
    }

    public void setParameter(final String name, final Object value) {
        String strVal = toString(value);
        int h = hash(name);
        int i = index(h);
        removeParameter0(h, i, name);
        addParameter0(h, i, name, strVal);
    }

    public void setParameter(final String name, final Iterable<?> values) {
        if (values == null) {
            throw new NullPointerException("values");
        }

        int h = hash(name);
        int i = index(h);

        removeParameter0(h, i, name);
        for (Object v : values) {
            if (v == null) {
                break;
            }
            String strVal = toString(v);
            addParameter0(h, i, name, strVal);
        }
    }

    public void clear() {
        for (int i = 0; i < entries.length; i++) {
            entries[i] = null;
        }
        head.before = head.after = head;
    }

    public String getParameter(String name, String defaultValue) {
        String value = getParameter(name);
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    public String getParameter(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        int h = hash(name);
        int i = index(h);
        Entry e = entries[i];
        while (e != null) {
            if (e.hash == h && eq(name, e.key)) {
                return e.value;
            }

            e = e.next;
        }
        return null;
    }

    public int getIntParameter(String name) {
        String value = getParameter(name);
        if (value == null) {
            throw new NumberFormatException("null");
        }
        return Integer.parseInt(value);
    }

    public int getIntParameter(String name, int defaultValue) {
        String value = getParameter(name);
        if (value == null) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public List<String> getParameters(final String name) {
        if (name == null) {
            throw new NullPointerException("name");
        }

        LinkedList<String> values = new LinkedList<String>();

        int h = hash(name);
        int i = index(h);
        Entry e = entries[i];
        while (e != null) {
            if (e.hash == h && eq(name, e.key)) {
                values.addFirst(e.value);
            }
            e = e.next;
        }
        return values;
    }

    public List<Map.Entry<String, String>> getParameters() {
        List<Map.Entry<String, String>> all = new LinkedList<Map.Entry<String, String>>();

        Entry e = head.after;
        while (e != head) {
            all.add(e);
            e = e.after;
        }
        return all;
    }

    public boolean contains(String name) {
        return getParameter(name) != null;
    }

    public Set<String> getParameterNames() {
        Set<String> names = new TreeSet<String>();

        Entry e = head.after;
        while (e != head) {
            names.add(e.key);
            e = e.after;
        }
        return names;
    }

    private void addParameter0(int h, int i, final String name, final String value) {
        // Update the hash table.
        Entry e = entries[i];
        Entry newEntry;
        entries[i] = newEntry = new Entry(h, name, value);
        newEntry.next = e;

        // Update the linked list.
        newEntry.addBefore(head);
    }

    private void removeParameter0(int h, int i, String name) {
        Entry e = entries[i];
        if (e == null) {
            return;
        }

        for (;;) {
            if (e.hash == h && eq(name, e.key)) {
                e.remove();
                Entry next = e.next;
                if (next != null) {
                    entries[i] = next;
                    e = next;
                } else {
                    entries[i] = null;
                    return;
                }
            } else {
                break;
            }
        }

        for (;;) {
            Entry next = e.next;
            if (next == null) {
                break;
            }
            if (next.hash == h && eq(name, next.key)) {
                e.next = next.next;
                next.remove();
            } else {
                e = next;
            }
        }
    }

    private static String toString(Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private static final int BUCKET_SIZE = 17;

    private static int hash(String name) {
        int h = 0;
        for (int i = name.length() - 1; i >= 0; i--) {
            char c = name.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c += 32;
            }
            h = 31 * h + c;
        }

        if (h > 0) {
            return h;
        } else if (h == Integer.MIN_VALUE) {
            return Integer.MAX_VALUE;
        } else {
            return -h;
        }
    }

    private static boolean eq(String name1, String name2) {
        int nameLen = name1.length();
        if (nameLen != name2.length()) {
            return false;
        }

        for (int i = nameLen - 1; i >= 0; i--) {
            char c1 = name1.charAt(i);
            char c2 = name2.charAt(i);
            if (c1 != c2) {
                if (c1 >= 'A' && c1 <= 'Z') {
                    c1 += 32;
                }
                if (c2 >= 'A' && c2 <= 'Z') {
                    c2 += 32;
                }
                if (c1 != c2) {
                    return false;
                }
            }
        }
        return true;
    }

    private static int index(int hash) {
        return hash % BUCKET_SIZE;
    }

    private static final class Entry implements Map.Entry<String, String> {

        final int hash;

        final String key;

        String value;

        Entry next;

        Entry before, after;

        Entry(int hash, String key, String value) {
            this.hash = hash;
            this.key = key;
            this.value = value;
        }

        void remove() {
            before.after = after;
            after.before = before;
        }

        void addBefore(Entry e) {
            after = e;
            before = e.before;
            before.after = this;
            after.before = this;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }

        public String setValue(String value) {
            if (value == null) {
                throw new NullPointerException("value");
            }
            //            HttpCodecUtil.validateHeaderValue(value);
            String oldValue = this.value;
            this.value = value;
            return oldValue;
        }

        @Override
        public String toString() {
            return key + "=" + value;
        }
    }
}
