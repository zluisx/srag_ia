"""Transformações usadas também ao carregar o pipeline serializado."""
import numpy as np
import pandas as pd

SINAIS = {
    "DISPNEIA": "Dispneia",
    "DESC_RESP": "Desconforto respiratório",
    "SATURACAO": "Saturação de O₂ abaixo de 95%",
    "FEBRE": "Febre",
    "TOSSE": "Tosse",
    "FADIGA": "Fadiga",
}
IDADES = ["Menos de 1 ano", "1 a 4 anos", "5 a 17 anos", "18 a 39 anos",
          "40 a 59 anos", "60 a 79 anos", "80 anos ou mais"]
REPRESENTANTES = [0, 1, 5, 18, 40, 60, 80]
ENTRADAS = ["NU_IDADE_N", "TP_IDADE", *SINAIS]


def categorizar(dados):
    idade = pd.to_numeric(dados["NU_IDADE_N"], errors="raise")
    unidade = dados["TP_IDADE"].astype(str)
    limites = unidade.map({"1": 30, "2": 11, "3": 150})
    if (limites.isna() | idade.isna() | (idade % 1 != 0) | (idade < 0) | (idade > limites)).any():
        raise ValueError("Idade ou unidade incompatível com o dicionário.")
    anos = idade.where(unidade == "3", 0)
    saida = pd.DataFrame({"idade": np.digitize(anos, REPRESENTANTES[1:]).astype(str)}, index=dados.index)
    for campo in SINAIS:
        valor = dados[campo].fillna("9").replace("", "9").astype(str)
        if not valor.isin(["1", "2", "9"]).all():
            raise ValueError(f"Código de sinal inválido em {campo}.")
        saida[campo] = valor
    return saida
