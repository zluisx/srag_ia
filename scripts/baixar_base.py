"""Baixa a edição do CSV usada no treino e confere a integridade antes de disponibilizá-la."""
import argparse
import hashlib
import shutil
import tempfile
from pathlib import Path
from urllib.request import urlopen

URL = "https://s3.sa-east-1.amazonaws.com/ckan.saude.gov.br/SRAG/2025/INFLUD25-23-11-2026.csv"
SHA256 = "1c1acac956c5dc9a952baf99fc646b72cd3f6a3e8551d7ac973d6c333ae54648"


def verificar(caminho):
    with caminho.open("rb") as entrada:
        if hashlib.file_digest(entrada, "sha256").hexdigest() != SHA256:
            raise ValueError("O conteúdo difere do extrato da entrega. Confira a versão na fonte.")


def baixar(pasta):
    pasta.mkdir(parents=True, exist_ok=True)
    destino = pasta / URL.rsplit("/", 1)[1]
    if destino.exists():
        verificar(destino)
        return destino
    with tempfile.NamedTemporaryFile(dir=pasta, delete=False) as arquivo:
        temporario = Path(arquivo.name)
    try:
        with urlopen(URL, timeout=90) as resposta, temporario.open("wb") as saida:
            shutil.copyfileobj(resposta, saida)
        verificar(temporario)
        temporario.replace(destino)
    finally:
        temporario.unlink(missing_ok=True)
    return destino


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("pasta", type=Path)
    print(baixar(parser.parse_args().pasta))
