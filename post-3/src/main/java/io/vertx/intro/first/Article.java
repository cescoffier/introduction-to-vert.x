package io.vertx.intro.first;


import java.util.concurrent.atomic.AtomicLong;

public class Article {

    private static final AtomicLong COUNTER = new AtomicLong();

    private final long id;

    private String title;

    private String url;

    public Article(String title, String url) {
        this.id = COUNTER.getAndIncrement();
        this.title = title;
        this.url = url;
    }

    public Article() {
        this.id = COUNTER.getAndIncrement();
    }

    public long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Article setTitle(String title) {
        this.title = title;
        return this;
    }

    public String getUrl() {
        return url;
    }

    public Article setUrl(String url) {
        this.url = url;
        return this;
    }
}
