package models;

import utils.TCPServer;

import java.util.Queue;

public class SocketWrapper {
    protected String id;
    protected String name;
    protected TCPServer.Options options;
    protected final Synchronized<Queue<String>> messageQueue;

    public SocketWrapper(String id, String name, TCPServer.Options options, final Queue<String> messageQueue) {
        this.id = id;
        this.name = name;
        this.options = options;
        this.messageQueue = new Synchronized<>(messageQueue);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TCPServer.Options getOptions() {
        return options;
    }

    public Synchronized<Queue<String>> getMessageQueue() {
        return messageQueue;
    }
}
