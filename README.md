# Escala — Acólitos, Coroinhas e Cerimoniários

Aplicativo Android nativo desenvolvido em Kotlin com Jetpack Compose e SQLite para gerenciamento e consulta de escalas litúrgicas (Coroinhas, Acólitos e Cerimoniários).

## Funcionalidades

- **Minha Escala**:
  - Busca com preenchimento automático por nome do servidor litúrgico.
  - Cartão em destaque com a próxima escalação (função, igreja e data por extenso).
  - Listagem completa das próximas escalações com identificadores visuais por igreja.
  - Persistência local do nome pesquisado para abertura rápida.

- **Escala do Dia**:
  - Calendário mensal interativo com navegação entre meses e destaques visuais para os dias com serviço litúrgico.
  - Indicador personalizado para os dias de serviço do usuário ativo.
  - Filtro por igreja (São José, Sagrado, Perpétuo Socorro ou todas).
  - Agrupamento em cartões recolhíveis com listagem de postos e nomes escalados.

- **Painel do Coordenador**:
  - Autenticação segura por chave de acesso (ex: `JFYLQP` ou `DEMO01`).
  - Visualização de escalas por igreja com filtro de dias futuros ou passados.
  - Edição direta de escalações com persistência imediata no banco SQLite local.

## Arquitetura e Tecnologias

- **Linguagem**: Kotlin
- **Interface**: Jetpack Compose com Material Design 3
- **Persistência**: SQLite nativo pré-carregado com dados de igrejas, postos e escalações
- **Navegação**: Material 3 NavigationBar com suporte completo a Edge-to-Edge
