# Model card · internação posterior em UTI

**Versão avaliada** `uti-36737af7c12c`. Regressão logística treinada em 26/11/2026. Usuário previsto para a demonstração: profissional da central de regulação. A pergunta é se uma notificação de SRAG hospitalizada, fora da UTI no dia da admissão, registrará entrada posterior em UTI durante essa internação.

## População e momento da decisão

Dos 52.932 registros de UF de notificação no Nordeste, 13.405 atenderam aos critérios do extrato. O treino contém 10.724 notificações, das quais 453 positivas. O teste contém 2.681, das quais 113 positivas. A data de internação deve pertencer a 2025. O início de sintomas precede ou coincide com a notificação, e a notificação precede ou coincide com a admissão. O SIVEP-Gripe registra dias, sem horário preciso, e permite atualizações retrospectivas. A disponibilidade exata de cada sinal à beira do leito é uma hipótese.

O alvo positivo exige `UTI=1` e data conhecida de entrada posterior ao dia da admissão. O negativo exige `UTI=2` e data de entrada vazia. Casos com UTI no dia da admissão, códigos desconhecidos ou cronologia inconsistente saem antes da divisão. A taxa observada se refere a essa população selecionada.

## Treinamento e escolha

Divisão estratificada 80/20 com semente 42, seguida de cinco dobras estratificadas apenas no treino. Cada candidato usa a transformação da idade e dos sinais, um ColumnTransformer com codificação one-hot e um classificador no mesmo Pipeline. Foram comparados pelo valor médio da precisão média (AP) por dobra. AP resume a relação entre precisão e sensibilidade em vários limiares e é informativa quando o evento é raro.

| Candidato | AP média na validação |
|---|---:|
| DummyClassifier, prevalência do treino | 0,0422 |
| Regressão logística | 0,0708 |
| Árvore, profundidade máxima 5 e folha mínima de 60 | 0,0694 |

O atraso na identificação de uma futura internação em UTI motivou dar maior peso à sensibilidade. Alertas em excesso também ocupam a equipe. Para equilibrar esses custos no demonstrador, escolhemos o corte que maximiza **F2** nas probabilidades fora da dobra do modelo selecionado. A prevalência original foi preservada, com estratificação, avaliação por AP e ajuste de limiar para lidar com a classe minoritária. Baixo significa probabilidade abaixo de 0,0526715581. O alerta começa nesse limite. A faixa alta começa em 0,0918085820, o percentil 75 dos escores de alerta no treino fora da dobra. As faixas ordenam prioridade relativa dentro do protótipo.

## Teste final

| Indicador | Resultado |
|---|---:|
| Verdadeiros positivos / falsos negativos | 59 / 54 |
| Falsos positivos / verdadeiros negativos | 788 / 1.780 |
| Sensibilidade / precisão dos alertas | 52,21% / 6,97% |
| F2 / AP / AUROC | 0,2271 / 0,0777 / 0,6533 |
| Brier | 0,0398 |

Entre 847 alertas, 59 corresponderam a uma internação posterior em UTI. O modelo perdeu 54 dos 113 eventos. A separação é modesta e o grupo de teste é interno, do mesmo período e região. Faltam validação externa, avaliação prospectiva e avaliação de calibração por hospital para sustentar uso clínico. O histórico de internações reflete também oferta de leitos e práticas locais. Usar o sistema para indicar, negar ou reservar um leito individualmente seria inadequado.

## Explicações e integração

No treino, a queda média de AP em cinco permutações foi maior para saturação abaixo de 95% (0,0222), faixa etária (0,0123) e fadiga (0,0066). A tabela completa está nos metadados. Importância por permutação mede associação para o modelo e pode variar com a correlação entre sinais.

Para explicar um caso, o Java substitui cada entrada, uma por vez, pela categoria mais frequente no treino e calcula a diferença na probabilidade. A interface mostra até três diferenças em pontos percentuais, ordenadas pelo valor absoluto. Os efeitos descrevem o comportamento do modelo e dependem da referência escolhida.

Sete faixas etárias e seis sinais de três categorias geram 5.103 combinações. No treino, o Python executa o Pipeline em todas e exporta uma tabela JSON para o Spring. Assim, a aplicação Java executa inferência local e dispensa um segundo servidor. O formulário rejeita as sete combinações com todos os sinais ignorados. Cada nova versão do modelo deve regenerar tabela e metadados e preservar o histórico das versões anteriores.

Artefatos: [pipeline serializado](../modelo/pipeline.joblib), [metadados e métricas](../backend/src/main/resources/modelo/metadados.json), [fluxo de seleção](qualidade.json) e [destino de todas as colunas](destino-colunas.tsv).
