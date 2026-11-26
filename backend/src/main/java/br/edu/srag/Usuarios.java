package br.edu.srag;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.simple.JdbcClient;

@Repository
class Usuarios {
    record Usuario(long id, String email, String senha, String perfil, boolean ativo) {}
    private static final String CAMPOS = "SELECT id,email,senha,perfil,ativo FROM usuarios";
    private final JdbcClient db;
    Usuarios(JdbcClient db) { this.db = db; }

    Optional<Usuario> buscar(String email) {
        return db.sql(CAMPOS + " WHERE email=?").param(email).query(Usuario.class).optional();
    }

    Optional<Usuario> buscarPorId(long id) {
        return db.sql(CAMPOS + " WHERE id=?").param(id).query(Usuario.class).optional();
    }

    List<Usuario> listar() {
        return db.sql(CAMPOS + " ORDER BY email").query(Usuario.class).list();
    }

    void adicionar(String email,String senha,String perfil) {
        db.sql("INSERT INTO usuarios(email,senha,perfil) VALUES(?,?,?)")
            .params(email,senha,perfil).update();
    }

    /** Cria a conta e devolve o registro gravado (ativa por padrão). */
    Usuario criar(String email,String senha,String perfil) {
        adicionar(email,senha,perfil);
        return buscar(email).orElseThrow();
    }

    int alterarPerfil(long id,String perfil) {
        return db.sql("UPDATE usuarios SET perfil=? WHERE id=?").params(perfil,id).update();
    }

    int alterarAtivo(long id,boolean ativo) {
        return db.sql("UPDATE usuarios SET ativo=? WHERE id=?").params(ativo,id).update();
    }
}
