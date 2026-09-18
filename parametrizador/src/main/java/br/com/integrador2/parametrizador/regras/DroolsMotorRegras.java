package br.com.integrador2.parametrizador.regras;

import org.kie.api.KieBase;
import org.kie.api.runtime.KieSession;
import org.springframework.stereotype.Component;

@Component
public class DroolsMotorRegras implements MotorRegras {

    private final KieBase kieBase;

    public DroolsMotorRegras(KieBase kieBase) {
        this.kieBase = kieBase;
    }

    @Override
    public void aplicar(FatoDocumento fato) {
        KieSession sessao = kieBase.newKieSession();
        try {
            sessao.insert(fato);
            sessao.fireAllRules();
        } finally {
            sessao.dispose();
        }
    }
}
