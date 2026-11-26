package br.edu.srag;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@Repository
class Registros {
    record Caso(long id, Modelo.Entrada entrada) {}
    record Predicao(long id, long caso, String usuario, OffsetDateTime instante, Modelo.Entrada entrada,
                    Modelo.Resultado resultado, boolean revisao) {}
    private final JdbcClient db;
    private final ObjectMapper json;
    Registros(JdbcClient db, ObjectMapper json) { this.db=db; this.json=json; }

    private long inserir(String sql, Object... parametros) {
        var chaves = new GeneratedKeyHolder();
        db.sql(sql).params(parametros).update(chaves, "ID");
        return Objects.requireNonNull(chaves.getKey()).longValue();
    }
    private Caso lerCaso(ResultSet rs, int i) throws SQLException {
        return new Caso(rs.getLong("id"),json.readValue(rs.getString("entrada"),Modelo.Entrada.class));
    }
    Caso criar(long usuario, Modelo.Entrada entrada) {
        return new Caso(inserir("INSERT INTO casos(usuario_id,entrada) VALUES(?,?)", usuario,
                     json.writeValueAsString(entrada)), entrada);
    }
    List<Caso> casos(long usuario) {
        return db.sql("SELECT id,entrada FROM casos WHERE usuario_id=? AND NOT excluido ORDER BY id DESC")
                 .param(usuario).query(this::lerCaso).list();
    }
    Caso caso(long id, long usuario, boolean bloquear) {
        return db.sql("SELECT id,entrada FROM casos WHERE id=? AND usuario_id=? AND NOT excluido"
                 + (bloquear ? " FOR UPDATE" : "")).params(id,usuario).query(this::lerCaso).optional()
                 .orElseThrow(Registros::ausente);
    }
    void editar(long id, long usuario, Modelo.Entrada entrada) {
        conferir(db.sql("UPDATE casos SET entrada=? WHERE id=? AND usuario_id=? AND NOT excluido")
                   .params(json.writeValueAsString(entrada),id,usuario).update());
    }
    void excluir(long id,long usuario) {
        conferir(db.sql("UPDATE casos SET excluido=TRUE WHERE id=? AND usuario_id=? AND NOT excluido")
                   .params(id,usuario).update());
    }
    long salvarPredicao(Caso caso, long usuario, Modelo.Resultado resultado) {
        return inserir("""
            INSERT INTO predicoes(caso_id,usuario_id,entrada,resultado,faixa,versao,instante)
            VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)
            """,caso.id(),usuario,json.writeValueAsString(caso.entrada()),json.writeValueAsString(resultado),
            resultado.faixa(),resultado.versao());
    }
    private static final String HISTORICO = """
        SELECT p.id,p.caso_id,u.email,p.instante,p.entrada,p.resultado,p.revisao
        FROM predicoes p JOIN usuarios u ON u.id=p.usuario_id WHERE 1=1
        """;
    private Predicao lerPredicao(ResultSet rs, int i) throws SQLException {
        return new Predicao(rs.getLong("id"),rs.getLong("caso_id"),rs.getString("email"),
            rs.getObject("instante",OffsetDateTime.class),json.readValue(rs.getString("entrada"),Modelo.Entrada.class),
            json.readValue(rs.getString("resultado"),Modelo.Resultado.class),rs.getBoolean("revisao"));
    }
    Predicao predicao(long id, long usuario) {
        return db.sql(HISTORICO + " AND p.id=? AND p.usuario_id=?").params(id,usuario)
                 .query(this::lerPredicao).optional().orElseThrow(Registros::ausente);
    }
    List<Predicao> historico(Long dono, Long caso, String email, String faixa, LocalDate inicio, LocalDate fim) {
        StringBuilder sql = new StringBuilder(HISTORICO);
        List<Object> args = new ArrayList<>();
        if (dono!=null) { sql.append(" AND p.usuario_id=?"); args.add(dono); }
        if (caso!=null) { sql.append(" AND p.caso_id=?"); args.add(caso); }
        if (email!=null) { sql.append(" AND u.email=?"); args.add(email); }
        if (faixa!=null) { sql.append(" AND p.faixa=?"); args.add(faixa); }
        var zona = ZoneId.of("America/Sao_Paulo");
        if (inicio!=null) { sql.append(" AND p.instante>=?"); args.add(inicio.atStartOfDay(zona).toOffsetDateTime()); }
        if (fim!=null) { sql.append(" AND p.instante<?"); args.add(fim.plusDays(1).atStartOfDay(zona).toOffsetDateTime()); }
        sql.append(" ORDER BY p.id DESC");
        return db.sql(sql.toString()).params(args).query(this::lerPredicao).list();
    }
    void revisar(long id, long usuario) {
        conferir(db.sql("UPDATE predicoes SET revisao=TRUE WHERE id=? AND usuario_id=?")
                   .params(id,usuario).update());
    }
    static ResponseStatusException ausente() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND,"Registro não encontrado.");
    }
    private static void conferir(int alterados) { if (alterados==0) throw ausente(); }
}
