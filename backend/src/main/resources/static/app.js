"use strict";
const $ = (q, root = document) => root.querySelector(q);
const main = $("#conteudo");
let sessao, meta, pagina, registro, timer;
const pct = (n) =>
  (n * 100).toLocaleString("pt-BR", { maximumFractionDigits: 1 }) + "%";
function aviso(texto) {
  $("#aviso").textContent = texto;
  clearTimeout(timer);
  timer = setTimeout(() => ($("#aviso").textContent = ""), 6000);
}
async function api(path, method = "GET", body) {
  const headers = { "Content-Type": "application/json" };
  if (sessao) headers.Authorization = "Bearer " + sessao.token;
  const r = await fetch("/api/" + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  if (!r.ok) {
    const erro = await r.json().catch(() => ({}));
    if (r.status === 401 && sessao) sair();
    throw new Error(
      erro.detail ||
        (r.status === 401
          ? "Sessão ou credenciais inválidas."
          : "A solicitação falhou (" + r.status + ")."),
    );
  }
  return r.status === 204 ? null : r.json();
}
function executar(fn) {
  return async (event) => {
    event?.preventDefault();
    const controle = event?.submitter || event?.currentTarget;
    if (controle instanceof HTMLButtonElement) controle.disabled = true;
    try {
      await fn(event);
    } catch (e) {
      aviso(e.message);
    } finally {
      if (controle instanceof HTMLButtonElement) controle.disabled = false;
    }
  };
}
function el(tag, texto, classe) {
  const n = document.createElement(tag);
  if (texto !== undefined) n.textContent = texto;
  if (classe) n.className = classe;
  return n;
}
function botao(texto, fn, classe = "sec") {
  const b = el("button", texto, classe);
  b.type = "button";
  b.onclick = executar(fn);
  return b;
}
function template(id) {
  main.replaceChildren($("#" + id).content.cloneNode(true));
}
function sair() {
  sessao = meta = registro = null;
  $("#menu").hidden = true;
  $("#identidade").replaceChildren();
  template("login");
  $("#entrar").onsubmit = executar(async (e) => {
    const login = await api(
      "login",
      "POST",
      Object.fromEntries(new FormData(e.target)),
    );
    sessao = login;
    meta = await api("modelo");
    $("#identidade").replaceChildren(
      el("span", login.email),
      botao("Sair", sair, ""),
    );
    await navegar(login.perfil === "GESTOR" ? "Auditoria" : "Nova análise");
  });
}
function navegar(nome) {
  pagina = nome;
  const nav = $("#menu");
  nav.hidden = false;
  nav.replaceChildren();
  const itens = [
    "Nova análise",
    "Meus registros",
    "Histórico",
    ...(sessao.perfil === "GESTOR" ? ["Auditoria", "Usuários"] : []),
    "Transparência",
    "Privacidade",
  ];
  itens.forEach((item) => {
    const b = botao(item, () => navegar(item), "");
    if (item === nome) b.setAttribute("aria-current", "page");
    nav.append(b);
  });
  if (nome === "Nova análise") return ficha();
  if (nome === "Meus registros") return listarCasos();
  if (nome === "Histórico") return historico();
  if (nome === "Auditoria") return auditoria();
  if (nome === "Usuários") return usuarios();
  if (nome === "Transparência") return transparencia();
  privacidade();
}
function ficha(caso) {
  registro = caso?.id || null;
  template("ficha");
  if (registro) $("#tituloFicha").textContent = "Editar registro " + registro;
  meta.idades.forEach((nome, i) =>
    $("#idade").add(new Option(nome, String(i))),
  );
  if (caso) $("#idade").value = caso.entrada.idade;
  meta.ordem.forEach((campo) => {
    const label = el("label", meta.sinais[campo], "sinal"),
      s = el("select");
    s.name = campo;
    s.setAttribute("aria-label", meta.sinais[campo]);
    for (const [v, t] of Object.entries(meta.dominio_sinais))
      s.add(new Option(t, v));
    s.value = caso?.entrada.sinais[campo] || "9";
    label.append(s);
    $("#sinais").append(label);
  });
  const form = $("#formCaso");
  async function salvar(analisar) {
    if (!form.reportValidity()) return;
    const sinais = Object.fromEntries(new FormData(form));
    if (Object.values(sinais).every((v) => v === "9"))
      throw Error("Informe ao menos um sinal conhecido.");
    const entrada = { idade: Number($("#idade").value), sinais };
    const c = await api(
      registro ? "casos/" + registro : "casos",
      registro ? "PUT" : "POST",
      entrada,
    );
    registro = c.id;
    if (analisar)
      mostrarResultado(await api("casos/" + registro + "/predicoes", "POST"));
    else {
      await navegar("Meus registros");
      aviso("Registro salvo.");
    }
  }
  form.onsubmit = executar(() => salvar(true));
  $("#salvar").onclick = executar(() => salvar(false));
}
function titulo(nome, descricao) {
  main.replaceChildren(el("h1", nome));
  if (descricao) main.append(el("p", descricao));
}
function painel(titulo, pai = main) {
  const n = el("section", undefined, "painel");
  if (titulo) n.append(el("h2", titulo));
  pai.append(n);
  return n;
}
function metricas(pai, pares) {
  const box = el("div", undefined, "metricas");
  pai.append(box);
  for (const [v, t] of pares) {
    const n = el("div", undefined, "metrica");
    n.append(el("strong", v, "numero"), el("span", t));
    box.append(n);
  }
}
function mostrarResultado(p) {
  titulo(
    "Resultado da análise",
    "Registro " + p.caso + " · " + new Date(p.instante).toLocaleString("pt-BR"),
  );
  const r = p.resultado,
    box = painel("Internação posterior em UTI");
  box.classList.add("resultado");
  metricas(box, [
    [pct(r.probabilidade), "Probabilidade estimada"],
    [r.faixa, "Faixa de risco"],
  ]);
  const fatores = painel("Fatores da estimativa");
  fatores.append(
    el(
      "p",
      "Variação em pontos percentuais ao comparar cada resposta com a categoria mais frequente no treino, mantendo as outras respostas.",
    ),
  );
  const lista = el("ul", undefined, "listaFatores");
  fatores.append(lista);
  for (const f of r.fatores) {
    const idade = f.campo === "idade",
      rotulo = idade ? "Faixa etária" : meta.sinais[f.campo];
    const ref = idade
      ? meta.idades[Number(f.referencia)]
      : meta.dominio_sinais[f.referencia];
    const valor = idade
      ? meta.idades[p.entrada.idade]
      : meta.dominio_sinais[p.entrada.sinais[f.campo]];
    lista.append(
      el(
        "li",
        rotulo +
          " · " +
          valor +
          " (referência: " +
          ref +
          ") · " +
          (f.diferenca > 0 ? "+" : "") +
          (100 * f.diferenca).toLocaleString("pt-BR", {
            maximumFractionDigits: 2,
          }) +
          " p.p.",
      ),
    );
  }
  if (!r.fatores.length)
    fatores.append(
      el("p", "Os valores coincidem com as referências do modelo."),
    );
  main.append(
    el(
      "p",
      "Apoio acadêmico à decisão. A avaliação do caso e a decisão sobre leitos cabem à equipe responsável.",
    ),
  );
  if (p.revisao) main.append(el("b", "Revisão humana solicitada"));
  else if (p.usuario === sessao.email)
    main.append(
      botao("Solicitar revisão humana", async () => {
        await api("predicoes/" + p.id + "/revisao", "PUT");
        mostrarResultado({ ...p, revisao: true });
      }),
    );
  main.append(el("p", "Versão " + r.versao, "legenda"));
}
async function listarCasos() {
  titulo("Meus registros");
  const rows = await api("casos");
  if (pagina !== "Meus registros") return;
  const box = painel("Admissões cadastradas");
  if (!rows.length) box.append(el("p", "Nenhum registro cadastrado."));
  rows.forEach((c) => {
    const linha = el("div", undefined, "linhaCaso"),
      a = el("div", undefined, "acoes");
    linha.append(
      el("b", "Registro " + c.id + " · " + meta.idades[c.entrada.idade]),
      a,
    );
    a.append(
      botao("Editar", () => ficha(c)),
      botao("Analisar", async () =>
        mostrarResultado(await api("casos/" + c.id + "/predicoes", "POST")),
      ),
      botao("Arquivar", async () => {
        if (confirm("Arquivar este registro e preservar as predições?")) {
          await api("casos/" + c.id, "DELETE");
          await listarCasos();
        }
      }),
    );
    box.append(linha);
  });
}
function tabela(rows, pai) {
  pai.replaceChildren();
  if (!rows.length) {
    pai.append(el("p", "Nenhuma predição encontrada."));
    return;
  }
  const wrapper = el("div", undefined, "tabela"),
    t = el("table"),
    head = t.createTHead().insertRow();
  ["Registro", "Data e hora", "Usuário", "Risco", "Revisão", ""].forEach((s) =>
    head.append(el("th", s)),
  );
  const body = t.createTBody();
  rows.forEach((r) => {
    const tr = body.insertRow();
    [
      r.caso,
      new Date(r.instante).toLocaleString("pt-BR"),
      r.usuario,
      r.resultado.faixa,
      r.revisao ? "Solicitada" : "—",
    ].forEach((s) => tr.append(el("td", s)));
    const td = tr.insertCell();
    td.append(botao("Detalhes", () => mostrarResultado(r)));
  });
  wrapper.append(t);
  pai.append(wrapper);
}
async function historico() {
  titulo("Histórico", "Predições realizadas pela sua conta.");
  const box = painel();
  tabela(await api("predicoes"), box);
}
async function auditoria() {
  template("auditoria");
  const form = $(".filtros"),
    box = $("#lista");
  const buscar = async () => {
    const p = new URLSearchParams();
    new FormData(form).forEach((v, k) => {
      if (v) p.set(k, v);
    });
    tabela(await api("auditoria?" + p), box);
  };
  form.onsubmit = executar(buscar);
  await buscar();
}
const PERFIS = { OPERADOR: "Operador", GESTOR: "Gestor/auditor" };
async function usuarios() {
  template("usuarios");
  const form = $("#formUsuario"),
    box = $("#listaUsuarios");
  const carregar = async () => {
    const rows = await api("usuarios");
    if (pagina !== "Usuários") return;
    box.replaceChildren();
    const wrapper = el("div", undefined, "tabela"),
      t = el("table"),
      head = t.createTHead().insertRow();
    ["E-mail", "Perfil", "Situação", ""].forEach((s) => head.append(el("th", s)));
    const body = t.createTBody();
    rows.forEach((u) => {
      const tr = body.insertRow(),
        propria = u.email === sessao.email;
      [u.email, PERFIS[u.perfil], u.ativo ? "Ativo" : "Desativado"].forEach((s) =>
        tr.append(el("td", s)),
      );
      const td = tr.insertCell();
      if (propria) {
        td.append(el("span", "Sua conta", "legenda"));
        return;
      }
      const outro = u.perfil === "GESTOR" ? "OPERADOR" : "GESTOR";
      td.append(
        botao(u.ativo ? "Desativar" : "Ativar", async () => {
          await api("usuarios/" + u.id + "/ativo", "PUT", { ativo: !u.ativo });
          await carregar();
        }),
        botao("Tornar " + PERFIS[outro].toLowerCase(), async () => {
          await api("usuarios/" + u.id + "/perfil", "PUT", { perfil: outro });
          await carregar();
        }),
      );
    });
    wrapper.append(t);
    box.append(wrapper);
  };
  form.onsubmit = executar(async () => {
    await api("usuarios", "POST", Object.fromEntries(new FormData(form)));
    form.reset();
    aviso("Usuário criado.");
    await carregar();
  });
  await carregar();
}
function transparencia() {
  titulo(
    "Transparência",
    "Internação em UTI após o dia da admissão hospitalar por SRAG.",
  );
  let b = painel("Desempenho no teste reservado"),
    t = meta.teste;
  metricas(b, [
    [pct(t.sensibilidade), "Sensibilidade"],
    [pct(t.precisao), "Precisão dos alertas"],
    [String(meta.n_teste), "Notificações no teste"],
  ]);
  b.append(
    el(
      "p",
      "O modelo detectou " +
        t.vp +
        " internações em UTI, deixou de sinalizar " +
        t.fn +
        " e gerou " +
        t.fp +
        " falsos alertas.",
    ),
  );
  b = painel("População e limites");
  b.append(
    el(
      "p",
      "Notificações do Nordeste com internação em 2025, idade e sinais disponíveis na admissão. Casos que chegaram à UTI no mesmo dia foram excluídos porque a base registra datas sem horário.",
    ),
    el(
      "p",
      "O desfecho depende da oferta de leitos. O desempenho é retrospectivo, com poucos casos positivos. O uso se limita à demonstração acadêmica.",
    ),
    el(
      "p",
      meta.modelo +
        " · Faixa média a partir de " +
        pct(meta.limiar) +
        " e alta a partir de " +
        pct(meta.alto) +
        ".",
    ),
    el("p", "Versão " + meta.versao, "legenda"),
  );
}
function privacidade() {
  titulo("Privacidade");
  const b = painel("Dados e finalidade");
  b.append(
    el(
      "p",
      "O demonstrador registra faixa etária, seis sinais, estimativa, fatores explicativos, versão, conta solicitante e horário. Use casos fictícios nas demonstrações.",
    ),
    el(
      "p",
      "O operador acessa seus registros. O gestor consulta o histórico geral para auditar resultados e pedidos de revisão.",
    ),
    el(
      "p",
      "Os registros de demonstração são mantidos durante a avaliação acadêmica. A equipe responsável deve eliminar o banco e suas cópias até 30 dias após a avaliação final.",
    ),
    el(
      "p",
      "Para solicitar revisão, abra o resultado e use o botão de revisão humana. O pedido aparece na auditoria do gestor.",
    ),
  );
}
sair();
