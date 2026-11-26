# Back-end: API de apoio à decisão (risco de UTI em SRAG)

API REST em **Java 17 + Spring Boot 3.3** com JWT, dois perfis (operador e gestor/auditor), banco relacional,
histórico de predições e Swagger. A IA entra por uma interface (`ModeloClient`), então o back-end funciona
antes de o modelo existir.

## Como rodar

Requisitos: JDK 17+ e Maven (ou abra a pasta no IntelliJ / VS Code com o Extension Pack for Java).

```bash
cd backend
mvn spring-boot:run
```

- Swagger: http://localhost:8080/swagger-ui.html
- Banco: H2 em arquivo (`./data/srag`), criado sozinho. Para PostgreSQL, defina `DB_URL`, `DB_USER` e `DB_PASSWORD`.
- Usuários de teste (criados se o banco estiver vazio):
  - gestor: `gestor@srag.local` / `gestor12345`
  - operador: `operador@srag.local` / `operador12345`

### Variáveis de ambiente

| Variável | Para quê | Padrão |
|---|---|---|
| `JWT_SECRET` | segredo do JWT (mín. 32 caracteres). **Troque.** | valor de exemplo |
| `JWT_EXPIRACAO_MIN` | validade do token | 60 |
| `MODELO_MODO` | `mock` (IA de mentira) ou `http` (serviço Python) | `mock` |
| `MODELO_URL` | endereço do serviço Python | `http://localhost:8000` |
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | banco | H2 em arquivo |
| `SEED_*` | e-mails e senhas dos usuários iniciais | ver `application.properties` |

## Testando pelo terminal

```bash
# 1. login (guarde o "token" da resposta)
curl -s -X POST localhost:8080/api/auth/login -H "Content-Type: application/json" \
  -d '{"email":"operador@srag.local","senha":"operador12345"}'

# 2. criar um registro
curl -s -X POST localhost:8080/api/registros -H "Authorization: Bearer TOKEN" -H "Content-Type: application/json" \
  -d '{"identificador":"ATEND-0001","dados":{"uf":"PI","faixa_etaria":"70-79","FEBRE":1,"DISPNEIA":1,"SATURACAO":1}}'

# 3. executar a predição (id do registro = 1)
curl -s -X POST localhost:8080/api/registros/1/predicoes -H "Authorization: Bearer TOKEN"

# 4. histórico
curl -s localhost:8080/api/registros/1/predicoes -H "Authorization: Bearer TOKEN"
```

No Swagger: faça o login, copie o token e clique em **Authorize**.

## Endpoints

| Método e caminho | Quem | O que faz |
|---|---|---|
| `POST /api/auth/login` | público | login, devolve o JWT |
| `POST/GET /api/usuarios`, `PATCH /api/usuarios/{id}/ativo` | gestor | cria, lista, ativa/desativa usuários |
| `POST /api/registros` | autenticado | cria registro (dados validados pelo esquema) |
| `GET /api/registros` | autenticado | lista paginada (operador: os seus; gestor: todos) |
| `GET/PUT/DELETE /api/registros/{id}` | dono (GET: também gestor) | consulta, atualiza, exclui |
| `POST /api/registros/{id}/predicoes` | dono | roda o modelo e grava no histórico |
| `GET /api/registros/{id}/predicoes` | dono ou gestor | histórico do registro |
| `GET /api/predicoes/minhas` | autenticado | minhas predições (paginado) |
| `GET /api/predicoes?inicio=&fim=&usuarioId=&faixa=&somenteRevisao=` | gestor | auditoria com filtros |
| `GET /api/predicoes/{id}` | dono ou gestor | uma predição |
| `POST /api/predicoes/{id}/revisao` | dono ou gestor | solicitar revisão humana |
| `GET /api/modelo/versao` | autenticado | versão do modelo em uso |
| `GET /api/modelo/esquema` | autenticado | campos e valores válidos (para o formulário do front) |

Erros seguem sempre o mesmo formato (`timestamp`, `status`, `erro`, `mensagem`, `detalhes`, `caminho`):
400 dados inválidos, 401 sem login, 403 sem permissão, 404 não encontrado, 409 conflito, 503 IA indisponível.

## Estrutura

```
src/main/java/br/edu/unifsa/srag/
├── controller/   endpoints REST
├── service/      regras de negócio
├── repository/   acesso ao banco (Spring Data JPA)
├── domain/       entidades: Usuario, RegistroAnalise, Predicao
├── dto/          objetos de entrada e saída da API
├── security/     JWT, filtro, BCrypt, perfis
├── modelo/       ENCAIXE DA IA: ModeloClient (mock e http) e validação pelo esquema
├── exception/    erros padronizados
└── config/       Swagger e usuários iniciais
servico-ia/       microserviço Python que carrega o modelo.joblib
```

Entidades: **Usuario** 1—N **RegistroAnalise** 1—N **Predicao** (e Usuario 1—N Predicao).

## Encaixando a IA (quando a pasta `modelo/` ficar pronta)

1. Copie os arquivos que o notebook 02 gerou (`modelo.joblib`, `metadados.json`, `esquema_entrada.json`,
   `explicador_shap.joblib`) para a pasta **`modelo/` na raiz do repositório**.
2. Substitua `src/main/resources/modelo/esquema_entrada.json` pelo `esquema_entrada.json` novo
   (já há uma cópia gerada com os dados atuais).
3. Suba o serviço Python:
   ```bash
   cd backend/servico-ia
   pip install -r requirements.txt     # scikit-learn na MESMA versão do Colab
   uvicorn app:app --port 8000
   ```
4. Rode o Java com `MODELO_MODO=http`:
   ```bash
   MODELO_MODO=http mvn spring-boot:run      # Windows (PowerShell): $env:MODELO_MODO="http"; mvn spring-boot:run
   ```
5. Confira em `GET /api/modelo/versao` que a versão é a do modelo real (e não `mock-0.0.0`).

**Contrato** entre o Java e o Python: `POST /predict` com `{"dados": {...}}` devolve
`{"probabilidade", "faixa", "fatores", "versao_modelo"}`; `GET /versao` devolve os metadados.

## Atenção

- **Campos vazios:** o modelo preenche campos ausentes com o valor mais comum, e para sintomas isso costuma ser
  "sim". Peça ao front para enviar **todos** os sintomas e comorbidades como 0 ou 1.
- O `identificador` do registro é um código interno. **Nunca** use CPF, nome ou outro dado que identifique o paciente.
- Não suba para o GitHub: `data/`, `target/` e segredos reais (já estão no `.gitignore`).
- Se o grupo for usar outro banco ou publicar o sistema, troque as senhas de teste e o `JWT_SECRET`.

## O que ainda dá para acrescentar (nível intermediário, opcional)

Refresh token, exclusão lógica, auditoria de operações, dashboard, exportação CSV/PDF, rate limiting, cache e testes
automatizados. Paginação, filtros e gestão de usuários já estão incluídos.
