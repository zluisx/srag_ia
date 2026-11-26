package br.edu.unifsa.srag.repository;

import br.edu.unifsa.srag.domain.Predicao;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface PredicaoRepository extends JpaRepository<Predicao, Long>, JpaSpecificationExecutor<Predicao> {
    List<Predicao> findByRegistroIdOrderByCriadoEmDesc(Long registroId);
    Page<Predicao> findByUsuarioId(Long usuarioId, Pageable pageable);
    long countByRegistroId(Long registroId);
}
