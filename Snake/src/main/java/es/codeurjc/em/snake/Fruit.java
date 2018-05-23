/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package es.codeurjc.em.snake;

/**
 *
 * @author Jose Daniel Campos
 */
public class Fruit {
    
    Location Position;
    int Points;
    private final String hexColor;
    
    public Fruit(int P){
    hexColor = SnakeUtils.getRandomHexColor();
    Position = SnakeUtils.getRandomLocation();
    Points = P;
    }

    public Location getPosition() {
        return Position;
    }
    
    
    
}
