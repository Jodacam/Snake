package es.codeurjc.em.snake;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import com.sun.javafx.print.Units;
import java.io.IOException;
import java.net.URI;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.websocket.DeploymentException;
import javax.websocket.Session;

import org.junit.BeforeClass;
import org.junit.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.support.HttpRequestWrapper;

public class SnakeTest {

    @BeforeClass
    public static void startServer() {
        Application.main(new String[]{"--server.port=9000"});
    }

    @Test
    public void testConnection() throws Exception {

        WebSocketClient wsc = new WebSocketClient();
        wsc.connect("ws://127.0.0.1:9000/snake");
        wsc.disconnect();
    }

    @Test
    public void testJoinLobby() throws Exception {

        AtomicReference<String> firstMsg = new AtomicReference<String>();

        WebSocketClient wsc = new WebSocketClient();
        wsc.onMessage((session, msg) -> {
            System.out.println("TestMessage: " + msg);
            firstMsg.compareAndSet(null, msg);
        });

        wsc.connect("ws://127.0.0.1:9000/snake");

        System.out.println("Connected");

        wsc.sendMessage(" {\"id\":0, \"messageType\": \"connect\", \"name\": \"name\", \"direction\": null}");

        Thread.sleep(1000);

        String msg = firstMsg.get();

        assertTrue("The fist message should contain 'join', but it is " + msg, msg.contains("join"));

        wsc.disconnect();
    }

    @Test
    public void testCreateGame() throws Exception {

        AtomicReference<String> firstMsg = new AtomicReference<String>();

        HttpResponse<String> request = Unirest.post("http://127.0.0.1:9000/games/").body(" {\"name\":\"Prueba1\",\n"
                + "            \"dificultad\": \"Normal\",\n"
                + "            \"Tipo\": \"Arcade\",\n"
                + "            \"jugadores\": 2}").asString();
        int Partida = Integer.parseInt(request.getBody());

        assertTrue("El id de partida tiene que ser mayor a 1", Partida > 0);

        WebSocketClient wsc = new WebSocketClient();

        wsc.onMessage((session, msg) -> {
            System.out.println("TestMessage: " + msg);
            firstMsg.compareAndSet(null, msg);
        });

        wsc.connect("ws://127.0.0.1:9000/snake");

        System.out.println("Connected");
        wsc.sendMessage(" {\"id\":" + Partida + ", \"messageType\": \"connect\", \"name\": \"name\", \"direction\": null}");

        Thread.sleep(1000);

        String msg = firstMsg.get();

        assertTrue("The fist message should contain 'join', but it is " + msg, msg.contains("join"));

        wsc.disconnect();
    }

    @Test
    public void testPartida() throws Exception {
        WebSocketClient[] Jugadores = new WebSocketClient[4];
        for (int i = 0; i < 4; i++) {
            WebSocketClient c = new WebSocketClient();
            c.onMessage((session, msg) -> {
                if (msg.contains("update")) {
                    //System.out.println("Esto es un update con : "+msg);
                } else if (msg.contains("join")) {
                    System.out.println("Esto es un join con : " + msg);
                } else if (msg.contains("endGame")) {
                    try {
                        c.disconnect();
                    } catch (IOException ex) {
                        Logger.getLogger(SnakeTest.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            });
            c.connect("ws://127.0.0.1:9000/snake");
            Jugadores[i] = c;

        }
        HttpResponse<String> request = Unirest.post("http://127.0.0.1:9000/games/").body(" {\"name\":\"PruebaPartida\",\n"
                + "            \"dificultad\": \"Normal\",\n"
                + "            \"Tipo\": \"Classic\",\n"
                + "            \"jugadores\": 4}").asString();

        int Partida = Integer.parseInt(request.getBody());

        assertTrue("El id de partida tiene que ser mayor a 1", Partida > 0);

        for (WebSocketClient c : Jugadores) {
            new Thread(() -> {
                try {
                    JugarPartida(61000, c, Partida);
                } catch (Exception ex) {
                    Logger.getLogger(SnakeTest.class.getName()).log(Level.SEVERE, null, ex);
                }
            }).start();
        }

        Thread.sleep(65000);

    }
//     

    public void JugarPartida(long Time, WebSocketClient client, int id) throws Exception {
        client.sendMessage(" {\"id\":" + id + ", \"messageType\": \"connect\", \"name\": +\"" + Thread.currentThread().getName() + "\", \"direction\": null}");
        System.out.println(Thread.currentThread().getName() + " EntraEn la partida");
        Thread.sleep(Time);
        System.out.println(Thread.currentThread().getName() + " Se sale de la partida");

        HttpResponse<String> requestGet = Unirest.get("http://127.0.0.1:9000/games/Puntuaciones/Classic").asString();
        String respon = requestGet.getBody();
        assertTrue("No ha llegado nada", respon != null);
        System.out.println(respon);
        client.disconnect();
    }

//     
//     
//     
//     
//     
    @Test
    public void testCarga() throws Exception {
        WebSocketClient[] clients = new WebSocketClient[10];
        for (int i = 0; i < 10; i++) {
            clients[i] = CrearCliente();
        }

        HttpResponse<String> request = Unirest.post("http://127.0.0.1:9000/games/").body(" {\"name\":\"salaA\",\n"
                + "            \"dificultad\": \"Normal\",\n"
                + "            \"Tipo\": \"Classic\",\n"
                + "            \"jugadores\": 4}").asString();
        int Partida = Integer.parseInt(request.getBody());

        assertTrue("El id de partida tiene que ser mayor a 1", Partida > 0);

        clients[0].sendMessage(" {\"id\":" + Partida + ", \"messageType\": \"connect\", \"name\": +\"" + "Hey" + "\", \"direction\": null}");

        HttpResponse<String> request2 = Unirest.post("http://127.0.0.1:9000/games/").body(" {\"name\":\"salaB\",\n"
                + "            \"dificultad\": \"Normal\",\n"
                + "            \"Tipo\": \"Classic\",\n"
                + "            \"jugadores\": 4}").asString();
        int Partida2 = Integer.parseInt(request2.getBody());

        assertTrue("El id de partida tiene que ser mayor a 1", Partida2 > 1);

        clients[5].sendMessage(" {\"id\":" + Partida2 + ", \"messageType\": \"connect\", \"name\": +\"" + "Ney" + "\", \"direction\": null}");

        Thread.sleep(1000);

        for (int i = 6; i < 10; i++) {
            WebSocketClient c = clients[i];
            new Thread(() -> {
                try {
                    JugarPartida(10000, c, Partida);
                } catch (Exception ex) {
                    Logger.getLogger(SnakeTest.class.getName()).log(Level.SEVERE, null, ex);
                }
            }).start();
        }

        for (int i = 1; i < 5; i++) {
            WebSocketClient c = clients[i];
            new Thread(() -> {
                try {
                    JugarPartida(10000, c, Partida2);
                } catch (Exception ex) {
                    Logger.getLogger(SnakeTest.class.getName()).log(Level.SEVERE, null, ex);
                }
            }).start();
        }

        Thread.sleep(20000);

    }

    public WebSocketClient CrearCliente() throws Exception {

        WebSocketClient c = new WebSocketClient();
        c.onMessage((session, msg) -> {

            try {

                if (msg.contains("update")) {
                    System.out.println("Esto es un update con : " + msg);
                } else if (msg.contains("join")) {
                    System.out.println("Esto es un join con : " + msg);
                } else if (msg.contains("endGame")) {

                    c.disconnect();

                } else if (msg.contains("failded-join")) {
                    System.out.println("No me he podido Unir : " + msg);
                    c.disconnect();
                }
            } catch (Exception e) {

            }
        });
        c.connect("ws://127.0.0.1:9000/snake");
        return c;
    }

}
