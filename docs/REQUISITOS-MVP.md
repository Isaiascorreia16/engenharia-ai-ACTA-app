# Requisitos do MVP

Fonte: especificação EF-ACTA-2026-001 v1.0, tal como resumida na instrução do projeto.

Prioridade da especificação: M = obrigatório, S = desejável, C = facultativo.

### Incluídos no MVP

| ID | Resumo | Pri. | Fase | Nota |
|---|---|---|---|---|
| RF-CAP-001 | Captura de áudio do microfone | M | 3 | Desvio: AAC em vez de PCM (limite de tamanho da API) |
| RF-CAP-002 | Iniciar a captura em ≤ 500 ms | M | 3 | |
| RF-CAP-003 | Indicador persistente de gravação | M | 3 | |
| RF-CAP-004 | Captura em segundo plano e com ecrã bloqueado | M | 3 | |
| RF-CAP-007 | Revogação de permissão: preservar o material | M | 3 | O Android mata a app; o formato ADTS preserva o gravado |
| RF-CAP-008 | Interrupção por chamada: marcar e retomar | M | 3 | Depende do mecanismo a confirmar |
| RF-CAP-009 | Bloquear início com menos de 500 MB livres | M | 3 | |
| RF-CAP-010 | Pausar com marca temporal | M | 3 | |
| RNF-007 | Preservar o material em terminação anómala | — | 3 | Parcial: preserva o áudio; a transcrição é posterior |
| RF-ASR-001 | Transcrição automática | M | 4 | Em lote, após a reunião |
| RF-ASR-004 | Marcas temporais | M | 4 | Parcial: por segmento, não por palavra |
| RF-ASR-007 | Pontuação e capitalização | M | 4 | Feitas pelo modelo |
| RF-ASR-012 | Edição manual de segmentos | M | 5 | |
| RF-ASR-013 | Registo da edição (autor, instante, conteúdo anterior) | M | 5 | |
| RF-DIA-001 | Segmentação por orador | M | 4 | Rótulos por parte gravada |
| RF-DIA-002 | Diarização sem saber o número de oradores | M | 4 | |
| RF-DIA-006 | Rótulo anónimo quando não identificado | M | 4 | Sem impressões vocais, todos começam anónimos |
| RF-DIA-007 | Reatribuição manual com propagação | M | 5 | |
| RF-DIA-010 | Proibição de inferir emoções | M | — | Cumprido por conceção |
| RF-DIA-011 | Tempo de intervenção por orador | S | 5 | |
| RF-ACT-001 | Gerar acta a pedido | M | 6 | |
| RF-ACT-002 | Rubricas mínimas da acta | M | 6 | |
| RF-ACT-003 | Extração de ações (responsável, descrição, prazo ISO 8601) | M | 6 | Plano de ações do enunciado |
| RF-ACT-004 | Ação sem responsável → "Por atribuir" | M | 6 | |
| RF-ACT-005 | Ancoragem ao excerto da transcrição | M | 6 | |
| RF-ACT-006 | Nada na acta sem excerto que o sustente | M | 6 | Verificação por citação literal |
| RF-ACT-007 | Rascunho até aprovação humana | M | 1, 6 | |
| RF-ACT-008 | Ciclo de vida documental | M | 1 | Sem o estado "arquivada" |
| RF-ACT-009 | Fixação e hash na aprovação | M | 1, 7 | |
| RF-ACT-010 | Menção obrigatória de IA | M | 6, 7 | |
| RF-ACT-012 | Exportação | M | 7 | Parcial: só PDF (não PDF/A-2b, DOCX nem Markdown) |
| RF-ACT-014 | Súmula executiva ≤ 250 palavras | S | 6 | Resumo do enunciado |
| RF-ACT-015 | Recusar acta com menos de 60 s de fala | S | 6 | |
| RF-ACT-016 | Preservar versões anteriores | S | 1, 7 | |
| RF-PER-003 | Pesquisa textual | S | 8 | |
| RF-PER-007 | Excluir das cópias de segurança automáticas | M | 0 | |
| RF-PER-010 | SHA-256 da acta aprovada | M | 7 | |
| RF-PAR-001 | Atributos do participante | M | 2 | Telefone e organização opcionais |
| RF-PAR-002 | Validar e-mail (e telefone E.164, se preenchido) | M | 2 | |
| RF-PAR-003 | Recusar registo inválido indicando o campo | M | 2 | |
| RF-PAR-004 | Estado de presença | M | 2 | |
| RF-PAR-006 | Consentimento registado | M | 2 | Parcial: só gravação |
| RF-PAR-007 | Advertência por falta de consentimento | M | 2 | |
| RF-PAR-009 | Lista de distribuição a partir dos presentes | M | 7 | |
| RF-PAR-010 | Destinatários adicionais | S | 7 | |
| RF-DIS-001 | Distribuir acta aprovada | M | 7 | Pelo cliente de e-mail do telemóvel |
| RF-DIS-002 | Pré-visualização e confirmação | M | 7 | |
| RF-DIS-003 | Impedir distribuição fora de "aprovada" | M | 1, 7 | |
| RF-DIS-004 | E-mail com anexo e súmula | M | 7 | |
| RF-DIS-007 | Registo da expedição | M | 7 | Parcial: confirmação manual |
| RF-DIS-010 | Partilha nativa | S | 7 | |
| RF-TTS-001 | Ouvir a acta em voz sintetizada | M | 8 | Android `TextToSpeech`, pt |
| RF-TTS-002 | Escolher velocidade e volume | S | 8 | Voz depende das instaladas |
| RF-TTS-006 | Não interferir com o leitor de ecrã | M | 8 | |
| RF-SEG-001 | Aviso e confirmação antes de gravar | M | 2 | Quatro caixas obrigatórias |
| RF-SEG-002 | Sinal sonoro e visual ao iniciar | M | 3 | |
| RF-SEG-003 | Autenticação para aceder ao arquivo | M | 8 | Bloqueio ao abrir a app |
| RF-SEG-012 | Apagar todos os dados | M | 8 | |
| RF-INT-007 | Documentar limitações de captura em videoconferência | S | 8 | No ecrã de ajuda |
| RF-PLT-001 | Kotlin + Jetpack Compose | M | 0 | API 26 até à mais recente |
| RF-PLT-003 | Foreground service do tipo microfone | M | 3 | |
| RF-PLT-010 | Respeitar tamanho de letra e contraste do sistema | M | todas | |
| RF-PLT-011 | Pacote abaixo de 150 MB | S | — | Sem modelos locais |
| — | Agendar próxima reunião no calendário | — | 7 | Pedido no enunciado; não está na especificação |
| — | Modo demonstração | — | 8 | Para avaliação sem chave de API |

### Fora do MVP (registar em LIMITACOES.md com o motivo)

- **Tempo real:** RF-ASR-002, RF-ASR-003 e RNF-001. A transcrição é em lote.
- **Processamento local e privacidade reforçada:** RF-ASR-009, RF-MOD-006 e RNF-008. O áudio é enviado para a OpenAI.
- **Confiança por segmento:** RF-ASR-005 e RF-ASR-006. Confirmar se a resposta do modelo a fornece; se sim, passa a incluída.
- **Tradução:** RF-TRA-001 a 008. O crioulo cabo-verdiano também não é suportado de forma fiável.
- **Impressões vocais e biometria de voz:** RF-DIA-004, 005 e 009, RF-SEG-006 e RNF-004.
- **Modos de operação configuráveis:** RF-MOD-001 a 007. O MVP funciona sempre como o modo M4 (arquivo e distribuição).
- **Cifra da base de dados (SQLCipher) e retenção:** RF-PER-001, 002, 004, 005 e RNF-009.
- **Registo de auditoria com encadeamento de hashes:** RF-SEG-005.
- **Plataforma iOS e paridade:** RF-PLT-002, 004, 007 e RNF-016.
- **Envio automático de e-mail com repetição:** RF-DIS-006 e RF-DIS-008. O envio é feito pelo cliente de e-mail do utilizador.
- **Restantes requisitos S e C** não listados como incluídos.
