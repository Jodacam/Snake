package es.codeurjc.em.snake;

import es.codeurjc.em.snake.GameType.Type;
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
    private GameType.Dificultad dificultad;
    private Type Tipo;
    private long Tiempo;
    private String name;

    private  volatile int id;
    
    private  volatile boolean started = false;

    public SnakeGame(GameType g, int id) {
        Tiempo = 0;
        this.name = g.getName();
        this.id = id;        
        this.dificultad = g.getDificultad();
        this.Tipo = g.getTipo();
        this.jugadoresMinimos = g.getJudores();
    }

    public void addSnake(Snake snake) {

        synchronized (snake) {
            snakes.put(snake.getId(), snake);
        }
        
        int count = numSnakes.getAndIncrement();

        if (count == jugadoresMinimos-1 && Tipo != Type.Lobby && !started) {
            startTimer();
           
        }
    }

    public boolean isStarted() {
        return started;
    }

    public Collection<Snake> getSnakes() {
        return snakes.values();
    }

    public void removeSnake(Snake snake) {
        synchronized (snake) {
            System.out.print("Serpiente Borrada en partida" + this.getName());
            snakes.remove(Integer.valueOf(snake.getId()));
        }
        int count = numSnakes.decrementAndGet();

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
                name.append("{\"nombre\": \""+snake.getName()+"\",");
                name.append("\"puntos\": "+snake.getPoints()+",");
                name.append("\"color\": \""+snake.getHexColor()+"\"},");
                
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
            
            switch(Tipo){
                case Arcade:
                    StringBuilder s = new StringBuilder();
                   
                    boolean ganada = false;
                    for(Snake snake : getSnakes()){
                        if(snake.getTail().size() > 10){
                            s.append("{\"id\": \"" + snake.getName() +"\",  \"win\": true");  
                            s.append("},");
                            ganada = true;
                            Long previLong = SnakeHandler.Puntuaciones.putIfAbsent(snake.getName(), Tiempo);
                           
                            if (null != previLong ){
                                if (previLong > Tiempo){
                                    SnakeHandler.Puntuaciones.put(snake.getName(), Tiempo);
                                }
                            }
                                    
                            
                        }
                        else{
                         s.append("{\"id\":\""+snake.getName()+"\",  \"win\": false"); 
                         s.append("},");
                        }
                    }
                    if(ganada){
                        s.deleteCharAt(s.length() - 1);
                        String Win = String.format("{\"type\":\"endGame\", \"data\" : [%s]}", s.toString());
                        broadcast(Win); 
                        stopTimer();
                    }
                    
                    break;
                case Unlimited:
                    
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
    volatile long  updateRate = 0;
    public void startTimer() {
        scheduler = Executors.newScheduledThreadPool(1);
        
        switch(dificultad){
            case Facil:
                updateRate = TICK_DELAY;
                break;
            case Normal:
                updateRate = TICK_DELAY/2;
                break;
            case Dificil:
                updateRate = TICK_DELAY/3;
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
