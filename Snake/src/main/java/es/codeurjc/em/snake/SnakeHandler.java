package es.codeurjc.em.snake;

import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@RestController
@RequestMapping("/games")
public class SnakeHandler extends TextWebSocketHandler {

    private static final String SNAKE_ATT = "snake";

    private AtomicInteger snakeIds = new AtomicInteger(0);

    private SnakeGame snakeGame = new SnakeGame("global",0);
    
    private AtomicInteger gameIds = new AtomicInteger(0);

    private Map<Integer, SnakeGame> games = new ConcurrentHashMap<>();

    private static final Gson JSON = new Gson();

    public SnakeHandler() {
        games.put(gameIds.getAndIncrement(), snakeGame);
    }
    
    

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        int id = snakeIds.getAndIncrement();

        Snake s = new Snake(id, session);

        session.getAttributes().put(SNAKE_ATT, s);

        /*snakeGame.addSnake(s);

        StringBuilder sb = new StringBuilder();
        for (Snake snake : snakeGame.getSnakes()) {
            sb.append(String.format("{\"id\": %d, \"color\": \"%s\"}", snake.getId(), snake.getHexColor()));
            sb.append(',');
        }
        sb.deleteCharAt(sb.length() - 1);
        String msg = String.format("{\"type\": \"join\",\"data\":[%s]}", sb.toString());

        snakeGame.broadcast(msg);*/
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        try {

            String payload = message.getPayload();

            char p = payload.charAt(0);

            Message m = JSON.fromJson(payload, Message.class);

            if (m.getMessageType().equals("ping")) {
                return;
            }

            Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);

            if (m.getMessageType().equals("connect")) {

                session.getAttributes().put("gameId", m.getId());

                s.setName(m.getName());
                SnakeGame game = games.get(m.getId());
                game.addSnake(s);

                StringBuilder sb = new StringBuilder();
                for (Snake snake : game.getSnakes()) {
                    sb.append(String.format("{\"id\": %d, \"color\": \"%s\"}", snake.getId(), snake.getHexColor()));
                    sb.append(',');
                }
                sb.deleteCharAt(sb.length() - 1);
                String msg = String.format("{\"type\": \"join\",\"data\":[%s]}", sb.toString());

                game.broadcast(msg);
                return;
            }

            if (m.getMessageType().equals("chat")) {
                SnakeGame game;
                if (m.getId() > 0){
                 game = games.get(m.getId());
                }else{
                game = snakeGame;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("{\"name\": \"%s\", \"message\": \"%s\"}", s.getName(), m.getDirection()));
                String msg = String.format("{\"type\": \"chat\",\"data\":%s}", sb.toString());

                game.broadcast(msg);
                return;
            }

            Direction d = Direction.valueOf(m.getDirection().toUpperCase());
            s.setDirection(d);

        } catch (Exception e) {
            System.err.println("Exception processing message " + message.getPayload());
            e.printStackTrace(System.err);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

        System.out.println("Connection closed. Session " + session.getId());

        Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);

        if (session.getAttributes().get("gameId") != null) {
            int id = (int) session.getAttributes().get("gameId");

            SnakeGame game = games.get(id);

            String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());

            game.broadcast(msg);
        }
        
        snakeIds.decrementAndGet();
    }

    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public int PostGame(@RequestBody String name) {

        boolean exist = false;

        name = name.replace("=", "");

        synchronized (this) {
            for (SnakeGame game : games.values()) {
                exist = game.getName().equals(name);
                if (exist) {
                    break;
                }
            }

            if (!exist) {

                int game = gameIds.getAndIncrement();

                games.put(game, new SnakeGame(name, game));
                return game;
            } else {
                return -1;
            }
        }
    }

    @GetMapping("/")
    public List<String> GetGames() {
        List<String> gamesInfo = new ArrayList<>();

        for (SnakeGame game : games.values()) {
            if(!game.getName().equals("global")){
                String gameInfo = game.getName() + "," + game.getNumSnakes() + "," + game.getId();
                gamesInfo.add(gameInfo);
            }
        }

        return gamesInfo;
    }
}
