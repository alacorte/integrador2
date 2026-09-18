package br.com.integrador2.parametrizador.regras;

/**
 * Abstrai o Drools do resto do servico. A fatia de regras dinamicas troca a
 * implementacao (DRL vindo do banco, KieBase recarregado) sem tocar no fluxo
 * de mensageria nem no servico de processamento.
 */
public interface MotorRegras {

    void aplicar(FatoDocumento fato);
}
