package es.codeurjc.em.snake;

import es.codeurjc.em.snake.GameType.Type;
import static es.codeurjc.em.snake.SnakeHandler.Puntuaciones;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import sun.font.Font2D;
import java.util.concurrent.atomic.AtomicBoolean;

public class SnakeGame {

    private final static long TICK_DELAY = 100;

    private ConcurrentHashMap<Integer, Snake> snakes = new ConcurrentHashMap<>();
    private AtomicInteger numSnakes = new AtomicInteger();
    private List<Fruit> frutas = new ArrayList<Fruit>();
    private long elapseTime = 0;
    private ScheduledExecutorService scheduler;
    private int jugadoresMinimos = 0;
    private GameType.Dificultad dificultad;
    private Type Tipo;
    private long Tiempo;
    private String name;

    private BlockingQueue<Integer> Huecos;

    private volatile int id;

    private volatile boolean started = false;

    public AtomicBoolean ganada;
    public SnakeGame(GameType g, int id) {
        ganada = new AtomicBoolean(false);
        Tiempo = 0;
        this.name = g.getName();
        this.id = id;
        this.dificultad = g.getDificultad();
        this.Tipo = g.getTipo();
        this.jugadoresMinimos = g.getJudores();
        Huecos = new LinkedBlockingDeque<>();
        for (int i = 0; i < jugadoresMinimos; i++) {
            Huecos.add(i);
        }
    }

    public void addSnake(Snake snake) throws InterruptedException {

        if (Tipo != Type.Lobby) {
            Integer i = Huecos.poll(5, TimeUnit.SECONDS);
            if (i == null) {
                throw new InterruptedException();
            }
        }
        synchronized (snake) {
            snakes.put(snake.getId(), snake);
        }

        int count = numSnakes.getAndIncrement();

        if (count == jugadoresMinimos - 1 && Tipo != Type.Lobby && !started) {
            startTimer();

        }
    }

    public boolean isStarted() {
        return started;
    }

    public Collection<Snake> getSnakes() {
        return snakes.values();
    }

    public void removeSnake(Snake snake) throws InterruptedException {

        synchronized (snake) {
            System.out.println("Serpiente Borrada en partida" + this.getName());
            snakes.remove(Integer.valueOf(snake.getId()));
        }
        
        int count = numSnakes.decrementAndGet();

        if (Tipo != Type.Lobby) {
            Huecos.put(0);
        }

        if (count == 0) {
            stopTimer();

        }
    }

    private void tick() {

        try {

            Tiempo += updateRate;
            elapseTime += System.currentTimeMillis();

            for (Snake snake : getSnakes()) {
                snake.update(getSnakes(), frutas);
            }

            if (elapseTime > 10000) {
                elapseTime = 0;
                if (frutas.size() < 20) {
                    Fruit f = new Fruit(200);
                    frutas.add(f);
                }
            }
            StringBuilder name = new StringBuilder();

            StringBuilder sb = new StringBuilder();
            for (Snake snake : getSnakes()) {
                name.append("{\"nombre\": \"" + snake.getName() + "\",");
                name.append("\"puntos\": " + snake.getPoints() + ",");
                name.append("\"color\": \"" + snake.getHexColor() + "\"},");

                sb.append(getLocationsJson(snake));
                sb.append(',');
            }
            StringBuilder f = new StringBuilder();
            for (Fruit fruit : frutas) {
                f.append(String.format("{\"x\": %d, \"y\": %d}", fruit.getPosition().x, fruit.getPosition().y));
                f.append(",");
            }
            long Time = 0;
            switch (Tipo) {
                case Classic:
                    Time = 60000 - Tiempo;
                    break;
                default:
                    Time = Tiempo;
                    break;
            }
            name.deleteCharAt(name.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
            f.deleteCharAt(f.length() - 1);
            String msg = String.format("{\"type\": \"update\", \"data\" : [%s] , \"fruits\" : [%s], \"People\":[%s],\"Tiempo\":%d }", sb.toString(), f.toString(), name.toString(), Time);

            broadcast(msg);
            StringBuilder s = new StringBuilder();
            boolean ganada = false;
            switch (Tipo) {
                case Arcade:

                    for (Snake snake : getSnakes()) {
                        if (snake.getTail().size() > 10) {
                            s.append("{\"id\": \"" + snake.getName() + "\",  \"win\": true");
                            s.append("},");
                            ganada = true;
                            Long previLong = SnakeHandler.Puntuaciones.get(Tipo).putIfAbsent(snake.getName(), Tiempo);

                            if (null != previLong) {
                                if (previLong > Tiempo) {
                                    SnakeHandler.Puntuaciones.get(Tipo).put(snake.getName(), Tiempo);
                                }
                            }

                        } else {
                            s.append("{\"id\":\"" + snake.getName() + "\",  \"win\": false");
                            s.append("},");
                        }
                    }
                    if (ganada) {
                        this.ganada.set(true);
                        s.deleteCharAt(s.length() - 1);
                        String Win = String.format("{\"type\":\"endGame\", \"data\" : [%s]}", s.toString());
                        synchronized (SnakeHandler.Puntuaciones.get(Tipo)) {
                            try{
                                File puntArcade = new File("src/main/resources/static/Arcade.json");
                                FileWriter fw = new FileWriter(puntArcade);
                                PrintWriter pw = new PrintWriter(fw);
                                List<String> list = new LinkedList<>();
                                
                                for (String n : SnakeHandler.Puntuaciones.get(Tipo).keySet()) {
                                    list.add(n + ":" + Puntuaciones.get(Tipo).get(n));
                                }
                                
                                pw.print(SnakeHandler.JSON.toJson(list));
                                
                                fw.close();
                                pw.close();
                            }catch(FileNotFoundException ex){
                                System.err.println("Archivo no encontrado");
                            }
                        }
                        broadcast(Win);
                        stopTimer(); 
                    }

                    break;
                case Unlimited:

                    break;
                case Classic:
                    if (Tiempo > 60 * 1000) {
                        System.out.println("Terminada");
                        List<Snake> list = new ArrayList<>(getSnakes());
                        list.sort((p1, p2) -> p2.getPoints() - p1.getPoints());
                        for (int i = 0; i < list.size(); i++) {
                            Snake snake = list.get(i);

                            if (i == 0) {
                                s.append("{\"id\": \"" + snake.getName() + "\",  \"win\": true");
                                s.append("},");
                                ganada = true;

                            } else {
                                s.append("{\"id\":\"" + snake.getName() + "\",  \"win\": false");
                                s.append("},");
                            }
                            Long previLong = SnakeHandler.Puntuaciones.get(Tipo).putIfAbsent(snake.getName(), (long) snake.getPoints());

                            if (null != previLong) {
                                if (previLong < (long) snake.getPoints()) {
                                    SnakeHandler.Puntuaciones.get(Tipo).put(snake.getName(), (long) snake.getPoints());
                                }
                            }

                        }
                        if (ganada) {
                            this.ganada.set(true);
                            s.deleteCharAt(s.length() - 1);
                            String Win = String.format("{\"type\":\"endGame\", \"data\" : [%s]}", s.toString());
                            synchronized (SnakeHandler.Puntuaciones.get(Tipo)) {
                                try{
                                File puntArcade = new File("src/main/resources/static/Classic.json");
                                FileWriter fw = new FileWriter(puntArcade);
                                PrintWriter pw = new PrintWriter(fw);
                                List<String> list2 = new LinkedList<>();
                                
                                for (String n : SnakeHandler.Puntuaciones.get(Tipo).keySet()) {
                                    list2.add(n + ":" + Puntuaciones.get(Tipo).get(name));
                                }
                                
                                pw.print(SnakeHandler.JSON.toJson(list));
                                
                                fw.close();
                                pw.close();
                                }catch(FileNotFoundException ex){
                                    System.err.println("Archivo no encontrado");
                                }
                            }
                            broadcast(Win);
                            stopTimer();
                        }
                    }

                    break;
            }

        } catch (Throwable ex) {
            System.err.println("Exception processing tick()");
            ex.printStackTrace(System.err);
        }
    }

    private String getLocationsJson(Snake snake) {

        synchronized (snake) {

            StringBuilder sb = new StringBuilder();
            sb.append(String.format("{\"x\": %d, \"y\": %d}", snake.getHead().x, snake.getHead().y));
            for (Location location : snake.getTail()) {
                sb.append(",");
                sb.append(String.format("{\"x\": %d, \"y\": %d}", location.x, location.y));
            }

            return String.format("{\"id\":%d,\"body\":[%s]}", snake.getId(), sb.toString());
        }
    }

    public void broadcast(String message) throws Exception {

        for (Snake snake : getSnakes()) {
            try {
                //System.out.println("Sending message " + message + " to " + snake.getId());
                snake.sendMessage(message);
            } catch (Throwable ex) {
                System.err.println("Execption sending message to snake " + snake.getId());
                ex.printStackTrace(System.err);
                removeSnake(snake);
            }

        }

    }
    volatile long updateRate = 0;

    public void startTimer() {
        scheduler = Executors.newScheduledThreadPool(1);

        switch (dificultad) {
            case Facil:
                updateRate = TICK_DELAY;
                break;
            case Normal:
                updateRate = TICK_DELAY / 2;
                break;
            case Dificil:
                updateRate = TICK_DELAY / 3;
                break;
        }

        scheduler.scheduleAtFixedRate(() -> tick(), TICK_DELAY, updateRate, TimeUnit.MILLISECONDS);
        started = true;
    }

    public void stopTimer() {
        if (scheduler != null) {
            scheduler.shutdown();
            started = false;

        }
    }

    public String getName() {
        return name;
    }

    public int getNumSnakes() {
        return numSnakes.get();
    }

    public int getId() {
        return id;
    }

    public int getJugadoresMinimos() {
        return jugadoresMinimos;
    }

    public GameType.Dificultad getDificultad() {
        return dificultad;
    }

    public Type getTipo() {
        return Tipo;
    }

}
