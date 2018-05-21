package es.codeurjc.em.snake;

import com.google.gson.Gson;
import java.util.ArrayList;
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

    //private SnakeGame snakeGame = new SnakeGame();
    
    private AtomicInteger gameIds = new AtomicInteger(0);
    
    private Map<Integer,SnakeGame> games = new ConcurrentHashMap<>();

    private static final Gson JSON = new Gson();
    
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

            Message m = JSON.fromJson(payload,Message.class);
            
            if (m.getMessageType().equals("ping")) {
                return;
            }
            
            Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
            
            if(m.getMessageType().equals("connect")){
                games.get(m.getId()).addSnake(s);
                return;
            }

            Direction d = Direction.valueOf(payload.toUpperCase());
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

        //snakeGame.removeSnake(s);

        String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());

        //snakeGame.broadcast(msg);
    }
    
    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public int PostGame(@RequestBody String name){
        int game = gameIds.getAndIncrement();
        
        games.put(game, new SnakeGame(name));
        
        return game;
    }
    
    @GetMapping("/")
    public List<String> GetGames(){
        List<String> gamesInfo = new ArrayList<>();
        
        for(SnakeGame game:games.values()){
            String gameInfo = game.getName() +","+ game.getNumSnakes();
            gamesInfo.add(gameInfo);
        }
        
        return gamesInfo;
    }
}
