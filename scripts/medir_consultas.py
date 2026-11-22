"""Reproduz os experimentos de cache, persistência e índice no banco local."""
import argparse
import csv
import json
import re
import time
from pathlib import Path
from statistics import median
from urllib.error import HTTPError

from rodar_carga import api, base_local, salvar_json

ESTRATEGIAS = (
    ("lazy", "lazy", True),
    ("eager", "eager", True),
    ("join-fetch", "join-fetch", True),
    ("lazy sem itens", "lazy", False),
    ("eager sem itens", "eager", False),
    ("dto-jpql", "dto", True),
    ("dto-nativo", "dto-nativo", True),
)
INDICE = "idx_pedido_cliente_data"


def exigir(condicao, mensagem):
    if not condicao:
        raise RuntimeError(mensagem)


def salvar_csv(arquivo, linhas):
    with arquivo.open("w", encoding="utf-8", newline="") as saida:
        escritor = csv.DictWriter(saida, fieldnames=list(linhas[0]))
        escritor.writeheader()
        escritor.writerows(linhas)


def leitura_cache(base, rota, x_cache, sql):
    resultado, headers = api(base, rota)
    exigir(headers["X-Cache"] == x_cache and resultado["consultasSql"] == sql,
           f"Cache ou SQL inesperado em {rota}.")
    return resultado


def roteiro_cache(base):
    produto = "produtos/10"
    categoria = "categorias/11/estatisticas"
    linhas = []
    api(base, "admin/cache/limpar", "POST")
    original = leitura_cache(base, produto + "/sem-cache", "BYPASS", 1)["dados"]

    def registrar(passo, rota, cache, consultas, campo="preco"):
        resposta = leitura_cache(base, rota, cache, consultas)
        dado = resposta["dados"][campo]
        linhas.append({"passo": passo, "resposta": cache, "sql": consultas,
                       "servico_ms": resposta["tempoServicoMs"], "valor": dado,
                       "ttl_s": resposta["ttlRestanteSegundos"]})
        return dado

    registrar("produto direto do banco", produto + "/sem-cache", "BYPASS", 1)
    registrar("primeira consulta", produto + "/com-cache", "MISS", 1)
    exigir(registrar("repetição", produto + "/com-cache", "HIT", 0) == original["preco"],
           "HIT retornou preço diferente.")
    media_anterior = registrar("categoria antes da atualização", categoria + "/com-cache",
                              "MISS", 1, "precoMedio")
    try:
        alterado = 125.50 if original["preco"] != 125.50 else 135.50
        api(base, produto, "PUT", {"preco": alterado})
        exigir(registrar("após PUT com invalidação", produto + "/com-cache?ttl=2", "MISS", 1) == alterado,
               "A leitura após PUT deveria retornar o preço atualizado.")
        registrar("repetição após PUT", produto + "/com-cache", "HIT", 0)
        media_nova = registrar("categoria após PUT", categoria + "/com-cache", "MISS", 1, "precoMedio")
        exigir(media_nova != media_anterior, "Estatísticas da categoria não foram atualizadas.")
        api(base, produto + "?invalidarCache=false", "PUT", {"preco": 140.50})
        exigir(registrar("PUT sem invalidação, leitura do cache", produto + "/com-cache", "HIT", 0) == alterado,
               "A demonstração deveria retornar o preço antigo.")
        exigir(registrar("PUT sem invalidação, leitura do banco", produto + "/sem-cache", "BYPASS", 1) == 140.50,
               "O banco deveria retornar o preço novo.")
        time.sleep(2.1)
        exigir(registrar("leitura após expiração", produto + "/com-cache", "MISS", 1) == 140.50,
               "Após o TTL o cache deveria recuperar o preço atualizado.")
        for dados in ({"preco": -1}, {"estoque": -1}, {"nome": " "}, {"preco": 1.123}):
            try:
                api(base, produto, "PUT", dados)
            except HTTPError as erro:
                exigir(erro.code == 400, "A validação do PUT deveria responder 400.")
            else:
                raise RuntimeError("O PUT aceitou dados inválidos.")
        exigir(leitura_cache(base, produto + "/sem-cache", "BYPASS", 1)["dados"]["preco"] == 140.50,
               "A atualização inválida alterou o banco.")
    finally:
        api(base, produto, "PUT", {"preco": original["preco"]})
        api(base, "admin/cache/limpar", "POST")
    return linhas


def amostras_cache(base, repeticoes):
    linhas = []
    alvos = {"Produto": "produtos/10", "Categoria": "categorias/11/estatisticas"}
    for alvo, rota in alvos.items():
        for _ in range(3):
            leitura_cache(base, rota + "/sem-cache", "BYPASS", 1)
        for rep in range(1, repeticoes + 1):
            api(base, "admin/cache/limpar", "POST")
            for variante, sufixo, estado, sql in (
                ("sem-cache", "/sem-cache", "BYPASS", 1),
                ("miss", "/com-cache", "MISS", 1),
                ("hit", "/com-cache", "HIT", 0),
            ):
                inicio = time.perf_counter()
                resultado = leitura_cache(base, rota + sufixo, estado, sql)
                linhas.append({"alvo": alvo, "variante": variante, "rep": rep, "sql": sql,
                               "servico_ms": resultado["tempoServicoMs"],
                               "cliente_ms": round((time.perf_counter() - inicio) * 1000, 3)})
    return linhas


def amostras_persistencia(base, repeticoes):
    linhas = []
    for limite in (10, 100, 500):
        for rep in range(1, repeticoes + 1):
            respostas = {}
            ordem = ESTRATEGIAS if rep % 2 else reversed(ESTRATEGIAS)
            for nome, rota, itens in ordem:
                consulta = f"pedidos/{rota}?limit={limite}"
                if not itens:
                    consulta += "&incluirItens=false"
                inicio = time.perf_counter()
                resposta, _ = api(base, consulta)
                duracao = (time.perf_counter() - inicio) * 1000
                dados = resposta["dados"]
                exigir(resposta["registrosRetornados"] == limite, "Número de pedidos incorreto.")
                respostas[nome] = dados
                linhas.append({
                    "estrategia": nome, "limite": limite, "rep": rep, "sql": resposta["consultasSql"],
                    "servico_ms": resposta["tempoServicoMs"], "cliente_ms": round(duracao, 3),
                    "pedidos": len(dados), "itens_orm": resposta["itensHidratados"],
                    "bytes_dados": len(json.dumps(dados, separators=(",", ":"), ensure_ascii=False).encode()),
                })
            exigir(respostas["lazy"] == respostas["eager"] == respostas["join-fetch"],
                   "As três variantes de listagem retornaram dados diferentes.")
            exigir(respostas["lazy sem itens"] == respostas["eager sem itens"],
                   "As listagens sem itens retornaram dados diferentes.")
            exigir(respostas["dto-jpql"] == respostas["dto-nativo"], "As projeções retornaram dados diferentes.")
    return linhas


def avaliar_indice(base, pasta):
    rota = "pedidos/cliente/150"
    resultado = {"cliente": 150}
    dados = {}
    try:
        for variante, metodo in (("sem-indice", "DELETE"), ("com-indice", "POST")):
            estado, _ = api(base, "admin/indice", metodo)
            valores = []
            planos = []
            for tentativa in range(7):
                dados[variante] = api(base, rota)[0]["dados"]
                plano = "\n".join(api(base, rota + "/explain")[0])
                if tentativa >= 2:
                    valores.append(float(re.search(r"Execution Time: ([\d.]+) ms", plano)[1]))
                    planos.append(plano)
            mediana = median(valores)
            representativo = planos[valores.index(mediana)]
            (pasta / (variante + ".txt")).write_text(representativo + "\n", encoding="utf-8")
            (pasta / (variante + "-rodadas.txt")).write_text("\n\n".join(planos) + "\n", encoding="utf-8")
            resultado[variante] = {
                "tamanho_bytes": estado.get("bytes", 0),
                "pedidos": len(dados[variante]),
                "tempos_ms": valores,
                "mediana_ms": mediana,
                "usou_indice": INDICE in representativo,
                "teve_sort": bool(re.search(r"^\s*(?:->\s*)?Sort\s+\(", representativo, re.MULTILINE)),
            }
        exigir(dados["sem-indice"] == dados["com-indice"], "O resultado SQL mudou após o índice.")
    finally:
        api(base, "admin/indice", "DELETE")
    return resultado


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", type=base_local, default="http://127.0.0.1:8080")
    parser.add_argument("--saida", type=Path, default=Path("resultados/consultas"))
    args = parser.parse_args()
    pasta = args.saida
    pasta.mkdir(parents=True, exist_ok=False)
    salvar_csv(pasta / "cache-roteiro.csv", roteiro_cache(args.base_url))
    salvar_csv(pasta / "cache-tempos.csv", amostras_cache(args.base_url, 15))
    print("Cache validado.", flush=True)
    salvar_csv(pasta / "persistencia.csv", amostras_persistencia(args.base_url, 5))
    print("Equivalência das consultas validada.", flush=True)
    salvar_json(pasta / "indice.json", avaliar_indice(args.base_url, pasta))
    print("Planos e resultados de índice registrados.")


if __name__ == "__main__":
    main()
