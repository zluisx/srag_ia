"""
Serviço de IA: carrega o modelo treinado (modelo.joblib) e responde ao back-end Java.

Rodar (dentro da pasta servico-ia):
    pip install -r requirements.txt
    uvicorn app:app --port 8000

A pasta com os arquivos do modelo (modelo.joblib, metadados.json, esquema_entrada.json,
explicador_shap.joblib) é, por padrão, a pasta "modelo/" na raiz do repositório.
Para usar outra: defina a variável de ambiente MODELO_DIR.
"""
import json
import os
from pathlib import Path
from typing import Any, Dict

import joblib
import numpy as np
import pandas as pd
from fastapi import FastAPI
from pydantic import BaseModel

PASTA = Path(os.environ.get("MODELO_DIR", Path(__file__).resolve().parents[2] / "modelo"))

modelo = joblib.load(PASTA / "modelo.joblib")
meta = json.loads((PASTA / "metadados.json").read_text(encoding="utf-8"))
esquema = json.loads((PASTA / "esquema_entrada.json").read_text(encoding="utf-8"))

try:
    explicador = joblib.load(PASTA / "explicador_shap.joblib")
except Exception as e:  # sem explicador o serviço ainda prevê, só não explica
    print("Aviso: explicador SHAP não carregado:", e)
    explicador = None

COLUNAS = meta["atributos_esperados"]
LIMIAR = meta["limiar_decisao"]
LIMIAR_ALTO = meta["limiar_risco_alto"]
# colunas categóricas de texto (as demais são numéricas)
COLUNAS_TEXTO = {c for c, r in esquema.items()
                 if r["tipo"] == "categorico" and any(isinstance(v, str) for v in r["valores_validos"])}

pre = modelo.named_steps["pre"]
NOMES_ATRIBUTOS = pre.get_feature_names_out()

app = FastAPI(title="Serviço de IA - risco de UTI em SRAG")


class Entrada(BaseModel):
    dados: Dict[str, Any]


def montar_dataframe(dados: Dict[str, Any]) -> pd.DataFrame:
    df = pd.DataFrame([dados]).reindex(columns=COLUNAS)   # campos ausentes viram vazio
    for c in COLUNAS:
        if c in COLUNAS_TEXTO:
            df[c] = df[c].astype(object).where(df[c].notna(), np.nan)
        else:
            df[c] = pd.to_numeric(df[c], errors="coerce")
    return df


def faixa_de_risco(p: float) -> str:
    return "baixo" if p < LIMIAR else ("médio" if p < LIMIAR_ALTO else "alto")


def explicar(df: pd.DataFrame, top: int = 5):
    if explicador is None:
        return []
    sv = explicador.shap_values(pre.transform(df))
    if isinstance(sv, list):
        sv = sv[1]
    sv = np.asarray(sv)
    if sv.ndim == 3:
        sv = sv[:, :, 1]
    contrib = sv[0]
    idx = np.argsort(-np.abs(contrib))[:top]
    return [{"fator": str(NOMES_ATRIBUTOS[i]),
             "efeito": "aumenta o risco" if contrib[i] > 0 else "reduz o risco",
             "contribuicao": round(float(contrib[i]), 4)} for i in idx]


@app.get("/versao")
def versao():
    return meta


@app.post("/predict")
def predict(entrada: Entrada):
    df = montar_dataframe(entrada.dados)
    p = float(modelo.predict_proba(df)[0, 1])
    return {
        "probabilidade": round(p, 4),
        "faixa": faixa_de_risco(p),
        "fatores": explicar(df),
        "versao_modelo": meta["versao_modelo"],
    }
