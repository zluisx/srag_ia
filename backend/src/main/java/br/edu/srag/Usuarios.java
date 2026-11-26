package br.edu.srag;

import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.jdbc.core.simple.JdbcClient;

@Repository
class Usuarios {
    record Usuario(long id, String email, String senha, String perfil) {}
    private final JdbcClient db;
    Usuarios(JdbcClient db) { this.db = db; }

    Optional<Usuario> buscar(String email) {
        return db.sql("SELECT id,email,senha,perfil FROM usuarios WHERE email=?")
            .param(email).query(Usuario.class).optional();
    }

    void adicionar(String email,String senha,String perfil) {
        db.sql("INSERT INTO usuarios(email,senha,perfil) VALUES(?,?,?)")
            .params(email,senha,perfil).update();
    }
}
