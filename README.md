# John Sampaio

**Desenvolvimento de sistemas, Python, React e análise de dados.**

Meu portfólio reúne aplicações com regras de negócio, APIs REST, bancos de dados,
testes e documentação, além de projetos de análise e BI. Cada repositório apresenta
o problema, as decisões técnicas e as instruções para executar e verificar a entrega.

## Projetos em destaque

### [ServiceDesk · Central de chamados](https://github.com/JVCSampaio/service-desk)

Sistema de atendimento interno: cadastro, prioridades, atribuição de equipe, resolução
e histórico. Controle de versão recusa alterações desatualizadas; os testes verificam
regras, persistência e o fluxo completo pela interface.

**Python · FastAPI · React · TypeScript · SQLite · Pytest · Playwright**  
[Código e execução](https://github.com/JVCSampaio/service-desk)
· [Requisitos e casos de teste](https://github.com/JVCSampaio/service-desk/blob/main/docs/ANALISE.md)

<a href="https://github.com/JVCSampaio/service-desk">
  <img src="https://raw.githubusercontent.com/JVCSampaio/service-desk/main/docs/overview.png" alt="ServiceDesk: fila, prioridades e histórico de atendimento" width="900">
</a>

### [StockFlow · Inventário e movimentações](https://github.com/JVCSampaio/stockflow)

Controle de materiais de TI com saldo e histórico na mesma transação, bloqueio de
saídas acima do saldo e reenvios sem duplicação. Inclui testes de concorrência,
interface responsiva e CSVs para análise no Power BI.

**Python · FastAPI · React · TypeScript · SQLite · Pytest · Docker**  
[Código e execução](https://github.com/JVCSampaio/stockflow)
· [Regras e decisões](https://github.com/JVCSampaio/stockflow/blob/main/docs/ANALISE.md)

<a href="https://github.com/JVCSampaio/stockflow">
  <img src="https://raw.githubusercontent.com/JVCSampaio/stockflow/main/docs/overview.png" alt="StockFlow: inventário, reposição e registro de movimentações" width="900">
</a>

### [E-commerce Analytics · React + Power BI](https://github.com/JVCSampaio/ecommerce-analytics-react-powerbi)

Análise de vendas, margem e operação de um e-commerce fictício. Dataset sintético com
30.170 pedidos, modelo estrela, 17 medidas DAX e dashboard com filtros e comparação anual.

**Python · Power BI · DAX · Power Query · React · TypeScript**  
[Abrir dashboard](https://jvcsampaio.github.io/ecommerce-analytics-react-powerbi/)
· [Modelo Power BI](https://github.com/JVCSampaio/ecommerce-analytics-react-powerbi/tree/main/powerbi)
· [Código e documentação](https://github.com/JVCSampaio/ecommerce-analytics-react-powerbi)

<a href="https://jvcsampaio.github.io/ecommerce-analytics-react-powerbi/">
  <img src="https://raw.githubusercontent.com/JVCSampaio/ecommerce-analytics-react-powerbi/main/docs/dashboard.png" alt="Dashboard de vendas: receita, margem, pedidos e comparação anual" width="900">
</a>

### [DataForge · Pipeline e API de dados](https://github.com/JVCSampaio/dataforge)

Ingestão incremental de API e CSV público, verificações de qualidade, persistência em
PostgreSQL e consultas analíticas com CTEs e funções de janela. Resultados expostos por uma API.

**Python · SQL · PostgreSQL · FastAPI · Docker**

### [ChurnLab · Previsão de churn](https://github.com/JVCSampaio/churnlab)

Pipeline sobre o dataset Telco Customer Churn: preparação, comparação de regressão logística,
Random Forest e XGBoost, acompanhamento de experimentos e API de inferência.

**Python · scikit-learn · XGBoost · MLflow · FastAPI**

## Fundamentos e outros projetos

| Projeto | Escopo |
|---|---|
| [Estudos de ciência de dados](https://github.com/JVCSampaio/data-science-projects) | Série de seis estudos baseados no livro de Stephen Klosterman: exploração, regressão, regularização, árvores e imputação. |
| [JobFlow](https://github.com/JVCSampaio/jobflow) | API de processamento de jobs com autenticação JWT, worker, retries e persistência. C# / ASP.NET Core / PostgreSQL. |
| [Forgehand](https://github.com/JVCSampaio/forgehand) | Ferramenta Python para delegar tarefas de programação a um modelo local, com integração MCP. |

## Tecnologias presentes no portfólio

- **Análise e BI:** Python, pandas, SQL, Power BI, DAX e Power Query.
- **Modelagem:** scikit-learn, XGBoost e MLflow.
- **Aplicações:** React, TypeScript, FastAPI e ASP.NET Core.
- **Bancos de dados:** SQL, SQLite e PostgreSQL.
- **Testes e entrega:** Pytest, Playwright, Git, GitHub Actions e Docker.

Os projetos de estudo identificam suas referências; os projetos com dados sintéticos
declaram essa origem. Resultados, testes e limitações estão descritos nos respectivos repositórios.
