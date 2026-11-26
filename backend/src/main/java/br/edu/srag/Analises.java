package br.edu.srag;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class Analises {
    private final Registros registros;
    private final Modelo modelo;
    Analises(Registros registros, Modelo modelo) { this.registros=registros; this.modelo=modelo; }

    Registros.Caso criar(long autor,Modelo.Entrada entrada) {
        modelo.validar(entrada);
        return registros.criar(autor,entrada);
    }
    Registros.Caso editar(long id,long autor,Modelo.Entrada entrada) {
        modelo.validar(entrada);
        registros.editar(id,autor,entrada);
        return new Registros.Caso(id,entrada);
    }
    @Transactional
    public Registros.Predicao executar(long id,long autor) {
        var caso = registros.caso(id,autor,true);
        var resultado = modelo.avaliar(caso.entrada());
        return registros.predicao(registros.salvarPredicao(caso,autor,resultado),autor);
    }
}
