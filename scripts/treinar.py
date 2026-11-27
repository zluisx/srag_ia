"""Treino reproduzível: python -m scripts.treinar caminho/do/arquivo.csv"""
import argparse
import hashlib
import itertools
import json
from datetime import datetime, timezone
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.compose import ColumnTransformer
from sklearn.dummy import DummyClassifier
from sklearn.inspection import permutation_importance
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import (average_precision_score, confusion_matrix, precision_recall_curve,
                             recall_score, precision_score, fbeta_score, roc_auc_score, brier_score_loss)
from sklearn.model_selection import StratifiedKFold, cross_val_predict, train_test_split
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import FunctionTransformer, OneHotEncoder
from sklearn.tree import DecisionTreeClassifier

from scripts.atributos import ENTRADAS, IDADES, REPRESENTANTES, SINAIS, categorizar
from scripts.perfil_base import NORDESTE

RAIZ = Path(__file__).resolve().parents[1]
DOCS = RAIZ / "docs"
RECURSOS = RAIZ / "backend/src/main/resources/modelo"
CONTROLE = ["NU_NOTIFIC", "SG_UF_NOT", "HOSPITAL", "UTI", "DT_INTERNA", "DT_ENTUTI",
            "DT_NOTIFIC", "DT_SIN_PRI"]
SEMENTE = 42


def gravar(destino, conteudo):
    destino.write_text(json.dumps(conteudo, indent=2, ensure_ascii=False, allow_nan=False) + "\n", encoding="utf-8")


def extrair(caminho):
    colunas = pd.read_csv(caminho, sep=";", nrows=0).columns.tolist()
    blocos = pd.read_csv(caminho, sep=";", dtype=str, usecols=CONTROLE + ENTRADAS, chunksize=30000)
    dados = pd.concat([b[b.SG_UF_NOT.isin(NORDESTE)] for b in blocos], ignore_index=True)
    fluxo = {"Notificações no Nordeste": len(dados)}
    faltantes = {c: round(100 * dados[c].isna().mean(), 3) for c in CONTROLE + ENTRADAS}

    def filtrar(regra, titulo):
        nonlocal dados
        dados = dados.loc[regra].copy()
        fluxo[titulo] = len(dados)

    filtrar(dados.NU_NOTIFIC.notna() & ~dados.NU_NOTIFIC.duplicated(keep=False), "Identificador único e presente")
    filtrar(dados.HOSPITAL.eq("1"), "Hospitalização confirmada")
    filtrar(dados.UTI.isin(["1", "2"]), "Alvo conhecido")
    uti_informada = dados.DT_ENTUTI.notna()
    for c in ["DT_INTERNA", "DT_SIN_PRI", "DT_NOTIFIC", "DT_ENTUTI"]:
        dados[c] = pd.to_datetime(dados[c], errors="coerce", utc=True, format="ISO8601")
    filtrar(dados.DT_INTERNA.dt.year.eq(2025), "Admissão em 2025")
    filtrar((dados.DT_SIN_PRI <= dados.DT_NOTIFIC) & (dados.DT_NOTIFIC <= dados.DT_INTERNA),
            "Sintomas e notificação até a admissão")
    sem_uti = dados.UTI.eq("2") & ~uti_informada.reindex(dados.index)
    uti_depois = dados.UTI.eq("1") & (dados.DT_ENTUTI > dados.DT_INTERNA) & (dados.DT_ENTUTI <= "2026-11-23")
    filtrar(sem_uti | uti_depois, "Sem UTI ou com entrada posterior ao dia da admissão")
    idade = pd.to_numeric(dados.NU_IDADE_N, errors="coerce")
    limite = dados.TP_IDADE.map({"1": 30, "2": 11, "3": 150})
    filtrar(idade.between(0, limite) & idade.mod(1).eq(0), "Idade válida e unidade conhecida")
    sintomas = dados[list(SINAIS)].fillna("9")
    filtrar(sintomas.isin(["1", "2", "9"]).all(axis=1) & sintomas.isin(["1", "2"]).any(axis=1),
            "Sinais válidos e ao menos um conhecido")
    gravar(DOCS / "qualidade.json", {"registros_apos_cada_regra": fluxo,
           "percentual_em_branco_antes_nordeste": faltantes,
           "percentual_em_branco_apos": {c: round(100 * dados[c].isna().mean(), 3) for c in faltantes},
           "percentual_ignorado_ou_vazio_apos": {c: round(100 * dados[c].fillna("9").eq("9").mean(), 3) for c in SINAIS}})
    regras = {
        "UTI": "Rótulo da resposta.", "NU_NOTIFIC": "Identificador usado apenas para deduplicação.",
        "DT_ENTUTI": "Data do próprio desfecho, usada apenas na definição da coorte.",
        "DT_SAIDUTI": "Só existe após o desfecho.", "EVOLUCAO": "Resultado da internação.",
        "DT_EVOLUCA": "Data do desfecho clínico.", "DT_ENCERRA": "Encerramento da investigação.",
        "SUPORT_VEN": "Suporte pode iniciar após a admissão.", "CLASSI_FIN": "Classificação final retrospectiva.",
        "CRITERIO": "Critério de confirmação retrospectiva.", "DT_NASC": "Identificador indireto; idade agrupada basta.",
    }
    linhas = ["campo\tuso_ou_motivo"]
    for c in colunas:
        motivo = regras.get(c)
        if not motivo:
            if c in ENTRADAS:
                motivo = "Entrada do pipeline."
            elif c in CONTROLE:
                motivo = "Seleção da coorte ou verificação de cronologia."
            else:
                motivo = "Fora do conjunto mínimo de idade e sinais na admissão."
        linhas.append(f"{c}\t{motivo}")
    (DOCS / "destino-colunas.tsv").write_text("\n".join(linhas) + "\n", encoding="utf-8")
    return dados[ENTRADAS], dados.UTI.eq("1").astype(int), fluxo


def treinar(caminho):
    DOCS.mkdir(exist_ok=True)
    RECURSOS.mkdir(parents=True, exist_ok=True)
    x, y, fluxo = extrair(caminho)
    xt, xv, yt, yv = train_test_split(x, y, test_size=.2, random_state=SEMENTE, stratify=y)
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEMENTE)
    modelos = {"Referência": DummyClassifier(strategy="prior"),
               "Regressão logística": LogisticRegression(max_iter=500),
               "Árvore de decisão": DecisionTreeClassifier(max_depth=5, min_samples_leaf=60, random_state=SEMENTE)}
    candidatos = {}
    for nome, clf in modelos.items():
        prep = ColumnTransformer([("categorias", OneHotEncoder(handle_unknown="ignore"), ["idade", *SINAIS])])
        pipe = Pipeline([("dominio", FunctionTransformer(categorizar)), ("preparo", prep), ("classificador", clf)])
        p = cross_val_predict(pipe, xt, yt, cv=cv, method="predict_proba")[:, 1]
        ap = float(np.mean([average_precision_score(yt.iloc[i], p[i]) for _, i in cv.split(xt, yt)]))
        candidatos[nome] = (ap, pipe, p)
    nome = max((k for k in modelos if k != "Referência"), key=lambda k: candidatos[k][0])
    _, modelo, prob = candidatos[nome]
    prec, rec, cortes = precision_recall_curve(yt, prob)
    f2 = 5 * prec[:-1] * rec[:-1] / np.maximum(4 * prec[:-1] + rec[:-1], 1e-12)
    limiar = float(cortes[np.argmax(f2)])
    alto = float(np.quantile(prob[prob >= limiar], .75))
    modelo.fit(xt, yt)
    teste = modelo.predict_proba(xv)[:, 1]
    classes = teste >= limiar
    tn, fp, fn, tp = confusion_matrix(yv, classes, labels=[0, 1]).ravel().tolist()
    # Importância conjunta da idade agrupada: permutar o grupo evita separar unidade e valor.
    agrupado = categorizar(xt)
    importancia = permutation_importance(modelo[1:], agrupado, yt, scoring="average_precision",
                                        n_repeats=5, random_state=SEMENTE)
    dominios = [range(len(IDADES)), *[("1", "2", "9") for _ in SINAIS]]
    chaves = list(itertools.product(*dominios))
    exemplos = pd.DataFrame([[str(REPRESENTANTES[a]), "3", *s] for a, *s in chaves], columns=ENTRADAS)
    scores = modelo.predict_proba(exemplos)[:, 1]
    tabela = {"".join(map(str, k)): float(p) for k, p in zip(chaves, scores)}
    assert np.allclose(teste, [tabela["".join(row)] for row in categorizar(xv).itertuples(index=False, name=None)])
    with caminho.open("rb") as arquivo:
        origem = hashlib.file_digest(arquivo, "sha256").hexdigest()
    versao = "uti-" + hashlib.sha256(json.dumps([origem,tabela,limiar,alto]).encode()).hexdigest()[:12]
    meta = {"versao": versao, "treino": datetime.now(timezone.utc).isoformat(), "arquivo": caminho.name,
            "sha256": origem, "modelo": nome, "coorte": fluxo, "n_treino": len(xt), "n_teste": len(xv),
            "positivos_treino": int(yt.sum()), "positivos_teste": int(yv.sum()),
            "ap_cv": {k: v[0] for k, v in candidatos.items()}, "limiar": limiar, "alto": alto,
            "idades": IDADES, "sinais": SINAIS, "ordem": list(SINAIS),
            "dominio_sinais": {"1": "Sim", "2": "Não", "9": "Ignorado"},
            "importancia_ap": dict(zip(agrupado.columns, importancia.importances_mean.tolist())),
            "referencia": dict(agrupado.mode().iloc[0]),
            "teste": {"sensibilidade": recall_score(yv, classes), "precisao": precision_score(yv, classes, zero_division=0),
                      "f2": fbeta_score(yv, classes, beta=2), "ap": average_precision_score(yv, teste),
                      "roc_auc": roc_auc_score(yv, teste), "brier": brier_score_loss(yv, teste),
                      "vn": tn, "fp": fp, "fn": fn, "vp": tp}}
    gravar(RECURSOS / "metadados.json", meta)
    gravar(RECURSOS / "escores.json", tabela)
    esquema = {"$schema": "https://json-schema.org/draft/2020-12/schema",
               "title": "Ficha de admissão SRAG", "type": "object", "additionalProperties": False,
               "required": ["idade", "sinais"], "properties": {
                   "idade": {"type": "integer", "minimum": 0, "maximum": len(IDADES)-1,
                             "description": "Faixas em ordem: " + ", ".join(IDADES)},
                   "sinais": {"type": "object", "additionalProperties": False, "required": list(SINAIS),
                              "properties": {c: {"type": "string", "description": nome_sinal,
                                                 "enum": ["1", "2", "9"]} for c, nome_sinal in SINAIS.items()},
                              "anyOf": [{"properties": {c: {"enum": ["1", "2"]}}} for c in SINAIS]}}}
    gravar(DOCS / "esquema-entrada.json", esquema)
    pasta = RAIZ / "modelo"
    pasta.mkdir(exist_ok=True)
    joblib.dump(modelo, pasta / "pipeline.joblib", compress=3)
    print(json.dumps(meta, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    treinar(parser.parse_args().csv)
