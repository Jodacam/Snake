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

    
    
    //ID de los Jugadores
    private AtomicInteger snakeIds = new AtomicInteger(0);
    
    //Id de las partidas
    private AtomicInteger gameIds = new AtomicInteger(0);

    
    //Mapa de partidas el Id es un numero sacado de GameIds
    private Map<Integer, SnakeGame> games = new ConcurrentHashMap<>();
    
    //Mapa de Usuarios Registrados. Si un usuario intenta registrarse con el nombre de otro jugador estará aqui guardado y no lo hará
    private Map<String, String> users = new ConcurrentHashMap<>();
    
    //Puntuaciones, Es un doble mapa para poder Guardar por Tipos y despues dentro estan otro Mapa de puntuaciones, donde la clave es el nombre del Usuario
    public static Map<Type, ConcurrentHashMap<String, Long>> Puntuaciones = new ConcurrentHashMap<>();

    
    //Lobby. Es un Sanake para aprovechar sus funciones de añadir, eliminar y BroadCast
    private SnakeGame snakeGame = new SnakeGame(new GameType("global", GameType.Dificultad.Dificil, GameType.Type.Lobby, -1), 0);

    private static final String SNAKE_ATT = "snake";

    
    //Objeto de GSON para los JSON
    public static final Gson JSON = new Gson();

    private ReentrantReadWriteLock InOut = new ReentrantReadWriteLock();

    public SnakeHandler() {

        
        //Inicializamos las puntuaciones
        Puntuaciones.put(Type.Arcade, new ConcurrentHashMap<>());
        Puntuaciones.put(Type.Classic, new ConcurrentHashMap<>());
        games.put(gameIds.getAndIncrement(), snakeGame);

        LoadFilesPunc("Classic", Puntuaciones.get(Type.Classic));
        LoadFilesPunc("Arcade", Puntuaciones.get(Type.Arcade));
        
        // Leemos los archivos Arcade.json y Classic.json de la carpeta Data. Ahi estan las Puntuaciones. Al estar en el constructor aun no hay nada en el servidor asi que funcionara
        LoadFilesUsers("Users");
        
        //Leemos el archivo de los Usuarios Registrados
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {

        //Al Conectarse metemos un serpiente en la sesion
        int id = snakeIds.getAndIncrement();

        Snake s = new Snake(id, session);

        session.getAttributes().put(SNAKE_ATT, s);

    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        //Swich de los diferentes mensajes
        try {

            String payload = message.getPayload();

            char p = payload.charAt(0);

            Message m = JSON.fromJson(payload, Message.class);

            Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);
            
            

            switch (m.getMessageType()) {
                case "ping":
                    break;
                case "connect":
                    //En vez de conectar por ConectionEstablish, Nos conectamos con un mensaje
                    //Metemos el Hilo de esta funcion en la sesion paras poder interumpirlo
                    session.getAttributes().put("Thread", Thread.currentThread());
                    s.setName(m.getName());
                    
                    SnakeGame game;
                    //Lock de Lectores/Escritores, no se puede meter gente en una partida mientras se elimina una partida, asi no se puede unir nadie a una partida que acaba de terminar
                    InOut.readLock().lock();
                    game = games.get(m.getId());
                    if (game != null && !game.ganada.get()) {
                        try {
                            //Añadimos la serpiente
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
                            //Mandamos el mensaje de Join
                            game.broadcast(msg);
                            return;

                        } catch (InterruptedException e) {
                            InOut.readLock().unlock();
                            //Si el Usuario no se ha podido conectar se le mandara un Failed Join que le echara del juego. 
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
                    //Recoge el mensaje para el chat y lo envia a los demas jugadores de la partida
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
                    //Si un jugador esta en Espera y le da a cancelar interumpe al hilo para que salga de la espera
                    Thread t = (Thread) session.getAttributes().get("Thread");
                    t.interrupt();

                    break;
                case "Start":
                    //Empieza una partida
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
        // Cuando se desconecta un jugadores le quitamos de la partida. Si la partida tiene 0 jugadores se cierra automaticamente
        System.out.println("Connection closed. Session " + session.getId());

        Snake s = (Snake) session.getAttributes().get(SNAKE_ATT);

        
        if (session.getAttributes().get("gameId") != null) {
            //Obtenemos el Id de la partdia
            int id = (int) session.getAttributes().get("gameId");

            SnakeGame game = games.get(id);
            
            if (game != null) {
                //Eliminamos la serpiente
                game.removeSnake(s);

                InOut.writeLock().lock();
                try {
                    //Borramos la partida si ya se ha ganado o si no hay serpientes
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
    
    
    
    //Api Rest. Aqui se crea una partida
    @PostMapping("/")
    @ResponseStatus(HttpStatus.CREATED)
    public int PostGame(@RequestBody String tstring) 
    {
        //Lee el tipo de partida
        GameType t = JSON.fromJson(tstring, GameType.class
        );
        boolean exist = false;

        t.setName(t.getName().replace("=", ""));

        //Comprobara que la partida existe con ese nombre.
        synchronized (this) {
            for (SnakeGame game : games.values()) {
                exist = game.getName().equals(t.getName());
                if (exist) {
                    break;
                }
            }
            //Si no existe la crea
            if (!exist) {

                int game = gameIds.getAndIncrement();

                games.put(game, new SnakeGame(t, game));

                return game;
            } else {
                return -1;
            }
        }
    }

    
    
    Object cierre = new Object();
    
    // Sirve para Registrar un usuario
    @PostMapping("/names")
    @ResponseStatus(HttpStatus.CREATED)
  public int  PostName(@RequestBody String user
    ) {

        user = user.replace("=", "");

        String name = user.split("%3A")[0];
        String password = user.split("%3A")[1];

        //Comprobamos que no hubiera un usuario con el mismo nombre
        String oldUser = users.putIfAbsent(user.split("%3A")[0], user.split("%3A")[1]);

        if (oldUser != null) {
            return -1;
        } else {
            synchronized(cierre){
                
            //Escribimos el usuario en el fichero
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
            }
            return 1;
        }
    }

  
    //Comprueba que el usuario y contraseña son correctos y te deja conectarte
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
    //Obtiene las partidas para mostrarlas en Java
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

    
    //Te conecta a una partida. Busca la que menos jugadores tenga y haya un hueco vacio
    @GetMapping("/Random")
    public int GetRandomGame() {
        int number = -1;
        if(games.values().size() >= 2){
        Collection<SnakeGame> ActualGames = games.values();
        List<SnakeGame> list = new ArrayList<SnakeGame>(ActualGames);
        list.remove(0);
        list.sort((p1, p2) -> p1.getNumSnakes() - p2.getNumSnakes());
        if (list.get(0).getNumSnakes() != list.get(0).getJugadoresMinimos()) {
            number = list.get(0).getId();
        } else {
            number -= list.get(0).getId();
        }
        }
        return number;
    }

    
    //Pides los Hall of Fame del diferente modo de juego
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
