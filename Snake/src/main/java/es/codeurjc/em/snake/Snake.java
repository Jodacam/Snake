package es.codeurjc.em.snake;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

public class Snake {

	private static final int DEFAULT_LENGTH = 5;

	private final int id;

	private Location head;
	private final Deque<Location> tail = new ArrayDeque<>();
	private int length = DEFAULT_LENGTH;

	private final String hexColor;
	private Direction direction;
        private int points;

	private final WebSocketSession session;
        
        private String name;

	public Snake(int id, WebSocketSession session) {
                
		this.id = id;
		this.session = session;
		this.hexColor = SnakeUtils.getRandomHexColor();
		resetState();
	}

	private void resetState() {
		this.direction = Direction.NONE;
		this.head = SnakeUtils.getRandomLocation();
		this.tail.clear();
		this.length = DEFAULT_LENGTH;
	}

	private synchronized void kill() throws Exception {
		resetState();
		sendMessage("{\"type\": \"dead\"}");
	}

    public int getPoints() {
        return points;
    }

	private synchronized void reward() throws Exception {
		this.length++;
		sendMessage("{\"type\": \"kill\"}");
	}

	protected synchronized void  sendMessage(String msg) throws Exception {
		this.session.sendMessage(new TextMessage(msg));
	}

	public synchronized void update(Collection<Snake> snakes,Collection<Fruit> fruits) throws Exception {

		Location nextLocation = this.head.getAdjacentLocation(this.direction);

		if (nextLocation.x >= Location.PLAYFIELD_WIDTH) {
			nextLocation.x = 0;
		}
		if (nextLocation.y >= Location.PLAYFIELD_HEIGHT) {
			nextLocation.y = 0;
		}
		if (nextLocation.x < 0) {
			nextLocation.x = Location.PLAYFIELD_WIDTH;
		}
		if (nextLocation.y < 0) {
			nextLocation.y = Location.PLAYFIELD_HEIGHT;
		}

		if (this.direction != Direction.NONE) {
			this.tail.addFirst(this.head);
			if (this.tail.size() > this.length) {
				this.tail.removeLast();
			}
			this.head = nextLocation;
		}

		handleCollisions(snakes);
                handleFruit(fruits);
                
	}

	private void handleCollisions(Collection<Snake> snakes) throws Exception {

		for (Snake snake : snakes) {

			boolean headCollision = this.id != snake.id && snake.getHead().equals(this.head);

			boolean tailCollision = snake.getTail().contains(this.head);

			if (headCollision || tailCollision) {
				kill();
				if (this.id != snake.id) {
					snake.reward();
				}
			}
		}
	}
        
        
        private void  handleFruit(Collection<Fruit> f){
            Iterator<Fruit> iter = f.iterator();
            while(iter.hasNext()){
                Fruit e = iter.next();
                if(this.head.equals(e.Position)){
                    iter.remove();
                    this.length++;
                    points+=e.Points;
                }
            }
            
            
        }

	public synchronized Location getHead() {
		return this.head;
	}

	public synchronized Collection<Location> getTail() {
		return this.tail;
	}

	public synchronized void setDirection(Direction direction) {
		this.direction = direction;
	}

	public int getId() {
		return this.id;
	}

	public String getHexColor() {
		return this.hexColor;
	}
        
        public String getName(){
            return name;
        }
        
        public void setName(String name){
            this.name = name;
        }
}
