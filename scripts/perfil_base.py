"""Contabiliza notificações e desfechos por recorte, sem exportar dados individuais."""

import argparse
import csv
from collections import Counter
from pathlib import Path


NORDESTE = {"AL", "BA", "CE", "MA", "PB", "PE", "PI", "RN", "SE"}


def resumir(caminho):
    recortes = {nome: Counter() for nome in ("Brasil", "Nordeste", "Piauí")}
    with caminho.open(encoding="utf-8-sig", newline="") as arquivo:
        leitor = csv.reader(arquivo, delimiter=";", strict=True)
        cabecalho = next(leitor)
        indices = [cabecalho.index(c) for c in ("SG_UF_NOT", "HOSPITAL", "UTI", "EVOLUCAO")]
        for linha in leitor:
            if len(linha) != len(cabecalho):
                raise ValueError(f"Número de campos inesperado na linha {leitor.line_num}")
            uf, hospital, uti, evolucao = (linha[i].strip() for i in indices)
            grupos = [recortes["Brasil"]]
            if uf in NORDESTE:
                grupos.append(recortes["Nordeste"])
            if uf == "PI":
                grupos.append(recortes["Piauí"])
            for grupo in grupos:
                grupo["notificações"] += 1
                if hospital == "1":
                    grupo["hospitalizações"] += 1
                    grupo["UTI " + (uti or "em branco")] += 1
                    grupo["evolução " + (evolucao or "em branco")] += 1
    return len(cabecalho), recortes


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("csv", type=Path)
    colunas, recortes = resumir(parser.parse_args().csv)
    print(f"Colunas no CSV: {colunas}")
    for nome, contagens in recortes.items():
        print(nome)
        for chave, quantidade in sorted(contagens.items()):
            print(f"  {chave}: {quantidade}")
