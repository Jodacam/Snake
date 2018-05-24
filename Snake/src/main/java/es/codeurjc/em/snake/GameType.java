package es.codeurjc.em.snake;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author Jose Daniel Campos
 */
public class GameType {
    
    
    public GameType(){};
    
    public enum Dificultad{
        Facil ,
        Normal,
        Dificil   
    }
    
    public enum Type{
        Lobby,
        Arcade,
        Unlimited
    }
    
    private int jugadores;

    public int getJudores() {
        return jugadores;
    }
    private String name;
    private Dificultad dificultad;
    private Type Tipo;
    

    public String getName() {
        return name;
    }

    public Dificultad getDificultad() {
        return dificultad;
    }

    public Type getTipo() {
        return Tipo;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDificultad(Dificultad dificultad) {
        this.dificultad = dificultad;
    }

    public void setTipo(Type Tipo) {
        this.Tipo = Tipo;
    }
    
    

    public GameType(String name, Dificultad dificultad, Type Tipo,int j) {
        this.name = name;
        this.dificultad = dificultad;
        this.Tipo = Tipo;
        this.jugadores = j;
    }
    
    
    
}
