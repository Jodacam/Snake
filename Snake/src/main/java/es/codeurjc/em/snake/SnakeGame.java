package es.codeurjc.em.snake;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class SnakeGame {

    private final static long TICK_DELAY = 100;

    private ConcurrentHashMap<Integer, Snake> snakes = new ConcurrentHashMap<>();
    private AtomicInteger numSnakes = new AtomicInteger();
    private List<Fruit> frutas = new ArrayList<Fruit>();
    private long elapseTime = 0;
    private ScheduledExecutorService scheduler;
    private int jugadoresMinimos = 0;

    private String name;

    private int id;

    public SnakeGame(String name, int id,int Jugadores) {
        this.name = name;
        this.id = id;
        jugadoresMinimos = Jugadores;
    }

    public void addSnake(Snake snake) {

        synchronized (this) {
            snakes.put(snake.getId(), snake);
        }
        int count = numSnakes.getAndIncrement();

        if (count == 0 && jugadoresMinimos > 0) {
            startTimer();
        }
    }

    public Collection<Snake> getSnakes() {
        return snakes.values();
    }

    public void removeSnake(Snake snake) {
        synchronized (this) {
            System.out.print("Serpiente Borrada en partida" + this.getName());
            snakes.remove(Integer.valueOf(snake.getId()));
        }
        int count = numSnakes.decrementAndGet();

        if (count == 0 && jugadoresMinimos > 0) {
            stopTimer();
        }
    }

    private void tick() {

        try {
            elapseTime += System.currentTimeMillis();

            for (Snake snake : getSnakes()) {
                snake.update(getSnakes(), frutas);
            }

            if (elapseTime > 1000) {
                elapseTime = 0;
                if (frutas.size() < 20) {
                    Fruit f = new Fruit(200);
                    frutas.add(f);
                }
            }
            StringBuilder name = new StringBuilder();
            
            StringBuilder sb = new StringBuilder();
            for (Snake snake : getSnakes()) {
                name.append("{\"nombre\": \""+snake.getName()+"\",");
                name.append("\"puntos\": "+snake.getPoints()+"},");
                
                sb.append(getLocationsJson(snake));
                sb.append(',');
            }
            StringBuilder f = new StringBuilder();
            for (Fruit fruit : frutas) {
                f.append(String.format("{\"x\": %d, \"y\": %d}", fruit.getPosition().x, fruit.getPosition().y));
                f.append(",");
            }

            name.deleteCharAt(name.length() - 1);
            sb.deleteCharAt(sb.length() - 1);
            f.deleteCharAt(f.length() - 1);
            String msg = String.format("{\"type\": \"update\", \"data\" : [%s] , \"fruits\" : [%s], \"People\":[%s] }", sb.toString(), f.toString(),name.toString());

            broadcast(msg);

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
               
                synchronized(snake){
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
        
    }

    public void startTimer() {
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(() -> tick(), TICK_DELAY, TICK_DELAY, TimeUnit.MILLISECONDS);
    }

    public void stopTimer() {
        if (scheduler != null) {
            scheduler.shutdown();
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
}
