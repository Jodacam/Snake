package es.codeurjc.em.snake;

import com.google.gson.Gson;
import es.codeurjc.em.snake.GameType.Type;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;
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

    private AtomicInteger snakeIds = new AtomicInteger(0);
    private AtomicInteger gameIds = new AtomicInteger(0);

    private Map<Integer, SnakeGame> games = new ConcurrentHashMap<>();
    private Map<String, String> users = new ConcurrentHashMap<>();
    public static Map<Type, ConcurrentHashMap<String, Long>> Puntuaciones = new ConcurrentHashMap<>();

    private SnakeGame snakeGame = new SnakeGame(new GameType("global", GameType.Dificultad.Dificil, GameType.Type.Lobby, -1), 0);

    private static final String SNAKE_ATT = "snake";

    public static final Gson JSON = new Gson();

    private ReentrantReadWriteLock InOut = new ReentrantReadWriteLock();

    public SnakeHandler() {

        Puntuaciones.put(Type.Arcade, new ConcurrentHashMap<>());
        Puntuaciones.put(Type.Classic, new ConcurrentHashMap<>());
        games.put(gameIds.getAndIncrement(), snakeGame);

        LoadFilesPunc("Classic", Puntuaciones.get(Type.Classic));
        LoadFilesPunc("Arcade", Puntuaciones.get(Type.Arcade));
        LoadFilesUsers("Users");
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        int id = snakeIds.getAndIncrement();

        Snake s = new Snake(id, session);

        session.getAttributes().put(SNAKE_ATT, s);

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        try {

            String payload = message.getPayload();

            char p = payload.charAt(0);

            Message m = JSON.fromJson(payload, Message.class);

            Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
            
            

            switch (m.getMessageType()) {
                case "ping":
                    break;
                case "connect":

                    session.getAttributes().put("Thread", Thread.currentThread());
                    s.setName(m.getName());
                    
                    SnakeGame game;
                    
                    InOut.readLock().lock();
                    game = games.get(m.getId());
                    if (game != null) {
                        try {
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

                        } catch (InterruptedException e) {
                            InOut.readLock().unlock();
                            String failed = String.format("{\"type\": \"failed-join\",\"data\":\"You couldn't connect to game %d\"}", m.getId());
                            session.sendMessage(new TextMessage(failed));
                            break;
                        }
                    } else {
                        InOut.readLock().unlock();
                        String failed = String.format("{\"type\": \"failed-join\",\"data\":\"You couldn't connect to game %d\"}", m.getId());
                        session.sendMessage(new TextMessage(failed));
                    }

                    break;
                case "chat":

                    SnakeGame game2;

                    if (m.getId() > 0) {
                        game2 = games.get(m.getId());
                    } else {
                        game2 = snakeGame;
                    }
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("{\"name\": \"%s\", \"message\": \"%s\"}", s.getName(), m.getDirection()));
                    String msg = String.format("{\"type\": \"chat\",\"data\":%s}", sb.toString());

                    game2.broadcast(msg);

                    break;

                case "Disconnect":

                    Thread t = (Thread) session.getAttributes().get("Thread");
                    t.interrupt();

                    break;
                case "Start":

                    SnakeGame game3;
                    game3 = games.get(m.getId());
                    if (game3.getNumSnakes() > 1) {
                        game3.startTimer();
                    }

                    break;

                default:
                    Direction d = Direction.valueOf(m.getDirection().toUpperCase());
                    s.setDirection(d);
                    break;
            }

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

        if (session.getAttributes().get("gameId") != null) {
            int id = (int) session.getAttributes().get("gameId");

            SnakeGame game = games.get(id);
            
            if (game != null) {
                game.removeSnake(s);

                InOut.writeLock().lock();
                try {

                    synchronized (game) {
                        if (!game.ganada.get() && (game.getNumSnakes() > 0 || game.getName().equals("global"))) {

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
        }
        //snakeIds.decrementAndGet();
    }

    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public int PostGame(@RequestBody String tstring
    ) {

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
    public int PostName(@RequestBody String user
    ) {

        user = user.replace("=", "");

        String name = user.split("%3A")[0];
        String password = user.split("%3A")[1];

        String oldUser = users.putIfAbsent(user.split("%3A")[0], user.split("%3A")[1]);

        if (oldUser != null) {
            return -1;
        } else {
            File f = null;
            FileWriter fw = null;
            PrintWriter pw = null;
            try {
                f = new File("data/Users.json");
                fw = new FileWriter(f);
                pw = new PrintWriter(fw);
                List<String> list = new LinkedList<>();

                for (String n : users.keySet()) {
                    list.add(n + ":" + users.get(n));
                }

                pw.print(SnakeHandler.JSON.toJson(list));

            } catch (FileNotFoundException ex) {
                System.err.println("Archivo no encontrado");
            } catch (IOException ex) {
                Logger.getLogger(SnakeHandler.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                pw.close();
            }
            return 1;
        }
    }

    @GetMapping("/names/{user}")
    public int GetLog(@PathVariable String user
    ) {

        user.replace("=", "");

        String name = user.split(":")[0];
        String password = user.split(":")[1];

        if (users.containsKey(name)) {
            if (user.split(":")[1].equals(users.get(name))) {
                return 1;
            } else {
                return 2;
            }
        } else {
            return -1;
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
    public int GetRandomGame() {
        int number = -1;
        Collection<SnakeGame> ActualGames = games.values();
        List<SnakeGame> list = new ArrayList<SnakeGame>(ActualGames);
        list.remove(0);
        list.sort((p1, p2) -> p1.getNumSnakes() - p2.getNumSnakes());
        if (list.get(0).getNumSnakes() != list.get(0).getJugadoresMinimos()) {
            number = list.get(0).getId();
        } else {
            number -= list.get(0).getId();
        }
        return number;
    }

    @GetMapping("/Puntuaciones/{tipo}")
    public List<String> GetPuntuaciones(@PathVariable Type tipo
    ) {
        List<String> list = new LinkedList<>();

        for (String name : Puntuaciones.get(tipo).keySet()) {
            list.add(name + ":" + Puntuaciones.get(tipo).get(name));
        }

        switch (tipo) {
            case Arcade:
                list.sort((p1, p2) -> Integer.parseInt(p1.split(":")[1]) - Integer.parseInt(p2.split(":")[1]));
                break;
            case Classic:
                list.sort((p1, p2) -> Integer.parseInt(p2.split(":")[1]) - Integer.parseInt(p1.split(":")[1]));
                break;
        }
        return list;
    }
    
    private void LoadFilesPunc(String fileType,ConcurrentHashMap map){
        File f = null;
        FileReader fr = null;
        BufferedReader br = null;
        List<String> list = null;

        try {
            f = new File("data/"+fileType+".json");
            fr = new FileReader(f);
            br = new BufferedReader(fr);
            list = JSON.fromJson(br, List.class);

            if (list != null) {
                for (String punt : list) {
                    map.put(punt.split(":")[0], Long.parseLong(punt.split(":")[1]));
                }
            }

            br.close();
        } catch (FileNotFoundException ex) {
            Logger.getLogger(SnakeHandler.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SnakeHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private void LoadFilesUsers(String fileType){
        File f = null;
        FileReader fr = null;
        BufferedReader br = null;
        List<String> list = null;

        try {
            f = new File("data/Users.json");
            fr = new FileReader(f);
            br = new BufferedReader(fr);
            list = JSON.fromJson(br, List.class);
            if (list != null) {
                for (String n : list) {
                    users.put(n.split(":")[0], n.split(":")[1]);
                }
            }

            br.close();

        } catch (FileNotFoundException ex) {
            Logger.getLogger(SnakeHandler.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(SnakeHandler.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
