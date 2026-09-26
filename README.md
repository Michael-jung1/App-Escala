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
  - Autenticação e autorização por chave de acesso com hash criptográfico SHA-256 e salt.
  - Proteção contra força bruta com rate limiting (bloqueio temporário de 30 segundos após 5 tentativas incorretas).
  - Armazenamento de credenciais protegido via `EncryptedSharedPreferences` (Jetpack Security com AES-256 GCM).
  - Visualização de escalas por igreja com filtro de dias futuros ou passados.
  - Edição direta de escalações com persistência imediata no banco SQLite local.
  - Importação inteligente de escalas via documentos/planilhas com IA multimodal (Gemini).

## Arquitetura e Tecnologias

- **Linguagem**: Kotlin
- **Interface**: Jetpack Compose com Material Design 3
- **Persistência**: SQLite nativo pré-carregado com dados de igrejas, postos e escalações
- **Segurança**: Jetpack Security (`androidx.security:security-crypto`), Hashing SHA-256 com Salt, `android:allowBackup="false"`
- **Navegação**: Material 3 NavigationBar com suporte completo a Edge-to-Edge

---

## 🔒 Diretrizes de Segurança e Proteção da Chave da API Gemini (CORREÇÃO 4)

### 1. Modelo de Ameaça e Restrição Obrigatória no Google Cloud
Como o aplicativo opera em modo cliente (*offline-first* sem servidor de autenticação intermediário), a chave de API fornecida no build (`BuildConfig.GEMINI_API_KEY`) trafega com o binário compilado.

Para mitigar o risco de extração e uso indevido da chave fora do aplicativo, **é obrigatório restringir a chave de API no Google Cloud Console**:

1. Acesse o **Google Cloud Console** > **APIs e Serviços** > **Credenciais**.
2. Localize a chave de API utilizada pelo projeto (`GEMINI_API_KEY`).
3. Em **Restrições de aplicativo**, selecione **Aplicativos Android**.
4. Clique em **Adicionar um item** e preencha:
   - **Nome do pacote**: `com.aistudio.escala.acolit`
   - **Impressão digital do certificado SHA-1**:
     - *Debug*: Execute `./gradlew signingReport` ou consulte a chave do `debug.keystore`.
     - *Release*: Extraia a impressão digital SHA-1 do keystore oficial de produção:
       ```bash
       keytool -list -v -keystore <seu-keystore-de-release>.jks -alias <seu-alias>
       ```
5. Em **Restrições de API**, restrinja a chave exclusivamente à **Generative Language API** (Google Gemini).
6. Salve as alterações.

> ⚠️ **Atenção Crítica**: Sempre que o keystore de release for rotacionado ou um novo certificado de upload/Play App Signing for emitido, a nova impressão digital SHA-1 deve ser adicionada imediatamente ao Google Cloud Console, sob risco de interrupção nas importações com IA.

### 2. Recomendação Arquitetural de Longo Prazo (Backend Proxy)
Para uma arquitetura com garantia total de proteção de credenciais e permissões (RBAC real no servidor):
- Implementar um backend intermediário (ex.: Google Cloud Function, Cloud Run ou Firebase Functions) que retenha a `GEMINI_API_KEY` em ambiente seguro (Google Secret Manager).
- O aplicativo cliente envia o documento para o backend com token de sessão assinado; o backend executa a chamada ao Gemini e retorna a estrutura JSON sanitizada ao app.

---

## 🛡️ Controles de Segurança Implementados

1. **Backup ADB Desativado**: `android:allowBackup="false"` definido no `AndroidManifest.xml` impede a extração de dados locais e banco SQLite via depuração USB.
2. **Rate Limiting**: Bloqueio de 30 segundos com contador regressivo após 5 falhas no código do coordenador.
3. **Credenciais Hashed e Criptografadas**: Códigos armazenados no SQLite utilizam hash SHA-256 com salt aleatório. Cache local opera exclusivamente sobre `EncryptedSharedPreferences`. Chaves fixas/mestres foram integralmente removidas do código-fonte.
4. **Sanitização de Mensagens**: Erros técnicos internos e stack traces de banco de dados são omitidos da interface do usuário.
5. **Mitigação de Prompt Injection**: Instruções no parser delimitam dados de entrada de comandos e reforçam a integridade estrutural da extração.
6. **Validação Rigorosa de Entradas**: Limites de tamanho, corte de espaços em branco e verificação de integridade antes da persistência no SQLite.
