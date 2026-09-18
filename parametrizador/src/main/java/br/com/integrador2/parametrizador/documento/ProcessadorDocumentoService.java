package br.com.integrador2.parametrizador.documento;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import br.com.integrador2.commons.mensageria.DocumentoRecebido;
import br.com.integrador2.commons.mensageria.RespostaParcial;
import br.com.integrador2.commons.model.OrigemProcessador;
import br.com.integrador2.commons.model.parametrizacao.ExecucaoDocumento;
import br.com.integrador2.commons.model.parametrizacao.StatusExecucao;
import br.com.integrador2.parametrizador.persistence.ExecucaoDocumentoRepository;
import br.com.integrador2.parametrizador.regras.FatoDocumento;
import br.com.integrador2.parametrizador.regras.MotorRegras;

@Service
public class ProcessadorDocumentoService {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorDocumentoService.class);

    private final MotorRegras motorRegras;
    private final ExecucaoDocumentoRepository execucoes;
    private final ObjectMapper objectMapper;

    public ProcessadorDocumentoService(MotorRegras motorRegras, ExecucaoDocumentoRepository execucoes,
            ObjectMapper objectMapper) {
        this.motorRegras = motorRegras;
        this.execucoes = execucoes;
        this.objectMapper = objectMapper;
    }

    /**
     * Se o JMS reentregar um documento ja processado, a regra nao roda de novo:
     * a execucao gravada e reaproveitada e a resposta parcial e republicada.
     */
    @Transactional
    public RespostaParcial processar(DocumentoRecebido documento) {
        Optional<ExecucaoDocumento> jaProcessado = execucoes.findByCorrelationId(documento.getCorrelationId());
        if (jaProcessado.isPresent()) {
            ExecucaoDocumento execucao = jaProcessado.get();
            log.info("Documento ja processado anteriormente, republicando resultado gravado");
            return montarResposta(execucao);
        }

        ExecucaoDocumento execucao = new ExecucaoDocumento(documento.getCorrelationId(),
                documento.getTipoDocumento(), documento.getPayloadJson());
        try {
            FatoDocumento fato = new FatoDocumento(documento.getTipoDocumento(),
                    lerCampos(documento.getPayloadJson()));
            motorRegras.aplicar(fato);
            execucao.concluirComSucesso(serializarResultado(fato));
        } catch (RuntimeException | IOException e) {
            log.error("Falha ao aplicar regras no documento", e);
            execucao.concluirComErro(e.getMessage());
        }

        execucoes.save(execucao);
        return montarResposta(execucao);
    }

    private RespostaParcial montarResposta(ExecucaoDocumento execucao) {
        RespostaParcial resposta = new RespostaParcial(execucao.getCorrelationId(),
                OrigemProcessador.PARAMETRIZADOR, execucao.getPayloadSaida(), execucao.getStatus());
        resposta.setMensagemErro(execucao.getMensagemErro());
        return resposta;
    }

    private Map<String, Object> lerCampos(String payloadJson) throws IOException {
        if (payloadJson == null || payloadJson.trim().isEmpty()) {
            return new HashMap<>();
        }
        return objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {
        });
    }

    private String serializarResultado(FatoDocumento fato) throws IOException {
        Map<String, Object> resultado = new HashMap<>();
        resultado.put("decisao", fato.getDecisao());
        resultado.put("observacao", fato.getObservacao());
        return objectMapper.writeValueAsString(resultado);
    }
}
