package utils;

import models.Synchronized;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class TCPServer {
    public static final int PORT = 5699;

    /*
        Sending data (on demand):

        1: POST <request_name> <parameters?>
        2: OK
        [
            1: <payload>
            1: DONE
        ]
        2: OK

        Retrieving data (on demand):

        1: GET <request_name> <parameters?>
        2: OK
        2: <payload>
        2: DONE

        Notifying with data (on demand, no ACK):

        1: SAY <method_name> "<data>"
    */
    public interface Options {
        Socket getClient();
        PrintWriter getSocketWriteStream();
        String awaitClientMessageLine() throws IOException;
        void acknowledgeWithError(final String error);
        BufferedReader getSocketReadStream();
        void acknowledgeClient();
    }
    public interface ServerEventsListener {
        void onServerStart();
        void onClientConnect(final Options options);
        void onClientMessage(final String head, final Options options);
        void onClientDisconnect(final Options options);
        void onServerError(final Exception e);
        void onServerEnd();
    }

    protected final Synchronized<Map<Socket, Queue<String>>> messageQueues = new Synchronized<>(new HashMap<>());
    public final Synchronized<Boolean> mustStop = new Synchronized<>(false);

    public void initiate(final ServerEventsListener listener) {
        final AtomicReference<ServerSocket> server = new AtomicReference<>();

        new Thread(() -> {
            while (!((Boolean) mustStop.get(mustStop -> mustStop))) {
                try {
                    //noinspection BusyWait
                    Thread.sleep(750);
                } catch (InterruptedException ignored) { }
            }

            try {
                server.get().close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mustStop.set(__ -> false);
            }
        }).start();

        try {
            server.set(new ServerSocket(PORT));
            server.get().setReuseAddress(true);
            listener.onServerStart();

            while (!server.get().isClosed()) {
                final Socket client = server.get().accept();

                messageQueues.get(messageQueues -> messageQueues.put(client, new LinkedList<>()));

                new Thread(() -> {
                    PrintWriter writer = null;
                    BufferedReader reader = null;

                    Options options = null;

                    try {
                        final PrintWriter finalWriter = writer = new PrintWriter(client.getOutputStream(), true);
                        final BufferedReader finalReader = reader = new BufferedReader(new InputStreamReader(client.getInputStream()));

                        final Options finalOptions = options = new Options() {
                            @Override
                            public Socket getClient() {
                                return client;
                            }

                            @Override
                            public PrintWriter getSocketWriteStream() {
                                return finalWriter;
                            }

                            @Override
                            public String awaitClientMessageLine() throws IOException {
                                //noinspection StatementWithEmptyBody
                                while (!finalReader.ready());

                                return finalReader.readLine();
                            }

                            @Override
                            public void acknowledgeWithError(String error) {
                                finalWriter.println("Error: " + error);
                            }

                            @Override
                            public BufferedReader getSocketReadStream() {
                                return finalReader;
                            }

                            @Override
                            public void acknowledgeClient() {
                                getSocketWriteStream().println("OK");
                            }
                        };

                        listener.onClientConnect(finalOptions);

                        while (client.isConnected()) {
                            while (!finalReader.ready()) {
                                messageQueues.get(messageQueues -> {
                                    final Queue<String> messageQueue = messageQueues.get(client);
                                    if (messageQueue.isEmpty()) return null;

                                    final String message = Objects.requireNonNull(messageQueue.poll());
                                    finalWriter.println(message);

                                    return null;
                                });
                            }

                            String line;
                            if ((line = reader.readLine()) != null)
                                listener.onClientMessage(line, finalOptions);
                        }
                    } catch (Exception e) {
                        listener.onServerError(e);
                    } finally {
                        try {
                            if (writer != null) {
                                writer.close();
                            }
                            if (reader != null) {
                                reader.close();
                                client.close();
                            }
                        } catch (Exception e) {
                            listener.onServerError(e);
                        }

                        listener.onClientDisconnect(options);

                        messageQueues.get(messageQueues -> messageQueues.remove(client));
                    }
                }).start();
            }
        } catch (Exception e) {
            listener.onServerError(e);
        } finally {
            try {
                server.get().close();
            } catch (Exception e) {
                listener.onServerError(e);
            }
        }

        listener.onServerEnd();
    }

    public Synchronized<Map<Socket, Queue<String>>> getMessageQueues() {
        return messageQueues;
    }
}
