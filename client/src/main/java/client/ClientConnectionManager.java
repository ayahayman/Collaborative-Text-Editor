package client;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ClientConnectionManager {
    private static Socket socket;
    private static DataOutputStream out;
    private static DataInputStream in;
    private static String siteId;
    private static String sessionId;
    private static int userId = -1;
    private static String currentDocument;
    private static String role;

    // For reconnection
    private static AtomicBoolean reconnecting = new AtomicBoolean(false);
    private static final int MAX_RECONNECT_ATTEMPTS = 10;
    private static final int RECONNECT_DELAY_MS = 2000;
    private static List<ReconnectionListener> reconnectionListeners = new ArrayList<>();

    public interface ReconnectionListener {
        void onReconnecting();

        void onReconnected();

        void onReconnectFailed();

        void onDisconnected();
    }

    public static void addReconnectionListener(ReconnectionListener listener) {
        reconnectionListeners.add(listener);
    }

    public static void removeReconnectionListener(ReconnectionListener listener) {
        reconnectionListeners.remove(listener);
    }

    public static void connect(String host, int port) throws IOException {
        if (socket == null || socket.isClosed()) {
            socket = new Socket(host, port);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());

            // Start connection monitor
            startConnectionMonitor(host, port);
        }
    }

    public static DataOutputStream getOut() {
        return out;
    }

    public static DataInputStream getIn() {
        return in;
    }

    public static void close() throws IOException {
        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
            out = null;
            in = null;
        }
    }

    // For CRDT usage
    public static void setSiteId(String id) {
        siteId = id;
    }

    public static String getSiteId() {
        return siteId;
    }

    // Session management for reconnection
    public static void setSessionId(String id) {
        sessionId = id;
    }

    public static String getSessionId() {
        return sessionId;
    }

    public static void setUserId(int id) {
        userId = id;
    }

    public static int getUserId() {
        return userId;
    }

    public static void setCurrentDocument(String doc) {
        currentDocument = doc;
    }

    public static String getCurrentDocument() {
        return currentDocument;
    }

    public static void setRole(String r) {
        role = r;
    }

    public static String getRole() {
        return role;
    }

    private static void startConnectionMonitor(String host, int port) {
        Thread monitor = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000); // Check connection every second

                    // Simple ping to check if connection is alive
                    if (socket != null && !socket.isClosed()) {
                        try {
                            socket.getOutputStream().write(0); // Send a harmless byte
                        } catch (SocketException se) {
                            // Connection lost, try to reconnect
                            if (sessionId != null && !reconnecting.get()) {
                                handleReconnection(host, port);
                            }
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    // Any other error, try reconnecting if we have a session
                    if (sessionId != null && !reconnecting.get()) {
                        handleReconnection(host, port);
                    }
                }
            }
        });

        monitor.setDaemon(true);
        monitor.start();
    }

    private static void handleReconnection(String host, int port) {
        if (reconnecting.compareAndSet(false, true)) {
            // Notify UI that we're trying to reconnect
            for (ReconnectionListener listener : reconnectionListeners) {
                listener.onReconnecting();
            }

            new Thread(() -> {
                try {
                    boolean reconnected = false;

                    for (int attempt = 0; attempt < MAX_RECONNECT_ATTEMPTS; attempt++) {
                        try {
                            // Close existing socket if necessary
                            if (socket != null && !socket.isClosed()) {
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    // Ignore
                                }
                            }

                            // Create new connection
                            socket = new Socket(host, port);
                            out = new DataOutputStream(socket.getOutputStream());
                            in = new DataInputStream(socket.getInputStream());

                            // Send reconnection request
                            out.writeUTF("reconnect");
                            out.writeUTF(sessionId);

                            String response = in.readUTF();
                            if ("RECONNECT_SUCCESS".equals(response)) {
                                // Get session state
                                userId = in.readInt();
                                currentDocument = in.readUTF();
                                role = in.readUTF();

                                // Process any pending messages that arrived while disconnected
                                int pendingCount = in.readInt();
                                for (int i = 0; i < pendingCount; i++) {
                                    // Just read the message type, actual handling in editor
                                    String msgType = in.readUTF();
                                }

                                reconnected = true;
                                break; // Successfully reconnected
                            }

                        } catch (Exception e) {
                            System.out.println("Reconnection attempt " + (attempt + 1) + " failed: " + e.getMessage());
                        }

                        // Wait before next attempt
                        try {
                            Thread.sleep(RECONNECT_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }

                    if (reconnected) {
                        for (ReconnectionListener listener : reconnectionListeners) {
                            listener.onReconnected();
                        }
                    } else {
                        for (ReconnectionListener listener : reconnectionListeners) {
                            listener.onReconnectFailed();
                        }
                    }

                } finally {
                    reconnecting.set(false);
                }
            }).start();
        }
    }

    public static boolean isReconnecting() {
        return reconnecting.get();
    }
}
