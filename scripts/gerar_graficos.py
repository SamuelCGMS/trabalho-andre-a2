"""Gera figuras, tabela resumida e painel HTML a partir de uma bateria concluída."""
import argparse
from base64 import b64encode
import csv
import json
from collections import defaultdict
from pathlib import Path
from statistics import median

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib.ticker import FuncFormatter

from rodar_carga import MATRIZ

CENARIOS = {"cache": "Cache", "persistencia": "Persistência", "indice": "Índices"}
VARIANTES = {
    "sem-cache": "Sem cache", "frio": "Cache frio", "quente": "Cache quente", "ttl-curto": "TTL curto",
    "lazy": "LAZY", "eager": "EAGER", "join-fetch": "JOIN FETCH",
    "sem-indice": "Sem índice", "com-indice": "Com índice",
}
METRICAS = {
    "media_ms": ("Latência média", "ms"),
    "p95_ms": ("Latência p95", "ms"),
    "p99_ms": ("Latência p99", "ms"),
    "vazao_rps": ("Vazão", "req/s"),
    "erro_pct": ("Taxa de erros", "%"),
}
ESTILOS = (
    ("#263238", "o", "-"),
    ("#126d64", "s", "--"),
    ("#1b6589", "^", "-."),
    ("#a66817", "D", ":"),
)


def preparar_dados(linhas, ambiente):
    grupos = defaultdict(list)
    chaves = set()
    for linha in linhas:
        amostra = (linha["cenario"], linha["variante"], int(linha["vus"]), int(linha["repeticao"]))
        if amostra in chaves:
            raise ValueError("O consolidado contém uma rodada repetida.")
        chaves.add(amostra)
        grupos[amostra[:3]].append(linha)

    painel = {
        "rodadas": len(linhas),
        "cargas": ambiente["cargas"],
        "repeticoes": ambiente["repeticoes"],
        "metricas": {chave: rotulo for chave, (rotulo, _) in METRICAS.items()},
        "cenarios": {},
    }
    esperadas = set()
    for cenario in ambiente["cenarios"]:
        registros = []
        for variante in MATRIZ[cenario]:
            for vus in ambiente["cargas"]:
                chave_grupo = (cenario, variante, vus)
                grupo = grupos[chave_grupo]
                esperadas.update((*chave_grupo, rep) for rep in range(1, ambiente["repeticoes"] + 1))
                if len(grupo) != ambiente["repeticoes"]:
                    raise ValueError(f"Combinação incompleta: {chave_grupo}.")
                pontos_p95 = [float(x["p95_ms"]) for x in grupo]
                hits = sum(int(x["hits"]) for x in grupo)
                misses = sum(int(x["misses"]) for x in grupo)
                linha = {
                    "cenario": cenario, "variante": variante, "rotulo": VARIANTES[variante], "vus": vus,
                    **{m: median(float(x[m]) for x in grupo) for m in METRICAS},
                    "p95_min": min(pontos_p95),
                    "p95_max": max(pontos_p95),
                    "requisicoes": sum(int(x["requisicoes"]) for x in grupo),
                    "hits": hits, "misses": misses,
                    "taxa_hit_pct": 100.0 * hits / (hits + misses) if hits + misses else None,
                }
                registros.append(linha)
        painel["cenarios"][cenario] = {"nome": CENARIOS[cenario], "linhas": registros}
    if esperadas != chaves or len(linhas) != ambiente["rodadas_esperadas"]:
        raise ValueError("O consolidado não corresponde à matriz registrada.")
    return painel


def numero_pt(valor, _):
    return f"{valor:,.3f}".rstrip("0").rstrip(".").replace(",", " ").replace(".", ",")


def gerar_figura(cenario, registros, campo, pasta):
    titulo, unidade = METRICAS[campo]
    fig, ax = plt.subplots(figsize=(11.4, 3.2), dpi=150, facecolor="white")
    fig.subplots_adjust(left=.105, right=.978, bottom=.31, top=.94)
    ax.set_axisbelow(True)
    ax.grid(axis="y", color="#dce2de", linewidth=.85)
    for lado in ("top", "right"):
        ax.spines[lado].set_visible(False)
    for lado in ("left", "bottom"):
        ax.spines[lado].set_color("#a7afac")
    for variante, (cor, marcador, estilo) in zip(MATRIZ[cenario], ESTILOS):
        dados = [linha for linha in registros if linha["variante"] == variante]
        xs = [linha["vus"] for linha in dados]
        ys = [linha[campo] for linha in dados]
        ax.plot(xs, ys, marker=marcador, linestyle=estilo, color=cor, linewidth=1.8,
                markersize=5, label=VARIANTES[variante])
        if campo == "p95_ms":
            amplitudes = [
                [ponto["p95_ms"] - ponto["p95_min"] for ponto in dados],
                [ponto["p95_max"] - ponto["p95_ms"] for ponto in dados],
            ]
            ax.errorbar(xs, ys, yerr=amplitudes, color=cor, fmt="none", capsize=2.5, alpha=.55)

    ax.set_xticks(sorted({linha["vus"] for linha in registros}))
    ax.set_xlabel("Usuários virtuais (VUs)", labelpad=5)
    valores = [linha[campo] for linha in registros]
    latencia = campo in ("media_ms", "p95_ms", "p99_ms")
    escala_log = cenario == "cache" and latencia and min(valores) > 0 and max(valores) / min(valores) >= 50
    rotulo = f"{titulo} ({unidade})"
    if escala_log:
        ax.set_yscale("log")
        rotulo += " · escala log"
    ax.set_ylabel(rotulo, labelpad=7)
    ax.yaxis.set_major_formatter(FuncFormatter(numero_pt))
    ax.tick_params(axis="both", labelsize=10, colors="#354240")
    if campo == "erro_pct" and all(linha[campo] == 0 for linha in registros):
        ax.set_ylim(-0.05, 0.2)
        ax.set_yticks([0, 0.1, 0.2])
    elif not escala_log:
        ax.set_ylim(bottom=0)
    fig.legend(loc="lower center", ncols=len(MATRIZ[cenario]), frameon=False,
               bbox_to_anchor=(.5, .015), fontsize=10)
    caminho = pasta / f"{cenario}-{campo.replace('_', '-')}.png"
    fig.savefig(caminho,
                metadata={"Title": f"{CENARIOS[cenario]} - {titulo} por carga"})
    plt.close(fig)
    return caminho


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bateria", type=Path, help="Pasta com consolidado.csv e execucao.json.")
    parser.add_argument("--saida", type=Path, help="Diretório de destino das figuras e do painel.")
    args = parser.parse_args()
    entrada = args.bateria / "consolidado.csv"
    with entrada.open(encoding="utf-8", newline="") as arquivo:
        linhas = list(csv.DictReader(arquivo))
    ambiente = json.loads((args.bateria / "execucao.json").read_text(encoding="utf-8"))
    painel = preparar_dados(linhas, ambiente)
    saida = args.saida or args.bateria
    graficos = saida / "graficos"
    graficos.mkdir(parents=True, exist_ok=True)

    arquivos = []
    for chave, cenario in painel["cenarios"].items():
        for campo in METRICAS:
            arquivos.append(gerar_figura(chave, cenario["linhas"], campo, graficos))

    csv_publicado = saida / "consolidado.csv"
    if entrada.resolve() != csv_publicado.resolve():
        csv_publicado.write_text(entrada.read_text(encoding="utf-8"), encoding="utf-8", newline="\n")
    registros = [linha for cenario in painel["cenarios"].values() for linha in cenario["linhas"]]
    with (saida / "comparativo.csv").open("w", encoding="utf-8", newline="") as arquivo:
        escritor = csv.DictWriter(arquivo, fieldnames=list(registros[0]))
        escritor.writeheader()
        escritor.writerows(registros)

    painel["arquivos"] = {
        png.name: "data:image/png;base64," + b64encode(png.read_bytes()).decode("ascii")
        for png in arquivos
    }
    painel["csv"] = "data:text/csv;charset=utf-8;base64," + b64encode(csv_publicado.read_bytes()).decode("ascii")
    molde = Path(__file__).with_name("painel.html").read_text(encoding="utf-8")
    dados = json.dumps(painel, ensure_ascii=False).replace("<", "\\u003c")
    (saida / "painel.html").write_text(molde.replace("__A2_DADOS__", dados), encoding="utf-8")
    print(f"Figuras, tabela e painel gerados em {saida}.")


if __name__ == "__main__":
    main()
