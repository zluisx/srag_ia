package br.edu.unifsa.srag.config;

import br.edu.unifsa.srag.domain.Perfil;
import br.edu.unifsa.srag.domain.Usuario;
import br.edu.unifsa.srag.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Cria um gestor e um operador de teste se o banco estiver vazio. Troque as senhas em produção. */
@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String gestorEmail;
    private final String gestorSenha;
    private final String operadorEmail;
    private final String operadorSenha;

    public DataInitializer(UsuarioRepository usuarios, PasswordEncoder encoder,
                           @Value("${app.seed.gestor-email}") String gestorEmail,
                           @Value("${app.seed.gestor-senha}") String gestorSenha,
                           @Value("${app.seed.operador-email}") String operadorEmail,
                           @Value("${app.seed.operador-senha}") String operadorSenha) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.gestorEmail = gestorEmail;
        this.gestorSenha = gestorSenha;
        this.operadorEmail = operadorEmail;
        this.operadorSenha = operadorSenha;
    }

    @Override
    public void run(String... args) {
        if (usuarios.count() > 0) return;
        criar("Gestor de Teste", gestorEmail, gestorSenha, Perfil.GESTOR);
        criar("Operador de Teste", operadorEmail, operadorSenha, Perfil.OPERADOR);
        log.info("Usuários iniciais criados: {} (GESTOR) e {} (OPERADOR)", gestorEmail, operadorEmail);
    }

    private void criar(String nome, String email, String senha, Perfil perfil) {
        Usuario u = new Usuario();
        u.setNome(nome);
        u.setEmail(email.toLowerCase());
        u.setSenhaHash(encoder.encode(senha));
        u.setPerfil(perfil);
        usuarios.save(u);
    }
}
