package pl.szczerbiak.chat.models;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

// TextWebSocketHandler - nasz socket będzie wymieniał tylko tekst
@EnableWebSocket
@Component
public class ChatSocket extends TextWebSocketHandler implements WebSocketConfigurer {

    // TODO: refactoring

    // Lista użytkowników
    List<UserChatModel> userList = new ArrayList<>();
    List<String> messageHistoryList = new ArrayList<>();
    private static String ADMIN = "admin";
    private static String PASSWORD = "admin";

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry webSocketHandlerRegistry) {
        webSocketHandlerRegistry
                .addHandler(this, "/chat")
                .setAllowedOrigins("*"); // jaki adres IP może się łączyć (* = dowolny host)
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
//        System.out.println("Somebody joined");
        userList.add(new UserChatModel(session));

        UserChatModel userChatModel = findUserBySessionId(session);
        printMessageHistory(userChatModel);
        userChatModel.sendMessage("Welcome to our chat!");
        userChatModel.sendMessage("Enter your nickname:");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
//        System.out.println("Somebody left");
        userList.remove(findUserBySessionId(session));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
//        System.out.println("Message: " + message.getPayload());
        UserChatModel sender = findUserBySessionId(session);
        String input = message.getPayload();

        if (input == null || input.isEmpty()) {
            return;
        }

        // Nickname setting
        if (sender.getNickname() == null) {
            welcomeDialog(sender, input, message);
            return;
        }

        // Banning setting
        if (!sender.isAdmin() && checkBanned(sender)) {
            return;
        }

        // Admin settings
        if (sender.isAdmin()) {
            if (input.startsWith("/")) {
                String[] res = input.split("\\s+");
                if (res.length >= 3 && res[0].equals("/changenick")) {
                    String oldNick = res[1];
                    String newNick = res[2];
                    findUserByNickname(oldNick).ifPresent(user -> changeNick(user, newNick));
                } else if (res.length >= 2) {
                    String nick = res[1];
                    if (res[0].equals("/kick")) {
                        findUserByNickname(nick).ifPresent(user -> kick(user));
                    } else if (res[0].equals("/ban")) {
                        findUserByNickname(nick).ifPresent(user -> ban(user));
                    } else if (res[0].equals("/unban")) {
                        findUserByNickname(nick).ifPresent(user -> unban(user));
                    }
                }
                return;
            }
        }

        String output = sender.getNickname() + ": " + input;

        addMessagetoHistory(output); // add message to history

        sendMessageToAll(output); // show message

    }

// =============================================================
// Helper methods
// =============================================================

    protected void sendMessageToAll(String message) throws Exception {
        for (UserChatModel userModel : userList) {
            userModel.sendMessage(message);
        }
    }

    private UserChatModel findUserBySessionId(WebSocketSession session) {
        return userList.stream()
                .filter(s -> s.getSession().getId().equals(session.getId()))
                .findAny().orElseThrow(NoSuchElementException::new);
    }

    private Optional<UserChatModel> findUserByNickname(String nickname) {
        return userList.stream()
                .filter(s -> s.getNickname().equals(nickname))
                .findAny();
    }

    private List<String> getNicknames() {
        return userList.stream()
                .map(s -> s.getNickname())
                .collect(Collectors.toList());
    }

    private void addMessagetoHistory(String message) {
        if (messageHistoryList.size() >= 10) { // last 10 messages
            messageHistoryList.remove(0);
        }
        messageHistoryList.add(message);
    }

    private void printMessageHistory(UserChatModel userChatModel) {
        for (String message : messageHistoryList) {
            try {
                userChatModel.sendMessage(message);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

// =============================================================
// User handling
// =============================================================

    private void welcomeDialog(UserChatModel sender, String input, TextMessage message) throws IOException {
        // User gave correct admin name
        if (sender.isAdmin() == true) {
            if (input.equals(PASSWORD)) {
                sender.setNickname(message.getPayload());
                sender.sendMessage("You're now logged-in as admin!");
            } else {
                sender.setAdmin(false);
                sender.sendMessage("Wrong password! Enter nickname:");
            }
            return;
        }

        if (input.equals(ADMIN)) {
            sender.sendMessage("Enter password:");
            sender.setAdmin(true);
        } else if (getNicknames().contains(input)) {
            sender.sendMessage("Nickname already in use!");
        } else {
            sender.setNickname(message.getPayload());
            sender.sendMessage("Nickname set to: " + message.getPayload());
        }
        return;

    }

    private boolean checkBanned(UserChatModel sender) throws IOException {

        if(sender.isBannedByAdmin()) {
            return true;
        }

        // If banned: wait 5 sec
        if (sender.isBanned()) {
            if (System.currentTimeMillis() - sender.getTime() < 5000L) {
                return true;
            } else {
                sender.setBanned(false);
            }
        }

        // If not banned: type up to 3 messages in 3 sec
        if (sender.getCounter() == 0) {
            sender.setTime(System.currentTimeMillis());
            sender.setCounter(sender.getCounter() + 1);
        } else if (System.currentTimeMillis() - sender.getTime() < 3000L) {
            if (sender.getCounter() < 3) {
                sender.setCounter(sender.getCounter() + 1);
            } else {
                sender.sendMessage("You're banned! Wait 5 sec...");
                sender.setBanned(true);
                sender.setCounter(0);
                sender.setTime(System.currentTimeMillis());
                return true;
            }
        }
        // Reset params
        else if (System.currentTimeMillis() - sender.getTime() > 3000L) {
            sender.setCounter(0);
            sender.setTime(System.currentTimeMillis());
        }

        return false;
    }

// =============================================================
// Admin panel
// =============================================================

    private void kick(UserChatModel user) {
        userList.remove(findUserBySessionId(user.getSession()));
        System.out.println(user.getNickname() + " removed");
    }

    private void ban(UserChatModel user) {
        if (!user.isBannedByAdmin()) {
            user.setBannedByAdmin(true);
            System.out.println(user.getNickname() + " banned");
        } else {
            System.out.println(user.getNickname() + " is already banned!");
        }
    }

    private void unban(UserChatModel user) {
        if (user.isBannedByAdmin()) {
            user.setBannedByAdmin(false);
            System.out.println(user.getNickname() + " is unbanned");
        } else {
            System.out.println(user.getNickname() + " is already unbanned!");
        }
    }

    private void changeNick(UserChatModel user, String nick) {
        String oldNick = user.getNickname();
        user.setNickname(nick);
        System.out.println(oldNick + " is now " + user.getNickname());
    }
}