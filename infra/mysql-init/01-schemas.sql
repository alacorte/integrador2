-- Um schema por servico: cada um roda as suas proprias migrations e mantem a
-- sua propria tabela flyway_schema_history. Num schema compartilhado, o
-- segundo servico a subir encontraria migrations aplicadas que nao consegue
-- resolver localmente e falharia no start.

CREATE DATABASE IF NOT EXISTS parametrizador_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS orquestrador_db
    CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- O usuario da aplicacao enxerga os dois: a tela desktop (fatia 2) le de ambos.
GRANT ALL PRIVILEGES ON parametrizador_db.* TO 'integrador2'@'%';
GRANT ALL PRIVILEGES ON orquestrador_db.* TO 'integrador2'@'%';
FLUSH PRIVILEGES;
