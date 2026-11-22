import http from 'k6/http';
import exec from 'k6/execution';
import { Counter, Rate, Trend } from 'k6/metrics';

const base = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const cenario = __ENV.CENARIO || 'cache';
const variante = __ENV.VARIANTE || 'sem-cache';
const duracao = Number(__ENV.DURACAO_S || 12);
const vus = Number(__ENV.VUS || 10);
const limite = Number(__ENV.LIMITE || 30);
const clientes = Number(__ENV.CLIENTES || 5000);
const medicao = { tags: { fase: 'medicao', name: cenario }, timeout: '4s', redirects: 0 };
const aquecimento = { tags: { fase: 'aquecimento', name: 'setup' }, timeout: '12s', redirects: 0 };
const variantes = {
  cache: ['sem-cache', 'frio', 'quente', 'ttl-curto'],
  persistencia: ['lazy', 'eager', 'join-fetch'],
  indice: ['sem-indice', 'com-indice'],
};
if (!variantes[cenario]?.includes(variante)) {
  throw new Error('Cenário ou variante inválida.');
}

const erros = new Rate('erros_medicao');
const hits = new Counter('cache_hits_medicao');
const misses = new Counter('cache_misses_medicao');
const latenciaHit = new Trend('latencia_hit_ms', true);
const latenciaMiss = new Trend('latencia_miss_ms', true);
const termino = new Trend('termino_medicao_ms');

export const options = {
  discardResponseBodies: true,
  scenarios: {
    carga: { executor: 'constant-vus', vus, duration: duracao + 's', gracefulStop: '5s' },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)'],
  thresholds: {
    'http_req_duration{fase:medicao}': ['max>=0'],
    'http_reqs{fase:medicao}': ['count>0'],
  },
};

function url(i, semCache = false) {
  if (cenario === 'cache') {
    const id = (i % 20) + 1;
    const sufixo = variante === 'ttl-curto' ? '?ttl=2' : '?ttl=600';
    const modo = semCache || variante === 'sem-cache' ? 'sem-cache' : 'com-cache' + sufixo;
    return base + '/api/categorias/' + id + '/estatisticas/' + modo;
  }
  if (cenario === 'persistencia') return base + '/api/pedidos/' + variante + '?limit=' + limite;
  return base + '/api/pedidos/cliente/' + ((i % clientes) + 1);
}

function conferir(resposta, operacao) {
  if (resposta.status !== 200) {
    throw new Error(operacao + ' retornou HTTP ' + resposta.status + '. ' + (resposta.error || ''));
  }
  return resposta;
}

function admin(metodo, rota) {
  const resposta = http.request(metodo, base + '/api/admin/' + rota, null, {
    ...aquecimento, responseType: 'text',
  });
  return conferir(resposta, metodo + ' ' + rota).json();
}

function aquecer(quantidade, semCache = false) {
  const requisicoes = Array.from({ length: quantidade }, (_, i) => ({
    method: 'GET', url: url(i, semCache), params: aquecimento,
  }));
  http.batch(requisicoes).forEach(r => conferir(r, 'Aquecimento'));
}

export function setup() {
  if (cenario === 'indice') {
    const comIndice = variante === 'com-indice';
    const estado = admin(comIndice ? 'POST' : 'DELETE', 'indice');
    if (estado.estado !== (comIndice ? 'CRIADO' : 'REMOVIDO') || (comIndice && !(estado.bytes > 0))) {
      throw new Error('O estado do índice diverge da variante.');
    }
  }

  if (cenario !== 'cache') {
    aquecer(8);
    return;
  }

  // Aquece a agregação e o pool com a mesma leitura em todas as variantes.
  aquecer(20, true);
  admin('POST', 'cache/limpar');
  const preenchido = variante === 'quente' || variante === 'ttl-curto';
  if (preenchido) aquecer(20);
  const chaves = admin('GET', 'cache/estatisticas').chavesNoRedis;
  if (chaves !== (preenchido ? 20 : 0)) {
    throw new Error('Estado inicial de cache inválido. Chaves encontradas: ' + chaves);
  }
}

export default function () {
  const resposta = http.get(url(exec.scenario.iterationInTest), medicao);
  termino.add(Date.now() - exec.scenario.startTime);
  if (cenario !== 'cache' || resposta.status !== 200) {
    erros.add(resposta.status !== 200);
    return;
  }

  const cache = resposta.headers['X-Cache'];
  if (variante === 'sem-cache') {
    erros.add(cache !== 'BYPASS');
  } else {
    erros.add(cache !== 'HIT' && cache !== 'MISS');
    if (cache === 'HIT') {
      hits.add(1);
      latenciaHit.add(resposta.timings.duration);
    }
    if (cache === 'MISS') {
      misses.add(1);
      latenciaMiss.add(resposta.timings.duration);
    }
  }
}

export function handleSummary(dados) {
  const metrica = nome => dados.metrics[nome]?.values || {};
  const http = metrica('http_req_duration{fase:medicao}');
  const requisicoes = metrica('http_reqs{fase:medicao}').count || 0;
  const segundos = Math.max(duracao * 1000, metrica('termino_medicao_ms').max || 0) / 1000;
  const resultado = {
    cenario, variante, vus,
    repeticao: Number(__ENV.REPETICAO || 1),
    duracao_s: duracao,
    janela_s: segundos,
    requisicoes,
    vazao_rps: requisicoes / segundos,
    media_ms: http.avg,
    p95_ms: http['p(95)'],
    p99_ms: http['p(99)'],
    erro_pct: 100 * (metrica('erros_medicao').rate ?? 0),
    hits: metrica('cache_hits_medicao').count || 0,
    misses: metrica('cache_misses_medicao').count || 0,
    hit_media_ms: metrica('latencia_hit_ms').avg ?? null,
    miss_media_ms: metrica('latencia_miss_ms').avg ?? null,
  };
  const texto = JSON.stringify(resultado, null, 2) + '\n';
  return __ENV.SUMARIO ? { [__ENV.SUMARIO]: texto, stdout: texto } : { stdout: texto };
}
