// client/src/network/AuthService.java
package network;

import java.io.IOException;

public class AuthService {
    private final ClientSocketManager socketManager;

    public AuthService(ClientSocketManager socketManager) {
        this.socketManager = socketManager;
    }

    public boolean login(String username, String password) throws IOException {
        String response = socketManager.sendAuthRequest(
            "LOGIN:" + username + ":" + password
        );
        return "SUCCESS".equals(response);
    }

    public boolean register(String username, String password) throws IOException {
        String response = socketManager.sendAuthRequest(
            "REGISTER:" + username + ":" + password
        );
        return "SUCCESS".equals(response);
    }
}