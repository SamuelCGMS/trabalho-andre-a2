-- Esquema do domínio: Categoria <- Produto <- ItemPedido -> Pedido -> Cliente
-- O PostgreSQL não cria índices para as chaves estrangeiras automaticamente.
-- idx_item_pedido_pedido faz parte da base e ajuda na busca de itens por pedido.
-- idx_pedido_cliente_data é criado separadamente para medir o efeito dele.

CREATE TABLE IF NOT EXISTS categoria (
    id   BIGSERIAL PRIMARY KEY,
    nome VARCHAR(100) NOT NULL
);

CREATE TABLE IF NOT EXISTS cliente (
    id     BIGSERIAL PRIMARY KEY,
    nome   VARCHAR(150) NOT NULL,
    email  VARCHAR(150) NOT NULL,
    cidade VARCHAR(80)  NOT NULL
);

CREATE TABLE IF NOT EXISTS produto (
    id           BIGSERIAL PRIMARY KEY,
    nome         VARCHAR(150)   NOT NULL,
    preco        NUMERIC(12, 2) NOT NULL,
    estoque      INTEGER        NOT NULL,
    categoria_id BIGINT         NOT NULL REFERENCES categoria (id)
);

CREATE TABLE IF NOT EXISTS pedido (
    id          BIGSERIAL PRIMARY KEY,
    cliente_id  BIGINT         NOT NULL REFERENCES cliente (id),
    data_pedido TIMESTAMP      NOT NULL,
    status      VARCHAR(20)    NOT NULL,
    valor_total NUMERIC(14, 2) NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS item_pedido (
    id             BIGSERIAL PRIMARY KEY,
    pedido_id      BIGINT         NOT NULL REFERENCES pedido (id),
    produto_id     BIGINT         NOT NULL REFERENCES produto (id),
    quantidade     INTEGER        NOT NULL,
    preco_unitario NUMERIC(12, 2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_item_pedido_pedido ON item_pedido (pedido_id);
