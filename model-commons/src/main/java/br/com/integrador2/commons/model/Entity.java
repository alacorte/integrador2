package br.com.integrador2.commons.model;

import java.io.Serializable;

/**
 * Base para as entidades de dominio compartilhadas entre backend e frontend.
 * Mantida deliberadamente simples ate a primeira spec definir as entidades reais.
 */
public abstract class Entity implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
