package br.edu.unifsa.srag.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Histórico de predições: cada execução do modelo gera uma NOVA linha.
 * Nada é sobrescrito (exceto o pedido de revisão humana, que é um acréscimo).
 */
@Entity
@Table(name = "predicoes")
public class Predicao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registro_id", nullable = false)
    private RegistroAnalise registro;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Cópia dos dados usados na predição (o registro pode ser editado depois). */
    @Column(name = "entrada_json", nullable = false, length = 8000)
    private String entradaJson;

    @Column(nullable = false)
    private double probabilidade;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private FaixaRisco faixa;

    @Column(name = "fatores_json", length = 4000)
    private String fatoresJson;

    @Column(name = "versao_modelo", nullable = false, length = 40)
    private String versaoModelo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    // Solicitação de revisão humana (LGPD, art. 20)
    @Column(name = "revisao_solicitada", nullable = false)
    private boolean revisaoSolicitada = false;

    @Column(name = "revisao_motivo", length = 500)
    private String revisaoMotivo;

    @Column(name = "revisao_em")
    private Instant revisaoEm;

    public Long getId() { return id; }
    public RegistroAnalise getRegistro() { return registro; }
    public void setRegistro(RegistroAnalise registro) { this.registro = registro; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
    public String getEntradaJson() { return entradaJson; }
    public void setEntradaJson(String entradaJson) { this.entradaJson = entradaJson; }
    public double getProbabilidade() { return probabilidade; }
    public void setProbabilidade(double probabilidade) { this.probabilidade = probabilidade; }
    public FaixaRisco getFaixa() { return faixa; }
    public void setFaixa(FaixaRisco faixa) { this.faixa = faixa; }
    public String getFatoresJson() { return fatoresJson; }
    public void setFatoresJson(String fatoresJson) { this.fatoresJson = fatoresJson; }
    public String getVersaoModelo() { return versaoModelo; }
    public void setVersaoModelo(String versaoModelo) { this.versaoModelo = versaoModelo; }
    public Instant getCriadoEm() { return criadoEm; }
    public boolean isRevisaoSolicitada() { return revisaoSolicitada; }
    public void setRevisaoSolicitada(boolean revisaoSolicitada) { this.revisaoSolicitada = revisaoSolicitada; }
    public String getRevisaoMotivo() { return revisaoMotivo; }
    public void setRevisaoMotivo(String revisaoMotivo) { this.revisaoMotivo = revisaoMotivo; }
    public Instant getRevisaoEm() { return revisaoEm; }
    public void setRevisaoEm(Instant revisaoEm) { this.revisaoEm = revisaoEm; }
}
