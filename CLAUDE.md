# CLAUDE.md — Projeto ACTA

Instruções permanentes para o Claude Code (secções 2.2, 3 e 4 da instrução original).
Ao retomar uma sessão: ler este ficheiro e `docs/PROGRESSO.md` e continuar a partir daí.

### 2.2. Regras permanentes
1. Executa as fases pela ordem. No fim de cada fase:
   - faz commit (mensagem em português que explique o porquê) e push;
   - atualiza `docs/PROGRESSO.md` (fase concluída, o que ficou feito, o que falta, problemas);
   - explica-me em português, de forma breve, o que fizeste e porquê. O objetivo é eu aprender, não apenas entregar.
2. **Paragens obrigatórias no fim das Fases 3, 4 e 7.** Estas fases dependem do microfone, da API e das apps do telemóvel e só se validam num telemóvel real. Diz-me o que devo testar e espera pelo meu resultado antes de continuar.
3. Se uma fase exigir uma decisão que este prompt não resolve, para e pergunta.
4. Se a sessão for retomada, lê primeiro `CLAUDE.md` e `docs/PROGRESSO.md` e continua a partir daí.
5. Nunca afirmes que algo compila ou funciona sem o teres verificado. Se não conseguires compilar aqui, diz-mo: o build real é feito pelo GitHub Actions.
6. Mantém `docs/registo-comandos.md` só com os comandos que executaste de facto, cada um com a sua finalidade.

## 3. ESPECIFICAÇÃO TÉCNICA

### Pilha
- Kotlin, Jetpack Compose, Material 3. `minSdk 26`. `targetSdk` e `compileSdk` na versão estável mais recente suportada pelo AGP.
- Gradle Kotlin DSL com version catalog (`gradle/libs.versions.toml`).
- Rede: OkHttp + kotlinx.serialization. Persistência: Room. Definições: DataStore.
- Camadas `ui/`, `domain/`, `data/`. As regras de negócio ficam em `domain/`, em Kotlin puro, sem dependências Android, com testes unitários.
- Toda a interface em português.

### Assinatura do APK
- Cada execução do GitHub Actions cria uma chave debug nova. Sem correção, cada APK novo recusa instalar-se por cima do anterior e obriga a desinstalar, com perda de dados.
- Solução: gerar `app/debug.keystore` com `keytool`, com as credenciais debug convencionais do Android (`android`/`androiddebugkey`), fazer commit e configurar o `signingConfig` debug para o usar.
- Não é um segredo: só assina builds de teste. Explicar isto no README.
- Se `keytool` não estiver disponível, dizer-me e não inventar o ficheiro.

### Gravação
- Serviço em primeiro plano do tipo microfone, com notificação persistente.
- `MediaRecorder`, fonte `VOICE_COMMUNICATION` (aproveita o cancelamento de eco e a redução de ruído do sistema), mono, 16 kHz, cerca de 64 kbps.
- **Formato `AAC_ADTS` durante a gravação, não MPEG-4.** Um M4A só fica válido quando a gravação é fechada corretamente. Se o processo morrer (o Android mata a app quando o utilizador revoga uma permissão), o ficheiro inteiro fica ilegível. O ADTS é legível até ao último bloco escrito.
- Gravar em partes com `setMaxFileSize` + `setNextOutputFile`, guardando o início de cada parte em ms. A duração de cada parte define-se na Fase 4, depois de confirmado o limite de duração por pedido da API. Até lá, usar 20 minutos.
- Antes do envio, converter cada parte ADTS para M4A com `MediaExtractor` + `MediaMuxer`, sem recodificar (a API não aceita `.aac`). **Nunca** cortar ficheiros por bytes.
- Antes de gravar, verificar se há pelo menos 500 MB livres.
- Emitir um sinal sonoro curto ao iniciar.
- Permissões: `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `POST_NOTIFICATIONS` (API 33+). Pedir em tempo de execução com explicação ao utilizador.
- **Chamadas recebidas:** pausar a gravação, marcar a interrupção na linha temporal e retomar automaticamente no fim da chamada. Antes de implementar, confirma que mecanismo funciona do API 26 até ao mais recente e que permissões exige. Se exigir `READ_PHONE_STATE`, pergunta-me primeiro.
- Ao reabrir a app após terminação anómala, detetar sessões interrompidas e recuperar as partes já gravadas.

### Transcrição
- `POST https://api.openai.com/v1/audio/transcriptions`, modelo `gpt-4o-transcribe-diarize`, `response_format=diarized_json`, `chunking_strategy=auto` (obrigatório acima de 30 s).
- **Antes de implementar, confirma na documentação oficial da OpenAI e mostra-me:**
  - o formato exato da resposta;
  - se o parâmetro `language` é suportado por este modelo;
  - a duração máxima de áudio por pedido e o limite de tamanho (25 MB, segundo a documentação à data desta instrução).
  Ajusta a duração das partes a esses limites, com margem. Não inventes campos. Se não conseguires aceder à documentação, para e diz-me.
- Um pedido por parte gravada. Somar aos tempos de cada segmento o início da respetiva parte.
- Os rótulos de orador só valem dentro de cada pedido. Guardar como "P1-A", "P2-A", etc., para o utilizador os associar na revisão.
- Guardar os segmentos com `id`, `inicioMs`, `fimMs`, `orador` e `texto`.
- Erros com mensagem clara (sem rede, chave inválida, sem saldo na conta, limite excedido) e opção de repetir só a parte que falhou.
- A transcrição é feita **depois** da reunião, em lote. Não é em tempo real.

### Acta
- Confirma na documentação oficial da OpenAI o modelo de texto atual e o mecanismo de saída estruturada com JSON Schema. Mostra-me antes de implementar.
- **Dados que vêm da base de dados, nunca do LLM:** identificação da reunião, data, hora de início e de termo, local, presentes e ausentes. O código preenche-os e envia-os ao LLM apenas como contexto. Pedi-los ao LLM seria convidá-lo a inventar.
- **Dados gerados pelo LLM:** ordem de trabalhos (inferida), súmula por ponto, deliberações, votações, ações (responsável, descrição, prazo), data da próxima reunião (se mencionada) e súmula executiva (≤ 250 palavras).
- Enviar ao LLM:
  - a data da reunião, para resolver prazos relativos como "até sexta-feira" para ISO 8601;
  - a lista de participantes com nome e função;
  - a transcrição com o id, o orador (nome, se atribuído) e o texto de cada segmento.
- Cada deliberação e cada ação traz `segmentoIds` (lista) e `citacao` (excerto literal curto).
  **Não pedir tempos ao LLM**: `ancoraInicioMs` e `ancoraFimMs` calculam-se em código a partir dos segmentos citados.
- **Verificação obrigatória em código:** um item fica "Não verificado", destacado a âmbar, se algum id não existir ou se a `citacao` normalizada (minúsculas, sem pontuação, espaços colapsados) não constar do texto desses segmentos. Nunca descartar em silêncio.
- O responsável de uma ação tem de corresponder a um participante da lista. Se não corresponder, ou se não for claro, fica "Por atribuir".
- Todas as actas (no ecrã e no PDF) incluem: "Documento gerado com recurso a inteligência artificial. Carece de validação humana."
- Se a transcrição tiver menos de 60 segundos de fala, recusar a geração e explicar porquê.

### Ciclo de vida da acta
- `RASCUNHO → EM_REVISAO → APROVADA → DISTRIBUIDA`. De `EM_REVISAO` pode voltar-se a `RASCUNHO`. Transições inválidas lançam erro.
- Distribuir só a partir de `APROVADA`.
- Na aprovação, fixar o conteúdo e calcular o SHA-256. Alterar depois disso cria uma nova versão; a anterior fica preservada e imutável.

### Distribuição e calendário
- Pré-visualização dos destinatários (presentes com e-mail válido, mais destinatários adicionados à mão) e confirmação explícita.
- Intent `ACTION_SEND` com `EXTRA_EMAIL`, o PDF anexado via FileProvider e a súmula executiva no corpo.
- Ao regressar à app, perguntar "O e-mail foi enviado?". Só com "Sim" marcar `DISTRIBUIDA` e registar data, destinatários e versão. Com "Não", manter `APROVADA`.
- "Agendar próxima reunião": Intent `ACTION_INSERT` em `CalendarContract.Events`, com título, data e participantes a partir da acta. Sem data de próxima reunião na acta, pedir a data ao utilizador.
- Também permitir partilhar o PDF pela folha de partilha nativa do Android.

### Voz
- Leitura da acta e da súmula com `TextToSpeech` nativo em português, com controlos de velocidade e volume.
- Se não houver voz em português instalada no telemóvel, informar e indicar onde instalar.

### Modo demonstração
- No primeiro arranque, oferecer "Explorar com uma reunião de exemplo". Carrega uma reunião fictícia, já processada, sem gastar API:
  - 4 participantes;
  - cerca de 15 minutos de transcrição verosímil em português, com 3 oradores;
  - 3 deliberações e 5 ações, uma delas "Por atribuir";
  - a acta em `RASCUNHO`.
- Permite testar revisão, acta, aprovação, PDF, distribuição, calendário, pesquisa e voz sem chave. Gravação e transcrição continuam a exigir chave.
- Os dados de exemplo são claramente marcados como fictícios e podem ser apagados.

## 4. SEGURANÇA E PRIVACIDADE — REGRAS ABSOLUTAS

- **Nunca** escrever chaves de API no código, no `build.gradle`, em ficheiros commitados ou nos logs.
- A chave OpenAI é introduzida pelo utilizador no ecrã de Definições. Gerar uma chave AES no Android Keystore, cifrar a chave da API com ela e guardar o resultado no DataStore.
- Excluir os dados da app das cópias de segurança automáticas (`allowBackup=false` e `dataExtractionRules`).
- Bloqueio da app com `BiometricPrompt` ao abrir (credencial do dispositivo como alternativa), porque o ecrã inicial já mostra o arquivo.
- Comando "Apagar todos os dados", com dupla confirmação.
- A app não infere emoções nem traços de personalidade a partir da voz.
- Antes de cada commit, verificar que não há segredos nem dados sensíveis. A única exceção autorizada é `app/debug.keystore`.

## Decisões aprovadas no arranque (2026-09-24)

- `applicationId` e pacote: `cv.acta.app`.
- Versões: AGP 9.4.1 (Kotlin integrado), Gradle 9.6.0, Kotlin 2.4.20, KSP 2.3.12, compileSdk/targetSdk 37.
- Gravação por partes: o `AACWriter` do Android não implementa `setNextFd`, por isso `setNextOutputFile` não funciona em ADTS.
  As partes rodam por temporizador (stop + novo `MediaRecorder`). A pausa e as chamadas também fecham a parte atual.
- Duração de cada parte: 5 minutos. O modelo `gpt-4o-transcribe-diarize` tem um máximo de 2 000 tokens de saída por pedido.
- Chamadas: deteção pelo modo do `AudioManager` (sem `READ_PHONE_STATE`).
- `app/debug.keystore` gerado com `openssl pkcs12` (PKCS12), porque o `keytool` não estava disponível no ambiente.
- Não há push a partir desta máquina: o utilizador faz o upload para o GitHub e o build é feito pelo GitHub Actions.
