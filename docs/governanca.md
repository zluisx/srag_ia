# Governança dos dados

## Escopo e responsáveis

A equipe usa o extrato público SIVEP-Gripe para a finalidade acadêmica de estudar a internação posterior em UTI. A ficha de demonstração recebe casos fictícios. O projeto evita datas de nascimento, nomes, números de notificação e texto livre na API. Faixas etárias e sintomas ainda podem contribuir para identificação por combinação, especialmente em unidades pequenas. Os arquivos de desenvolvimento e o histórico precisam de acesso controlado.

Na simulação institucional, a direção hospitalar seria o Data Owner e responderia pela finalidade e pelo acesso. A vigilância epidemiológica seria Data Steward e manteria dicionário, regras e qualidade. A TI seria Data Custodian e manteria o ambiente, os backups e a recuperação. A instituição que define o tratamento seria a controladora, eventual prestador de hospedagem atuaria como operador conforme contrato, e o encarregado receberia dúvidas e solicitações. O perfil de aplicativo “operador” identifica quem cadastra casos; esse nome de perfil é distinto da função jurídica de operador.

## Catálogo dos campos utilizados

Origem comum: extrato nacional SIVEP-Gripe, edição de 23/11/2026, e dicionário oficial de 2019 a 2025 entregues com o trabalho. A vigilância é responsável pela definição de todos os campos desta tabela. A última coluna mede percentual em branco nas 52.932 notificações do Nordeste antes do tratamento. Campo de saúde associado à pessoa na ficha é **pessoal sensível / restrito**. Idade, identificador e datas com potencial de vinculação são **pessoais / restritos** no contexto do registro. A UF isolada é informação territorial **não pessoal / pública**. O vínculo com uma notificação permanece restrito.

| Campo | Descrição, tipo e domínio usado | Finalidade | Classe | % branco |
|---|---|---|---|---:|
| NU_NOTIFIC | Identificador textual da notificação | Conferir presença e unicidade antes de excluir o campo | Pessoal | 0 |
| SG_UF_NOT | Sigla da UF notificadora | Recorte das nove UFs nordestinas | Territorial | 0 |
| HOSPITAL | Categoria 1 sim, 2 não, 9 ignorado | Selecionar hospitalizações | Saúde | 4,644 |
| UTI | Categoria 1 sim, 2 não, 9 ignorado | Definir alvo observado com data de entrada | Saúde | 15,890 |
| DT_INTERNA | Data da admissão | Delimitar população de 2025 e momento da decisão | Pessoal | 10,060 |
| DT_ENTUTI | Data de entrada em UTI | Confirmar temporalidade do desfecho | Pessoal | 71,484 |
| DT_NOTIFIC | Data de notificação | Verificar disponibilidade da ficha | Pessoal | 0 |
| DT_SIN_PRI | Data dos primeiros sintomas | Verificar cronologia | Pessoal | 0 |
| NU_IDADE_N | Idade inteira, domínio aplicado por unidade | Gerar faixa etária para capturar diferenças observadas entre estágios da vida | Pessoal | 0 |
| TP_IDADE | Dias (1, 0–30), meses (2, 0–11), anos (3, 0–150) | Interpretar corretamente a idade informada | Pessoal | 0 |
| DISPNEIA | Categoria de sinal | Dificuldade respiratória referida | Saúde | 8,267 |
| DESC_RESP | Categoria de sinal | Desconforto respiratório registrado | Saúde | 8,399 |
| SATURACAO | Categoria de sinal | Indicação de saturação de oxigênio abaixo de 95% | Saúde | 15,527 |
| FEBRE | Categoria de sinal | Caracterizar manifestação febril na apresentação | Saúde | 9,089 |
| TOSSE | Categoria de sinal | Caracterizar manifestação respiratória na apresentação | Saúde | 5,477 |
| FADIGA | Categoria de sinal | Caracterizar comprometimento funcional relatado | Saúde | 22,856 |

Para os seis sinais, a ficha usa 1 para sim, 2 para não e 9 para ignorado. O pipeline também converte vazio em ignorado. A API recebe a faixa etária já generalizada e os códigos dos sinais. Ela exige ao menos um sinal conhecido. A origem de uma resposta ignorada pode refletir a prática de preenchimento do serviço. O modelo pode explorar essa associação. Os percentuais após as regras e a proporção de ignorados estão no [relatório reproduzível](qualidade.json).

## Qualidade e rastreabilidade

- **Acurácia de domínio.** Verificar unidade e valor de idade, códigos de sinais e alvo. Os limites verificam plausibilidade. A confirmação clínica dependeria de fonte independente.
- **Completude.** Exigir alvo conhecido, idade válida, sinal conhecido e datas necessárias à coorte. Preservar desconhecimento nos campos opcionais.
- **Consistência.** Conferir unicidade por notificação e concordância entre UTI e a data de entrada. Uma pessoa pode gerar mais de uma notificação. A base aberta não permite demonstrar independência por pessoa.
- **Tempestividade.** Aceitar ficha notificada até o dia da admissão e sintomas iniciados até a notificação. UTI positiva exige dia posterior dentro da janela do extrato. A aplicação coleta a faixa e os sinais no momento de preencher a ficha fictícia; não coleta datas individuais.
- **Linhagem.** CSV identificado por SHA-256 → seleção do Nordeste e regras do script → treino estratificado e pipeline → tabela de probabilidades e metadados → predição com versão, entrada, resultado, pessoa solicitante e hora. A versão do modelo deriva da origem e do conteúdo da tabela e dos limiares. A lista das 194 colunas e seu destino acompanha o [inventário](destino-colunas.tsv).

## Acesso, segurança e retenção

| Recurso | Operador | Gestor/auditor |
|---|---|---|
| Criar, editar, arquivar, prever e solicitar revisão | Próprios registros | Próprios registros |
| Histórico e detalhes | Próprias predições | Todas pela auditoria |
| Filtros gerais e pedidos de revisão | Sem acesso | Consulta |
| Model card, transparência e privacidade na aplicação | Consulta | Consulta |

A API aplica escopo de usuário na consulta SQL, senhas com BCrypt e JWT assinado com expiração de 30 minutos. O token fica na memória da página. O banco é relacional, com chaves estrangeiras e transação para fixar entrada e resultado a cada execução. Arquivar uma ficha preserva as predições já feitas. Os logs de acesso da aplicação registram rota, método, identificador interno e status. A demonstração escuta no endereço local. Para hospedagem, a instituição precisaria configurar TLS, criptografia de disco e backups, restrições de acesso ao servidor, gestão de segredos e monitoramento de exportações. São controles propostos para outro ambiente.

A retenção escolhida para a demonstração vai até 30 dias após a avaliação final. O custodiante deve eliminar a cópia local e os backups nesse prazo, registrando a conclusão. A eliminação é operacional e manual. Durante a demonstração, clicar em “Solicitar revisão humana” marca a predição. O gestor identifica os pedidos pela auditoria e encaminha à equipe responsável. Como procedimento proposto, o responsável examina a ficha e comunica o parecer ao solicitante em até dois dias úteis, registrando o parecer em protocolo institucional. A interface registra a solicitação; o parecer e o prazo dependem desse procedimento externo.

A finalidade, a necessidade de cada campo e os controles acima orientam o desenho do demonstrador. Referência normativa do trabalho: [Lei Geral de Proteção de Dados Pessoais](https://www.planalto.gov.br/ccivil_03/_ato2015-2018/2018/lei/l13709.htm).
