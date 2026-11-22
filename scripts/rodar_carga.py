"""Executa a matriz k6 local e registra cada rodada em uma nova pasta."""
import argparse
import csv
import json
import math
import os
import platform
import shutil
import subprocess
from datetime import datetime
from pathlib import Path
from urllib.parse import urlsplit
from urllib.request import Request, urlopen

RAIZ = Path(__file__).resolve().parents[1]
MATRIZ = {
    "cache": ("sem-cache", "frio", "quente", "ttl-curto"),
    "persistencia": ("lazy", "eager", "join-fetch"),
    "indice": ("sem-indice", "com-indice"),
}
CAMPOS = (
    "cenario", "variante", "vus", "repeticao", "duracao_s", "janela_s", "requisicoes",
    "vazao_rps", "media_ms", "p95_ms", "p99_ms", "erro_pct", "hits", "misses",
    "hit_media_ms", "miss_media_ms",
)


def api(base, rota, metodo="GET", corpo=None):
    dados = json.dumps(corpo).encode() if corpo is not None else None
    requisicao = Request(base + "/api/" + rota, data=dados, method=metodo,
                         headers={"Content-Type": "application/json"})
    with urlopen(requisicao, timeout=20) as resposta:
        return json.load(resposta), resposta.headers


def salvar_json(caminho, valor):
    caminho.write_text(json.dumps(valor, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def base_local(valor):
    url = urlsplit(valor)
    if (url.scheme != "http" or url.hostname not in {"127.0.0.1", "localhost", "::1"}
            or url.path not in {"", "/"} or url.username or url.query or url.fragment):
        raise argparse.ArgumentTypeError("Informe a origem HTTP da API local.")
    return valor.rstrip("/")


def argumentos():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", type=base_local, default="http://127.0.0.1:8080")
    parser.add_argument("--k6", default=shutil.which("k6"))
    parser.add_argument("--duracao", type=int, default=12)
    parser.add_argument("--repeticoes", type=int, default=2)
    parser.add_argument("--vus", type=int, nargs="+", default=[10, 50, 100, 200])
    parser.add_argument("--limite", type=int, default=30)
    parser.add_argument("--cenario", choices=MATRIZ)
    parser.add_argument("--saida", type=Path)
    args = parser.parse_args()
    if not args.k6:
        parser.error("Instale o k6 ou informe seu executável em --k6.")
    if not 1 <= args.duracao <= 3600 or args.repeticoes < 1 or not 1 <= args.limite <= 2000:
        parser.error("Informe duração de 1 a 3600 segundos, ao menos uma repetição e limite de 1 a 2000.")
    if any(v < 1 for v in args.vus) or len(set(args.vus)) != len(args.vus):
        parser.error("Os níveis de VUs devem ser positivos e distintos.")
    return args


def ordem_rodadas(cenarios, cargas, repeticoes):
    for rep in range(1, repeticoes + 1):
        for cenario in cenarios:
            variantes = MATRIZ[cenario]
            for vus in cargas:
                ordem = variantes if rep % 2 else reversed(variantes)
                for variante in ordem:
                    yield cenario, variante, vus, rep


def rodar(args, pasta, volume, caso):
    cenario, variante, vus, rep = caso
    nome = f"{cenario}_{variante}_{vus}vu_{rep}"
    caminho = pasta / "rodadas" / f"{nome}.json"
    env = {chave: valor for chave, valor in os.environ.items() if not chave.upper().startswith("K6_")}
    env.update({
        "K6_NO_USAGE_REPORT": "true",
        "BASE_URL": args.base_url,
        "CENARIO": cenario,
        "VARIANTE": variante,
        "VUS": str(vus),
        "REPETICAO": str(rep),
        "DURACAO_S": str(args.duracao),
        "LIMITE": str(args.limite),
        "CLIENTES": str(volume["cliente"]),
        "SUMARIO": caminho.as_posix(),
    })
    with (pasta / "rodadas" / f"{nome}.log").open("w", encoding="utf-8") as log:
        resultado = subprocess.run([args.k6, "run", "--quiet", "k6/teste.js"], cwd=RAIZ,
                                   env=env, stdout=log, stderr=subprocess.STDOUT,
                                   timeout=args.duracao + 90)
    if resultado.returncode != 0 or not caminho.is_file():
        raise RuntimeError(f"k6 falhou em {nome}. Consulte rodadas/{nome}.log.")
    linha = json.loads(caminho.read_text(encoding="utf-8"))
    identidade = tuple(linha[c] for c in ("cenario", "variante", "vus", "repeticao"))
    if identidade != caso or linha["requisicoes"] < 1 or linha["duracao_s"] != args.duracao:
        raise RuntimeError(f"Sumário incompatível com a rodada {nome}.")
    for campo in ("janela_s", "vazao_rps", "media_ms", "p95_ms", "p99_ms", "erro_pct"):
        if not isinstance(linha.get(campo), (int, float)) or not math.isfinite(linha[campo]):
            raise RuntimeError(f"Métrica {campo} ausente ou inválida em {nome}.")
    return linha


def main():
    args = argumentos()
    cenarios = [args.cenario] if args.cenario else list(MATRIZ)
    volume, _ = api(args.base_url, "admin/contagens")
    if volume["categoria"] != 20 or volume["cliente"] < 1 or volume["pedido"] < max(10000, args.limite):
        raise RuntimeError("O banco deve estar populado com 20 categorias e ao menos 10.000 pedidos.")
    ambiente, _ = api(args.base_url, "admin/ambiente")
    versao = subprocess.check_output([args.k6, "version"], encoding="utf-8").strip()
    inicio = datetime.now().astimezone()
    pasta = (args.saida or RAIZ / "resultados" / inicio.strftime("%Y%m%d-%H%M%S")).resolve()
    pasta.mkdir(parents=True, exist_ok=False)
    (pasta / "rodadas").mkdir()
    casos = list(ordem_rodadas(cenarios, sorted(args.vus), args.repeticoes))
    salvar_json(pasta / "execucao.json", {
        "inicio": inicio.isoformat(timespec="seconds"),
        "sistema": platform.system(),
        "versaoSistema": platform.version(),
        "cpusLogicas": os.cpu_count(),
        "k6": versao,
        "api": ambiente,
        "volume": volume,
        "cargas": sorted(args.vus),
        "duracao_s": args.duracao,
        "repeticoes": args.repeticoes,
        "limite": args.limite,
        "cenarios": cenarios,
        "rodadas_esperadas": len(casos),
    })
    linhas = []
    try:
        for i, caso in enumerate(casos, 1):
            linha = rodar(args, pasta, volume, caso)
            linhas.append(linha)
            print(f"[{i}/{len(casos)}] {caso[0]}/{caso[1]}, {caso[2]} VUs, rep {caso[3]} | "
                  f"{linha['requisicoes']} req, p95 {linha['p95_ms']:.2f} ms, {linha['erro_pct']:.2f}% erros",
                  flush=True)
    finally:
        if "indice" in cenarios:
            api(args.base_url, "admin/indice", "DELETE")
    temporario = pasta / "consolidado.tmp"
    with temporario.open("w", encoding="utf-8", newline="") as arquivo:
        gravador = csv.DictWriter(arquivo, fieldnames=CAMPOS)
        gravador.writeheader()
        gravador.writerows(linhas)
    temporario.replace(pasta / "consolidado.csv")
    print(f"{len(linhas)} rodadas concluídas. {pasta}")


if __name__ == "__main__":
    main()
