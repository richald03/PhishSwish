package com.example.phishswish.model;

public class InboxMessage {
    public String address;
    public String body;
    public long date;
    public boolean isRead;

    public InboxMessage(String address, String body, long date, boolean isRead) {
        this.address = address;
        this.body = body;
        this.date = date;
        this.isRead = isRead;
    }
}
