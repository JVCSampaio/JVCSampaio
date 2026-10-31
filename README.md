# João Vitor Sampaio

**Analista de Dados/BI Jr ou Dev Python Jr | Rio de Janeiro ou remoto**

Ciência da Computação na Estácio, conclusão em 12/2026. [LinkedIn](https://www.linkedin.com/in/jvcsampaio)

## Dados e BI

<a href="https://jvcsampaio.github.io/ecommerce-analytics-react-powerbi/">
  <img src="https://raw.githubusercontent.com/JVCSampaio/ecommerce-analytics-react-powerbi/main/docs/dashboard.png" alt="Dashboard de vendas do e-commerce: receita, margem, pedidos e comparação anual" width="820">
</a>

- [ecommerce-analytics-react-powerbi](https://github.com/JVCSampaio/ecommerce-analytics-react-powerbi): vendas, margem e logística de um e-commerce fictício. Dataset sintético em Python, modelo estrela, 17 medidas DAX em PBIP/TMDL e dashboard React/TypeScript. [Abrir o dashboard](https://jvcsampaio.github.io/ecommerce-analytics-react-powerbi/).
- [supplywatch-analytics](https://github.com/JVCSampaio/supplywatch-analytics): compras e fornecedores em Óleo e Gás. ETL de CSV, Excel e JSON com checagem de qualidade, modelo estrela, OTIF e saldo vencido por fornecedor, dashboard Streamlit e projeto Power BI (PBIP) com relatório de quatro páginas e 22 medidas DAX.
- [commercial-pulse-analytics](https://github.com/JVCSampaio/commercial-pulse-analytics): receita, margem, descontos e devoluções em vendas B2B, com regressão comparada a um baseline sazonal, dashboard Streamlit e modelo Power BI (TMDL, DAX e Power Query).
- [dataforge](https://github.com/JVCSampaio/dataforge): ingestão incremental da API do GitHub e de um CSV público (Gapminder), checagem de qualidade, PostgreSQL, SQL com CTEs e funções de janela, API FastAPI em Docker, DAG do Airflow e fluxo do n8n.

Os dados de vendas e compras são simulados, com semente fixa. Os modelos Power BI do SupplyWatch (projeto PBIP) e do Commercial Pulse (pacote TMDL) passam por validação estrutural no CI, mas ainda não foram abertos no Power BI Desktop e não têm PBIX.

## Python e IA

- [opsatlas](https://github.com/JVCSampaio/opsatlas): consulta a procedimentos técnicos que responde com o trecho e a revisão da fonte, e se abstém quando o acervo não tem a resposta. Busca lexical por padrão e RAG opcional. Python/FastAPI, Angular, PostgreSQL, Kafka e Airflow, com deploy testado em Kubernetes no CI.
- [churnlab](https://github.com/JVCSampaio/churnlab): previsão de cancelamento em 7.043 clientes de telecom (Telco Customer Churn). Compara regressão logística, Random Forest e XGBoost com validação cruzada e registra os experimentos no MLflow. A regressão logística venceu (ROC-AUC de 0,840 no teste), e o corte escolhido encontra 90% dos clientes que cancelam. API de inferência em FastAPI.
- [forgehand](https://github.com/JVCSampaio/forgehand): servidor MCP em Python que delega uma tarefa de código a um modelo local, num worktree Git limitado e sem o histórico da conversa. Adaptado de um projeto MIT, com a origem creditada.

## Sistemas e QA

- [service-desk](https://github.com/JVCSampaio/service-desk): central de chamados com fila, prioridade, atribuição, resolução, histórico e controle de versão. Python/FastAPI, React, TypeScript e SQLite.
- [service-desk-qa](https://github.com/JVCSampaio/service-desk-qa): 59 cenários Cypress em Gherkin, em português, e contrato de API em JSON Schema. Achou 6 defeitos no ServiceDesk, que foram corrigidos e viraram testes de regressão. [Relatório da última execução](https://jvcsampaio.github.io/service-desk-qa/).
- [chatflow](https://github.com/JVCSampaio/chatflow): chatbot de um provedor de internet fictício no WhatsApp, Telegram e webchat, com transferência para atendente, webhook com HMAC e dados pessoais mascarados. Node.js, TypeScript, MongoDB e React. 166 testes de unidade e API (99,7% das linhas), 24 cenários BDD, Newman e Selenium. [Relatórios de testes](https://jvcsampaio.github.io/chatflow/).
- [stockflow](https://github.com/JVCSampaio/stockflow): inventário de materiais de TI. Saldo e histórico na mesma transação, saída acima do saldo recusada e reenvio sem duplicar o movimento. Python/FastAPI, React, TypeScript, SQLite e Docker.

## Stack

Python, SQL, pandas, scikit-learn, Power BI/DAX, Excel avançado (Power Query), FastAPI, PostgreSQL, Streamlit, MLflow, Airflow, n8n, PyTorch, Hugging Face, Docker, Linux, Git, Scrum/Kanban.

## Estudos

Série arquivada do livro *Projetos de Ciência de Dados com Python* (Klosterman): [01 exploração](https://github.com/JVCSampaio/projeto-01-exploracao-dados), [02 regressão univariada](https://github.com/JVCSampaio/projeto-02-regressao-logistica-univariada), [03 regressão multivariada](https://github.com/JVCSampaio/projeto-03-regressao-logistica-multivariada), [04 Ridge](https://github.com/JVCSampaio/projeto-04-ridge-regularizacao), [05 árvores e random forest](https://github.com/JVCSampaio/projeto-05-arvores-de-decisao-random-forest) e [06 imputação](https://github.com/JVCSampaio/projeto-06-imputacao-pagamentos).
