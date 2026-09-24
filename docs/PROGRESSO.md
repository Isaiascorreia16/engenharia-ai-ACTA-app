# Progresso

## Fase 0 — Ficheiros base e projeto mínimo — concluída
- Feito: workflow (conteúdo exato), `.gitignore`, version catalog, projeto Android mínimo com `MainActivity` "ACTA",
  `allowBackup=false` + `dataExtractionRules` + `fullBackupContent`, `debug.keystore` e `signingConfig`, teste trivial.
- Problemas: não há JDK/SDK neste ambiente, por isso **nada foi compilado localmente**. O build é verificado pelo GitHub Actions.
  O `gradlew` e o `gradle-wrapper.jar` não foram gerados; o workflow gera-os.

## Fase 1 — Domínio e dados — concluída
- Feito: modelos do domínio (`domain/modelo`), máquina de estados, versionamento e SHA-256 (`domain/ciclo`),
  entidades Room, DAO e base de dados (`data/db`), mapeadores entidade↔domínio.
- Testes: transições válidas e inválidas, distribuir um rascunho falha, aprovação calcula SHA-256,
  alterar uma acta aprovada cria nova versão e preserva a anterior.
- Não compilado localmente (sem JDK). A validar no primeiro build do Actions.

## Fase 2 — Preparação da reunião — concluída
- Feito: validação de participantes (e-mail, E.164, erro por campo) e de reuniões em `domain/validacao`, com testes;
  `RepositorioReunioes`; ecrãs Início (lista + pesquisa), Nova reunião, Reunião (participantes, presença, consentimento)
  e Aviso antes de gravar (quatro caixas obrigatórias, alerta com nomes sem consentimento, pedido de permissões).
- Nota: como o projeto foi escrito de seguida e sem compilador local, os commits intermédios referenciam classes das fases
  seguintes. Só o estado final do repositório foi pensado para compilar.

## Fase 3 — Gravação — concluída (falta teste em telemóvel real)
- Feito: `ServicoGravacao` (foreground service do tipo microfone, notificação persistente com Pausar/Retomar/Terminar),
  MediaRecorder `VOICE_COMMUNICATION`, AAC ADTS, mono, 16 kHz, 64 kbps; partes de 5 min com rotação por temporizador;
  pausa e chamada fecham a parte e abrem uma marca temporal; deteção de chamadas pelo modo do `AudioManager`
  (listener no API 31+, verificação a cada 1 s abaixo disso), sem `READ_PHONE_STATE`; verificação de 500 MB livres;
  sinal sonoro ao iniciar; wake lock parcial; recuperação de sessões interrompidas no arranque do processo; ecrã de gravação.
- Desvio: não se usa `setMaxFileSize` + `setNextOutputFile`, porque o `AACWriter` não suporta `setNextFd` (confirmado no AOSP).
  Custo: cerca de 0,1 a 0,3 s perdidos em cada mudança de parte, registados como marca "Mudança de parte".
- Paragem obrigatória: os testes em telemóvel estão listados no README (secção "Testes no telemóvel").

## Fase 4 — Chave de API e transcrição — concluída (falta teste real)
- Documentação da OpenAI consultada a 2026-09-24 (developers.openai.com):
  - `POST /v1/audio/transcriptions`, `model=gpt-4o-transcribe-diarize`, `response_format=diarized_json`,
    `chunking_strategy=auto` (obrigatório acima de 30 s).
  - Resposta: `{task, duration, text, segments:[{type:"transcript.text.segment", id, start, end, text, speaker}], usage}`.
    Usam-se só `speaker`, `start`, `end` e `text`.
  - `language`: não está documentado para este modelo, por isso não é enviado. O modelo também não aceita `prompt`.
  - Limites: ficheiros até 25 MB. Não há duração máxima publicada, mas o modelo tem janela de 16 000 tokens e
    **2 000 tokens de saída**. Partes de 5 min (cerca de 2,4 MB a 64 kbps e cerca de 1 100 tokens de fala em português) dão margem.
  - Confiança por segmento/logprobs: não disponível no modo diarizado (RF-ASR-005/006 ficam fora).
- Feito: `Definicoes` (DataStore + AES-256/GCM no Android Keystore), `ConversorAudio` (ADTS→M4A com MediaExtractor +
  MediaMuxer, sem recodificar), `ClienteOpenAI` (erros: sem rede, tempo esgotado, chave inválida, sem saldo, limite, ficheiro grande),
  `ServicoTranscricao` (um pedido por parte, rótulos "P<n>-<orador>", repetir só a parte que falhou), ecrã Definições.
- Testes: montagem dos segmentos (offset, rótulos, ids), tempo de fala sem contar sobreposições, tempo por orador.

## Fase 5 — Revisão — concluída
- Feito: `RepositorioRevisao` e ecrã Revisão: segmentos (orador, mm:ss, texto) com marcas temporais intercaladas,
  filtro por orador, edição com registo (autor, instante, texto anterior) e histórico, associação rótulo→participante
  com propagação, reatribuição de um segmento isolado, tempo de intervenção por orador, botão "Gerar acta"
  (recusa com menos de 60 s de fala).

## Fase 6 — Acta — concluída
- Documentação da OpenAI consultada a 2026-09-24: modelo de texto recomendado `gpt-6-sol` (há também `gpt-6-astra` e `gpt-6-luna`);
  saída estruturada pela Responses API (`POST /v1/responses`, `text.format = {type: "json_schema", name, strict: true, schema}`);
  no modo strict todos os campos são obrigatórios, `additionalProperties: false` e os opcionais são `["string","null"]`.
  A resposta traz `output[]` com itens `message` → `content[]` do tipo `output_text` (ou `refusal`), e `status` pode ser `incomplete`.
- Feito: `PromptActa` (instruções + entrada com data, participantes e transcrição com ids), `Cabecalho` (dados da BD),
  `EsquemaActa`, `Verificador` (citação normalizada nos segmentos citados, âncoras calculadas em código, "Não verificado" a âmbar,
  responsável → participante único ou "Por atribuir", prazo ISO, súmula ≤ 250 palavras), `RepositorioActas` (geração no escopo da app,
  edição, versões), ecrã Acta com faixa de IA, rubricas editáveis e "Ver origem" que abre o segmento na transcrição.
- Testes da verificação: válido, id inexistente, citação inexistente, citação de outro segmento, responsável que não é participante,
  responsável parcial e ambíguo, prazo não ISO, limite da súmula, segmento editado depois da geração.

## Fase 7 — Aprovação, PDF, distribuição e calendário — concluída (falta teste real)
- Feito: aprovação com fixação e SHA-256 do JSON canónico; `GeradorPdf` (PdfDocument, A4, menção de IA e hash no rodapé de
  cada página, itens não verificados assinalados); `Partilha` (ACTION_SEND com EXTRA_EMAIL e PDF via FileProvider,
  folha de partilha nativa, ACTION_INSERT em CalendarContract.Events); ecrã Distribuir com pré-visualização, destinatários
  adicionais validados, confirmação explícita e a pergunta "O e-mail foi enviado?" ao regressar (só "Sim" marca DISTRIBUIDA
  e regista data, destinatários e versão); agendamento da próxima reunião, com a data pedida ao utilizador quando a acta não a tem.

## Fase 8 — Arquivo, voz, segurança, demonstração e documentação — concluída
- Feito: pesquisa textual (títulos, transcrições, actas); leitura em voz (`LeitorVoz`, pt-PT → pt-BR → pt, velocidade e volume,
  indicação para instalar voz); bloqueio com `BiometricPrompt` ao abrir (biometria fraca ou credencial do dispositivo);
  "Apagar todos os dados" com dupla confirmação (BD, gravações, PDF, definições e chave do Keystore); modo demonstração com reunião
  fictícia (4 participantes, cerca de 15 min, 3 oradores, 3 deliberações, 5 ações, uma "Por atribuir", acta em RASCUNHO) que pode ser apagada;
  ecrã de Ajuda com limitações (inclui RF-INT-007); README, JUSTIFICACAO-FERRAMENTA e LIMITACOES; revisão de segredos.
- Revisão de segredos: nenhuma chave de API nos ficheiros; o único material criptográfico commitado é `app/debug.keystore` (autorizado).

---

# Relatório final

> **Estado de verificação:** o projeto **não foi compilado** neste ambiente (sem JDK nem Android SDK). "Implementado" significa
> "código escrito para o requisito"; a compilação e os testes unitários só ficam confirmados no primeiro build verde do GitHub Actions,
> e os comportamentos de hardware só nos testes em telemóvel (README, "Testes no telemóvel").

| ID | Estado | Evidência |
|---|---|---|
| RF-CAP-001 | Parcial (AAC, não PCM) | `data/audio/ServicoGravacao.kt` |
| RF-CAP-002 | Implementado (a medir no telemóvel) | `ServicoGravacao.iniciarSessao` |
| RF-CAP-003 | Implementado | `ServicoGravacao` (notificação), `ui/gravacao/EcraGravacao.kt` |
| RF-CAP-004 | Implementado | FGS microfone + wake lock, `AndroidManifest.xml` |
| RF-CAP-007 | Implementado | ADTS + `RepositorioGravacao.recuperarSessoesInterrompidas` |
| RF-CAP-008 | Implementado | `ServicoGravacao.tratarModo` |
| RF-CAP-009 | Implementado | `ui/aviso/EcraAviso.kt`, `EspacoLivre` |
| RF-CAP-010 | Implementado | `ServicoGravacao.pausar/retomar`, marcas em `RepositorioGravacao` |
| RNF-007 | Parcial | `recuperarSessoesInterrompidas` |
| RF-ASR-001 | Implementado | `data/ServicoTranscricao.kt`, `data/openai/ClienteOpenAI.kt` |
| RF-ASR-004 | Parcial (por segmento) | `domain/transcricao/MontagemTranscricao.kt` |
| RF-ASR-007 | Implementado (pelo modelo) | — |
| RF-ASR-012 | Implementado | `ui/revisao/EcraRevisao.kt`, `RepositorioRevisao.editarTexto` |
| RF-ASR-013 | Implementado | `EdicaoSegmentoEntity`, histórico no diálogo de edição |
| RF-DIA-001 | Implementado (rótulos por parte) | `MontagemTranscricao.montar` |
| RF-DIA-002 | Implementado (pelo modelo) | — |
| RF-DIA-006 | Implementado | `OradorEntity.participanteId = null` ("Anónimo") |
| RF-DIA-007 | Implementado | `RepositorioRevisao.associar`, `reatribuirSegmento` |
| RF-DIA-010 | Cumprido por conceção | `PromptActa.INSTRUCOES` (regra 10); não há análise de voz |
| RF-DIA-011 | Implementado | `MontagemTranscricao.tempoPorOrador`, `EcraRevisao` |
| RF-ACT-001 | Implementado | `RepositorioActas.gerar` |
| RF-ACT-002 | Implementado | `DocumentoActa`, `Cabecalho.construir`, `EcraActa`, `GeradorPdf` |
| RF-ACT-003 | Implementado | `Verificador.verificar` (prazo ISO), `EsquemaActa` |
| RF-ACT-004 | Implementado | `Verificador.resolverResponsavel` + testes |
| RF-ACT-005 | Implementado | âncoras em `Verificador.verificarCitacao` |
| RF-ACT-006 | Implementado | `Verificador` + `VerificadorTest` |
| RF-ACT-007 | Implementado | acta criada em RASCUNHO; aprovação manual |
| RF-ACT-008 | Implementado (sem "arquivada") | `domain/ciclo/CicloActa.kt` + `CicloActaTest` |
| RF-ACT-009 | Implementado | `CicloActa.aprovar`, `HashActa` |
| RF-ACT-010 | Implementado | `MENCAO_IA`, `FaixaAvisoIA`, rodapé do PDF |
| RF-ACT-012 | Parcial (só PDF) | `data/pdf/GeradorPdf.kt` |
| RF-ACT-014 | Implementado | `Verificador.limitarPalavras`, `PromptActa` |
| RF-ACT-015 | Implementado | `MontagemTranscricao.falaSuficiente`, `EcraRevisao` |
| RF-ACT-016 | Implementado | `Versionamento`, `RepositorioActas.guardar`, seletor de versões |
| RF-PER-003 | Implementado | `ActaDao.pesquisar`, `EcraInicio` |
| RF-PER-007 | Implementado | `allowBackup=false`, `res/xml/*_rules.xml` |
| RF-PER-010 | Implementado | `HashActa.sha256` + teste com vetor conhecido |
| RF-PAR-001 | Implementado | `Participante`, `DialogoParticipante` |
| RF-PAR-002 | Implementado | `ValidacaoParticipante` + `ValidacaoTest` |
| RF-PAR-003 | Implementado | erros por campo em `DialogoParticipante` |
| RF-PAR-004 | Implementado | `Presenca`, `SeletorPresenca` |
| RF-PAR-006 | Parcial (gravação) | `Participacao.consentimento/consentimentoEmMs` |
| RF-PAR-007 | Implementado | `Presencas.presentesSemConsentimento`, `EcraAviso`, `EcraReuniao` |
| RF-PAR-009 | Implementado | `Presencas.destinatariosPorOmissao` |
| RF-PAR-010 | Implementado | `EcraDistribuir` (adicionar destinatário) |
| RF-DIS-001 | Implementado | `EcraDistribuir`, `Partilha.email` |
| RF-DIS-002 | Implementado | pré-visualização e botão "Confirmo" |
| RF-DIS-003 | Implementado | `CicloActa.distribuir` + teste; `EcraDistribuir` |
| RF-DIS-004 | Implementado | `Partilha.email`, `TextoActa.corpoEmail` |
| RF-DIS-007 | Parcial (confirmação manual) | `RepositorioActas.registarDistribuicao` |
| RF-DIS-010 | Implementado | `Partilha.partilhar` |
| RF-TTS-001 | Implementado | `ui/acta/LeitorVoz.kt`, `DialogoVoz` |
| RF-TTS-002 | Implementado | sliders em `DialogoVoz` |
| RF-TTS-006 | Implementado (a validar com TalkBack) | `USAGE_MEDIA`, só a pedido |
| RF-SEG-001 | Implementado | `EcraAviso` |
| RF-SEG-002 | Implementado | `ServicoGravacao.sinalSonoro`, "● A gravar" |
| RF-SEG-003 | Implementado | `MainActivity` (BiometricPrompt) |
| RF-SEG-012 | Implementado | `Contentor.apagarTodosOsDados`, `EcraDefinicoes` |
| RF-INT-007 | Implementado | `ui/ajuda/EcraAjuda.kt` |
| RF-PLT-001 | Implementado | Kotlin + Compose, minSdk 26, targetSdk 37 |
| RF-PLT-003 | Implementado | `foregroundServiceType="microphone"` |
| RF-PLT-010 | Implementado (a validar) | tipografia M3 em sp, `TemaActa` |
| RF-PLT-011 | Implementado (previsto) | sem modelos locais |
| Agendar próxima reunião | Implementado | `Partilha.calendario`, `DialogoCalendario` |
| Modo demonstração | Implementado | `data/demo/DadosDemo.kt` |

## Problemas conhecidos
1. **Não compilado localmente.** Podem surgir erros de compilação no primeiro build do Actions (sobretudo na combinação
   AGP 9.4 com Kotlin integrado, KSP e Room, que é recente). Corrigir a partir do log do Actions.
2. `debug.keystore` gerado com `openssl` (PKCS12 com AES-256/PBKDF2). O JDK 17 lê este formato, mas se a assinatura falhar,
   gerar outro com o `keytool` (comando no README).
3. Perda de cerca de 0,1 a 0,3 s em cada mudança de parte.
4. Abaixo do Android 12 a deteção de chamadas faz uma verificação por segundo (pode perder o primeiro segundo da chamada).
5. Os commits intermédios (Fases 1 a 7) referenciam classes das fases seguintes; só o estado final foi pensado para compilar.

## Próximos passos
1. Enviar para o GitHub, obter o primeiro build verde e corrigir eventuais erros de compilação.
2. Fazer os testes em telemóvel do README (G1–G6, T1–T2, A1, E1, C1, V1) e registar os resultados aqui.
3. Cifrar a base de dados (SQLCipher) e definir retenção (RF-PER-001/002).
4. Exportação PDF/A-2b e DOCX; registo de auditoria com encadeamento de hashes (RF-SEG-005).
5. Testes instrumentados (Room, conversão ADTS→M4A com um ficheiro real).
