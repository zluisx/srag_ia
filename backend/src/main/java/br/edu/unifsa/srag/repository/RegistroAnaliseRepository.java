package br.edu.unifsa.srag.repository;

import br.edu.unifsa.srag.domain.RegistroAnalise;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistroAnaliseRepository extends JpaRepository<RegistroAnalise, Long> {
    Page<RegistroAnalise> findByCriadoPorId(Long usuarioId, Pageable pageable);
    boolean existsByIdentificador(String identificador);
}
