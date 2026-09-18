package br.com.integrador2.integrador.origem;

import br.com.integrador2.commons.mensageria.RespostaConsolidada;

/**
 * Fronteira com os sistemas de origem. Hoje o outro lado e um broker ActiveMQ
 * embutido fazendo o papel do IBM MQ; quando o IBM MQ real existir, a troca
 * fica contida na implementacao desta interface e no bean de ConnectionFactory
 * externa — nenhum outro ponto do sistema muda.
 */
public interface OrigemGateway {

    void responder(String filaResposta, RespostaConsolidada resposta);
}
