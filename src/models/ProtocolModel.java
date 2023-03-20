package models;

import utils.TCPServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtocolModel {
    public static ProtocolModel instance = null;
    public static abstract class Options implements TCPServer.Options {
        public abstract List<SocketWrapper> getClientSocketWrappers();

        public abstract SocketWrapper getClientSocketWrapper();
    }

    public final TCPServer server;
    public final Map<Socket, SocketWrapper> clients;

    private final Pattern requestHeadPattern = Pattern.compile("^(?<method>[a-z]+) +(?<requestId>[a-z0-9]+)( +\"(?<params>.+)\")?$", Pattern.CASE_INSENSITIVE);

    public enum ClientStatusUpdateMode {
        Connected,
        Disconnected
    }
    public interface ProtocolListener {
        void onInit();
        void onClientStatusUpdate(final Options options, final ClientStatusUpdateMode mode);
        void onGetData(final String requestId, final String params, final TCPServer.Options options);
        void onPostData(final String requestId, final String params, final List<String> payloads, final TCPServer.Options options);
    }

    public ProtocolModel() {
        server = new TCPServer();
        clients = new HashMap<>();
    }

    public void init(final ProtocolListener listener) {
        server.initiate(new TCPServer.ServerEventsListener() {
            @Override
            public void onServerStart() {
                clients.clear();

                listener.onInit();
            }

            @Override
            public void onClientConnect(TCPServer.Options options) {
                server.getMessageQueues().get(messageQueues -> clients.put(options.getClient(), new SocketWrapper(UUID.randomUUID().toString(), "Anonymous", new Options() {

                    @Override
                    public List<SocketWrapper> getClientSocketWrappers() {
                        return clients.values().stream().toList();
                    }

                    @Override
                    public SocketWrapper getClientSocketWrapper() {
                        return clients.get(options.getClient());
                    }

                    @Override
                    public Socket getClient() {
                        return options.getClient();
                    }

                    @Override
                    public PrintWriter getSocketWriteStream() {
                        return options.getSocketWriteStream();
                    }

                    @Override
                    public String awaitClientMessageLine() throws IOException {
                        return options.awaitClientMessageLine();
                    }

                    @Override
                    public void acknowledgeWithError(String error) {
                        options.acknowledgeWithError(error);
                    }

                    @Override
                    public BufferedReader getSocketReadStream() {
                        return options.getSocketReadStream();
                    }

                    @Override
                    public void acknowledgeClient() {
                        options.acknowledgeClient();
                    }
                }, messageQueues.get(options.getClient()))));

                listener.onClientStatusUpdate((Options) clients.get(options.getClient()).getOptions(), ClientStatusUpdateMode.Connected);
            }

            @Override
            public void onClientMessage(String head, TCPServer.Options options) {
                final Matcher matcher = requestHeadPattern.matcher(head);
                if (!matcher.matches()) return;

                final String method = matcher.group("method").toLowerCase();
                final String requestId = matcher.group("requestId");
                final String params = matcher.group("params");

                options.acknowledgeClient();

                if (method.equals("get"))
                    listener.onGetData(requestId, params, clients.get(options.getClient()).getOptions());
                else if (method.equals("post")) {
                    final List<String> payloads = new ArrayList<>();

                    try {
                        String line;
                        while ((line = options.awaitClientMessageLine()) != null && !line.equalsIgnoreCase("DONE"))
                            payloads.add(line);

                        listener.onPostData(requestId, params, payloads, clients.get(options.getClient()).getOptions());

                        options.getSocketWriteStream().println("OK");
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            @Override
            public void onClientDisconnect(TCPServer.Options options) {
                listener.onClientStatusUpdate((Options) clients.remove(options.getClient()).getOptions(), ClientStatusUpdateMode.Disconnected);
            }

            @Override
            public void onServerError(Exception e) {
                e.printStackTrace();
            }

            @Override
            public void onServerEnd() {
                clients.clear();
            }
        });
    }
}
