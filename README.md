# Regulação SRAG

Projeto final integrado da UNIFSA · Variante A. Aplicação acadêmica para a central de regulação consultar a probabilidade de internação posterior em UTI entre casos hospitalizados por SRAG. A unidade de análise é uma notificação. O formulário usa faixa etária e seis sinais disponíveis na admissão.

## Executar no VS Code

Abra a pasta **projeto-srag**. No terminal PowerShell integrado, execute:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\iniciar.ps1
```

O inicializador usa o JDK 21 já preparado nesta máquina ou um JDK disponível em `JAVA_HOME` ou no PATH. O Maven Wrapper baixa o Maven e as dependências na primeira execução. Em outro computador, instale o JDK 21 antes desse passo. O ambiente Python e o CSV são necessários somente para reproduzir o treinamento.

No primeiro uso do banco padrão, o terminal pede duas senhas de pelo menos 12 caracteres. As contas iniciais são `operador@demo.local` e `gestor@demo.local`. Abra [a aplicação](http://127.0.0.1:8080/) quando o terminal informar que o Spring iniciou. Interrompa o processo com Ctrl+C. Ao reiniciar, utilize as mesmas senhas. O H2 mantém os dados em `backend/data/`.

No Linux ou macOS, configure `SRAG_OPERADOR_SENHA` e `SRAG_GESTOR_SENHA` no ambiente, entre em `backend` e execute `sh mvnw spring-boot:run`. O JDK é o mesmo. O [Swagger UI local](http://127.0.0.1:8080/swagger-ui.html) e a [especificação OpenAPI](http://127.0.0.1:8080/v3/api-docs) são servidos pelo Spring.

## Demonstrar o sistema

1. Entre como operador e abra **Nova análise**. Escolha uma faixa etária e informe ao menos um dos seis sinais de um caso fictício.
2. Clique em **Salvar e analisar**. Confira probabilidade, faixa, fatores locais e versão. Use **Solicitar revisão humana**.
3. Em **Meus registros**, edite a ficha e analise novamente. As duas execuções ficam disponíveis em **Histórico**, cada uma com a própria entrada.
4. Saia e entre como gestor. Em **Auditoria**, filtre por e-mail, período ou faixa e confira o pedido de revisão. As páginas **Transparência** e **Privacidade** explicam o desempenho e o tratamento dos dados.

[Vídeo da demonstração de ponta a ponta](docs/demonstracao.mp4)

## Arquitetura e segurança

A interface HTML, CSS e JavaScript é servida pelo Spring Boot. Os controllers HTTP recebem o JWT e encaminham a requisição ao serviço de análises. O repositório JDBC persiste usuários, casos e predições em H2. Cada predição grava um retrato da ficha, versão, resultado, principais fatores, autor e instante. Uma transação impede que a edição concorrente de um caso altere a entrada usada na execução.

O scikit-learn faz o treino offline. O script salva o Pipeline completo e exporta para Java o resultado exato das 5.103 combinações possíveis de faixas e sinais. A aplicação lê esse artefato ao iniciar. Assim, a demonstração roda em um único processo Java e também funciona sem o CSV. A explicação local compara a saída com a obtida ao substituir um atributo de cada vez por sua referência no treino.

| Configuração | Comportamento |
|---|---|
| `SRAG_BIND`, `SERVER_PORT` | Endereço e porta HTTP. Padrão local em 127.0.0.1 e 8080. |
| `SRAG_DB`, `SRAG_DB_PASSWORD` | URL JDBC H2 e senha. Para demonstração descartável, pode-se usar `jdbc:h2:mem:srag` e fornecer as senhas iniciais por ambiente. |
| `SRAG_OPERADOR_EMAIL`, `SRAG_GESTOR_EMAIL` | E-mails para provisionamento inicial. |
| `SRAG_OPERADOR_SENHA`, `SRAG_GESTOR_SENHA` | Definem a senha ao criar cada conta. Contas existentes mantêm seu hash BCrypt. |
| `SRAG_JWT_SECRET` | Segredo opcional de pelo menos 32 bytes. Na ausência, o processo gera uma chave aleatória e reiniciar encerra sessões. |

O operador consulta suas próprias análises. O gestor consulta também o histórico geral. A interface mantém o token em memória por sessão da página. A documentação de governança descreve minimização, acesso, retenção e as medidas adicionais previstas para hospedagem.

## Dados e reprodução

A edição recebida contém **336.448 notificações e 194 colunas**. Usamos a UF de notificação para recortar o Nordeste, onde havia 52.932 notificações e 48.894 hospitalizações confirmadas. O Piauí, incluído no Nordeste, tinha 1.404 notificações e 1.392 hospitalizações confirmadas. O recorte regional aumentou o número disponível de positivos para treino. O dicionário usado foi `dicionario-de-dados-2019-a-2025.pdf`. [Extrato oficial de 23/11/2026](https://dadosabertos.saude.gov.br/dataset/srag-2019-a-2026/resource/20c49de3-ddc3-4b76-a942-1518eaae9c91)

Para reproduzir com Python 3.12 a partir da raiz do projeto:

```powershell
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
$csv = Join-Path $env:USERPROFILE 'Downloads/INFLUD25-23-11-2026.csv'
.\.venv\Scripts\python.exe scripts/perfil_base.py $csv
.\.venv\Scripts\python.exe -m scripts.treinar $csv
```

Se precisar do arquivo original, use `python scripts/baixar_base.py data` e passe ao treino o CSV dessa pasta. O download verifica o SHA-256 da edição. O script de treino lê o CSV em blocos, gera os relatórios e atualiza os artefatos. Reinicie o Spring depois de retreinar. Carregue o joblib somente de uma origem confiável e execute o Python a partir da raiz para resolver o módulo de transformação.

## Resultados e documentação

A regressão logística alcançou AP média de **0,0708** na validação cruzada, frente a **0,0422** da referência e **0,0694** da árvore. No teste final, identificou **59 de 113** internações posteriores em UTI e produziu **788 falsos alertas**. Isso limita o projeto à demonstração acadêmica. O estudo cobre notificações selecionadas do Nordeste em 2025, com exclusão de UTI no mesmo dia da admissão. As estimativas dependem das práticas de notificação e da disponibilidade de leitos.

- [Model card com coorte, limiares, limitações e explicabilidade](docs/model-card.md)
- [Governança, catálogo, matriz de acesso e retenção](docs/governanca.md)
- [Relatório de qualidade antes e depois](docs/qualidade.json)
- [Destino das 194 colunas](docs/destino-colunas.tsv)
- [Esquema de entrada da API](docs/esquema-entrada.json)
- [Metadados da versão e importância global dos atributos](backend/src/main/resources/modelo/metadados.json)

## Verificação

Com JDK 21 configurado, execute `backend\mvnw.cmd -f backend/pom.xml test` a partir da raiz. O teste abre um servidor real em porta aleatória e usa H2 em memória. Ele cobre autenticação, validação de ficha, isolamento por perfil e proprietário, alteração, arquivamento, histórico e revisão.

As fontes técnicas incluem [Spring Boot](https://docs.spring.io/spring-boot/), [Spring Security](https://docs.spring.io/spring-security/reference/), [Springdoc OpenAPI](https://springdoc.org/), [H2](https://h2database.com/), [Apache Maven](https://maven.apache.org/), [scikit-learn](https://scikit-learn.org/stable/), [pandas](https://pandas.pydata.org/), [NumPy](https://numpy.org/), [SciPy](https://scipy.org/) e [joblib](https://joblib.readthedocs.io/). Documentação da base: [Ministério da Saúde](https://dadosabertos.saude.gov.br/dataset/srag-2019-a-2026).

## Entrega acadêmica e uso de IA

O enunciado prevê entrega em 03/12/2026 e entrevistas em 04/12/2026. O grupo deve inserir a identificação dos integrantes, registrar suas contribuições reais e submeter o material pela Plataforma A. O CSV bruto, o banco local e as credenciais ficam fora do versionamento. [Repositório público da equipe](https://github.com/zluisx/srag_ia).

O Codex auxiliou na leitura do enunciado e do dicionário, no diagnóstico da base, na implementação e execução do treinamento, no desenvolvimento da API e da interface, nos testes, na documentação e na demonstração. Cada integrante deve revisar o material e explicar as decisões que o grupo apresentar.
