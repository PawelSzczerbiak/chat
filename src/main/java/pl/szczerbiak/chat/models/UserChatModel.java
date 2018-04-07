package pl.szczerbiak.chat.models;

import lombok.Data;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

@Data
public class UserChatModel {
    private WebSocketSession session;
    private String nickname;
    private Long time; // message time
    private int counter; // number of messages
    private boolean isBanned;
    private boolean isAdmin;

    public UserChatModel(WebSocketSession session){
        this.session = session;
    }

    public void sendMessage(String message) throws IOException {
        session.sendMessage(new TextMessage(message));
    }
}