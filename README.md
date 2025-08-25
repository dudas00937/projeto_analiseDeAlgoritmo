# projeto_analiseDeAlgoritmo
Documentação do projeto App.java

Esse código utiliza HTTP, arrays estáticos físicos, rotas implementadas manualmente (get, post, patch e delete), e o HTML e JS estão dentro do código de Java. Além de não usar bibliotecas de JSON.

O código tem alguns pontos positivos, como por exemplo, ser simples, didático e auto-contido (dá pra rodar sem dependências externas). Implementa uma API REST funcional e um front-end básico em HTML/JS. CSV é legível e po de ser editado manualmente. Porem dificulta algumas coisas, sendo exemplos disso: 
-Estrutura de dados baseada em arrays fixos (limita escalabilidade e dificulta manutenção).
-Ausência de POO real, não há classe Task, apenas arrays paralelos.
-Parsing de JSON manual (não lida bem com caracteres especiais, listas etc.).
-Baixa manutenibilidade, HTML muito grande hardcoded dentro de INDEX_HTML.
-Servidor HTTP básico sem suporte a middlewares, filtros, autenticação etc.
-Armazenamento em CSV pouco confiável se houver concorrência ou muitos acessos.
-Ausência de testes unitários e separação em camadas (Controller, Model, Service).

Proposta de substituição:
-Definir uma classe Task com atributos id, titulo, descricao, status, criadoEm.
-Persistência usando JPA com banco de dados (H2 para teste ou PostgreSQL/MySQL em produção).
-Classe Task, representando uma tarefa (id, título, descrição, status, data de criação).
-Classe TaskRepository, gerenciando as tarefas em memória além de salvar o CSV.
-Classe App, como servidor HTTP.
-Handlers usando TaskRepository para manipular tarefas.
