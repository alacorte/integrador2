package br.com.integrador2.commons.model.parametrizacao;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;

import br.com.integrador2.commons.model.EntidadeBase;

/**
 * Parametrizacao de um tipo de documento: por onde ele entra e para onde a
 * resposta volta no IBM MQ.
 */
@Entity
@Table(name = "tipo_documento")
public class TipoDocumento extends EntidadeBase {

    private static final long serialVersionUID = 1L;

    @Column(nullable = false, unique = true, length = 60)
    private String codigo;

    @Column(name = "fila_entrada", nullable = false, length = 120)
    private String filaEntrada;

    @Column(name = "fila_resposta", nullable = false, length = 120)
    private String filaResposta;

    @Column(length = 255)
    private String descricao;

    @Column(nullable = false)
    private boolean ativo = true;

    protected TipoDocumento() {
    }

    public TipoDocumento(String codigo, String filaEntrada, String filaResposta, String descricao) {
        this.codigo = codigo;
        this.filaEntrada = filaEntrada;
        this.filaResposta = filaResposta;
        this.descricao = descricao;
    }

    public String getCodigo() {
        return codigo;
    }

    public String getFilaEntrada() {
        return filaEntrada;
    }

    public void setFilaEntrada(String filaEntrada) {
        this.filaEntrada = filaEntrada;
    }

    public String getFilaResposta() {
        return filaResposta;
    }

    public void setFilaResposta(String filaResposta) {
        this.filaResposta = filaResposta;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
