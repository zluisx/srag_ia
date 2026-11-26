package br.edu.unifsa.srag.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Caso submetido para análise. O "identificador" é um código interno (ex.: número do leito/atendimento),
 * NUNCA CPF, nome ou outro dado que identifique o paciente (LGPD: princípio da necessidade).
 */
@Entity
@Table(name = "registros_analise")
public class RegistroAnalise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String identificador;

    /** Dados do caso (JSON), no formato do esquema_entrada.json. */
    @Column(name = "dados_json", nullable = false, length = 8000)
    private String dadosJson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por_id", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    public Long getId() { return id; }
    public String getIdentificador() { return identificador; }
    public void setIdentificador(String identificador) { this.identificador = identificador; }
    public String getDadosJson() { return dadosJson; }
    public void setDadosJson(String dadosJson) { this.dadosJson = dadosJson; }
    public Usuario getCriadoPor() { return criadoPor; }
    public void setCriadoPor(Usuario criadoPor) { this.criadoPor = criadoPor; }
    public Instant getCriadoEm() { return criadoEm; }
    public Instant getAtualizadoEm() { return atualizadoEm; }
    public void setAtualizadoEm(Instant atualizadoEm) { this.atualizadoEm = atualizadoEm; }
}
