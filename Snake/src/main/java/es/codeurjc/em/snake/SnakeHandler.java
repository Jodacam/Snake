package es.codeurjc.em.snake;

import com.google.gson.Gson;
import es.codeurjc.em.snake.GameType.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    private SnakeGame snakeGame = new SnakeGame(new GameType("global", GameType.Dificultad.Dificil, GameType.Type.Lobby, -1), 0);

    private AtomicInteger gameIds = new AtomicInteger(0);

    private Map<Integer, SnakeGame> games = new ConcurrentHashMap<>();

    private Set<String> names = new CopyOnWriteArraySet<>();

    private static final Gson JSON = new Gson();

    private ReentrantReadWriteLock InOut = new ReentrantReadWriteLock();
    
    public static Map<Type,ConcurrentHashMap<String,Long>> Puntuaciones = new ConcurrentHashMap<>();
    
    

    public SnakeHandler() {
        Puntuaciones.put(Type.Arcade, new ConcurrentHashMap<>());
        Puntuaciones.put(Type.Classic, new ConcurrentHashMap<>());
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
               
                session.getAttributes().put("Thread", Thread.currentThread());
                
                s.setName(m.getName());
                InOut.readLock().lock();
                SnakeGame game = games.get(m.getId());
                if (game != null) {
                   try{ 
                    game.addSnake(s);
                    InOut.readLock().unlock();
                    session.getAttributes().put("gameId", m.getId());
                    StringBuilder sb = new StringBuilder();
                    for (Snake snake : game.getSnakes()) {
                        sb.append(String.format("{\"id\": %d, \"color\": \"%s\", \"name\":\"%s\"}", snake.getId(), snake.getHexColor(), snake.getName()));
                        sb.append(',');
                    }
                    sb.deleteCharAt(sb.length() - 1);
                    String msg = String.format("{\"type\": \"join\",\"data\":[%s]}", sb.toString());
                    game.broadcast(msg);
                    return;
                    
                    
                   } catch(InterruptedException e){
                    InOut.readLock().unlock();
                    String failed = String.format("{\"type\": \"failed-join\",\"data\":\"You couldn't connect to game %d\"}", m.getId());
                    session.sendMessage(new TextMessage(failed));
                    return;
                   }
                } else {
                    InOut.readLock().unlock();
                    String failed = String.format("{\"type\": \"failed-join\",\"data\":\"You couldn't connect to game %d\"}", m.getId());
                    session.sendMessage(new TextMessage(failed));
                    return;
                }
            }
            if (m.getMessageType().equals("chat")) {
                SnakeGame game;
                if (m.getId() > 0) {
                    game = games.get(m.getId());
                } else {
                    game = snakeGame;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(String.format("{\"name\": \"%s\", \"message\": \"%s\"}", s.getName(), m.getDirection()));
                String msg = String.format("{\"type\": \"chat\",\"data\":%s}", sb.toString());

                game.broadcast(msg);
                return;
            }
            
            if(m.getMessageType().equals("Start")){
                SnakeGame game;
                game = games.get(m.getId());
                if (game.getNumSnakes()>1)
                    game.startTimer();
                return;
            }
            
            if(m.getMessageType().equals("Disconnect")){
                Thread t = (Thread)session.getAttributes().get("Thread");
                t.interrupt();
                return;
            }
            
            Direction d = Direction.valueOf(m.getDirection().toUpperCase());
            s.setDirection(d);

        } catch (Exception e) {
            InOut.readLock().unlock();
            System.err.println("Exception processing message " + message.getPayload());
            e.printStackTrace(System.err);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {

        System.out.println("Connection closed. Session " + session.getId());

        Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);

        names.remove(s.getName());

        if (session.getAttributes().get("gameId") != null) {
            int id = (int) session.getAttributes().get("gameId");
            
                SnakeGame game = games.get(id);
                game.removeSnake(s);
                InOut.writeLock().lock();
            try {
                synchronized (game) {
                    if (game.getNumSnakes() > 0 || game.getName().equals("global")) {

                        String msg = String.format("{\"type\": \"leave\", \"id\": %d}", s.getId());

                        game.broadcast(msg);

                    } else {
                        games.remove(id);
                    }
                }
            } finally {
                InOut.writeLock().unlock();
            }
        }

        //snakeIds.decrementAndGet();
    }

    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public int PostGame(@RequestBody String tstring) {

        GameType t = JSON.fromJson(tstring, GameType.class
        );
        boolean exist = false;

        t.setName(t.getName().replace("=", ""));

        synchronized (this) {
            for (SnakeGame game : games.values()) {
                exist = game.getName().equals(t.getName());
                if (exist) {
                    break;
                }
            }

            if (!exist) {

                int game = gameIds.getAndIncrement();

                games.put(game, new SnakeGame(t, game));

                return game;
            } else {
                return -1;
            }
        }
    }

    @PostMapping("/names")
    @ResponseStatus(HttpStatus.CREATED)
    public int PostName(@RequestBody String name) {

        boolean exist = false;

        name = name.replace("=", "");

        synchronized (this) {
            for (String n : names) {
                exist = n.equals(name);
                if (exist) {
                    break;
                }
            }

            if (!exist) {

                names.add(name);

                return 1;

            } else {
                return -1;
            }
        }
    }

    @GetMapping("/")
    public List<String> GetGames() {
        List<String> gamesInfo = new ArrayList<>();

        for (SnakeGame game : games.values()) {
            if (!game.getName().equals("global")) {
                String gameInfo = game.getName() + "," + game.getNumSnakes() + "," + game.getId() + "," + game.getDificultad() + "," + game.getTipo() + "," + game.getJugadoresMinimos();
                gamesInfo.add(gameInfo);
            }
        }

        return gamesInfo;
    }
    
    @GetMapping("/Random")
    public int GetRandomGame(){
        int number = -1;
        Collection<SnakeGame> ActualGames = games.values();
        List<SnakeGame> list = new ArrayList<SnakeGame>( ActualGames );
        list.remove(0);
        list.sort((p1,  p2) -> p1.getNumSnakes() - p2.getNumSnakes());                     
        if (list.get(0).getNumSnakes() != list.get(0).getJugadoresMinimos()){
            number = list.get(0).getId();
        }else{
            number -=list.get(0).getId();
        }
        return number;
    }
    
     @GetMapping("/Puntuaciones/{tipo}")
     public List<String> GetPuntuaciones(@PathVariable Type tipo){        
         List<String> list = new LinkedList<>();
         
         for (String name : Puntuaciones.get(tipo).keySet()){
             list.add(name+":"+Puntuaciones.get(tipo).get(name));             
         }  
         
         switch(tipo){
             
         }        
         list.sort((p1,p2)-> Integer.parseInt(p1.split(":")[1]) - Integer.parseInt(p2.split(":")[1]));
         return list;
     }
    
}
