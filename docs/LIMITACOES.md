# Limitações do MVP

## Requisitos fora do MVP (com motivo)

| Requisitos | Motivo |
|---|---|
| RF-ASR-002, RF-ASR-003, RNF-001 (tempo real) | A transcrição é feita em lote, depois da reunião, com um pedido por parte gravada. |
| RF-ASR-009, RF-MOD-006, RNF-008 (processamento local e privacidade reforçada) | O áudio é enviado para a OpenAI. Não há modelos locais (manteria o APK abaixo de 150 MB, RF-PLT-011). |
| RF-ASR-005, RF-ASR-006 (confiança por segmento) | Confirmado na documentação a 2026-09-24: o modo `diarized_json` não devolve confiança nem logprobs (o `include[]=logprobs` só existe para `gpt-4o-transcribe` e `gpt-4o-mini-transcribe`). |
| RF-TRA-001 a 008 (tradução) | Fora do âmbito. O crioulo cabo-verdiano também não é suportado de forma fiável pelo modelo. |
| RF-DIA-004, 005, 009, RF-SEG-006, RNF-004 (impressões vocais e biometria de voz) | Todos os oradores começam anónimos; a associação é manual. Evita tratar dados biométricos. |
| RF-MOD-001 a 007 (modos de operação) | O MVP funciona sempre como o modo M4 (arquivo e distribuição). |
| RF-PER-001, 002, 004, 005, RNF-009 (SQLCipher e retenção) | A base de dados Room não é cifrada e não há política de retenção automática. A chave da API é cifrada; os dados estão excluídos das cópias de segurança; há "Apagar todos os dados". |
| RF-SEG-005 (auditoria com encadeamento de hashes) | Só há hash da acta aprovada e registo de edições de segmentos, sem cadeia. |
| RF-PLT-002, 004, 007, RNF-016 (iOS e paridade) | Só Android. |
| RF-DIS-006, RF-DIS-008 (envio automático com repetição) | O envio é feito pelo cliente de e-mail do utilizador; a app pergunta se o e-mail foi enviado. |
| Restantes requisitos S e C não listados como incluídos | Fora do âmbito do MVP. |

## Desvios e limitações dos requisitos incluídos

- **RF-CAP-001:** grava em AAC (ADTS, 64 kbps) em vez de PCM, por causa do limite de 25 MB por pedido da API.
- **Partes de gravação:** o `setNextOutputFile` não funciona em ADTS (o `AACWriter` do Android não implementa `setNextFd`, como se pode confirmar no AOSP).
  A rotação faz stop e novo `MediaRecorder`, o que perde cerca de 0,1 a 0,3 s por mudança de parte (de 5 em 5 minutos).
  A mudança fica registada na linha temporal.
- **Duração das partes (5 min):** a documentação não publica uma duração máxima por pedido, mas o `gpt-4o-transcribe-diarize` tem
  um máximo de 2 000 tokens de saída; 5 minutos de fala em português dão margem.
- **Parâmetro `language`:** não está documentado para o modelo diarizado, por isso não é enviado. A língua da reunião fica só como metadado.
- **RF-CAP-008 (chamadas):** deteção pelo modo do `AudioManager` (RINGTONE, IN_CALL, IN_COMMUNICATION…), sem `READ_PHONE_STATE`.
  Também pausa em chamadas VoIP (WhatsApp, Teams) feitas no próprio telemóvel. Abaixo do Android 12 a deteção é feita a cada segundo.
- **RF-CAP-007 / RNF-007:** se o processo morre, preserva-se o áudio até ao último bloco escrito. A sessão é recuperada ao reabrir a app,
  com a duração estimada pela hora de escrita do ficheiro e corrigida na conversão.
- **RF-ASR-004:** marcas temporais por segmento, não por palavra.
- **RF-DIA-001:** os rótulos de orador valem só dentro de cada parte ("P1-A" e "P2-A" podem ser pessoas diferentes).
- **RF-ACT-006 (verificação):** garante que as citações existem nos segmentos indicados e que as âncoras temporais vêm dos segmentos.
  **Não garante** que o resumo do item diga o mesmo que a citação, nem que a transcrição esteja correta. Por isso a aprovação é humana.
- **RF-ACT-012:** exportação só em PDF gerado com `PdfDocument` (não é PDF/A-2b; sem DOCX nem Markdown).
- **RF-DIS-007:** o registo da expedição depende da confirmação manual ("O e-mail foi enviado?").
- **RF-SEG-003:** o bloqueio é pedido ao abrir a app, não ao regressar de outra app, para não interromper o envio de e-mail.
  Se o telemóvel não tiver bloqueio de ecrã configurado, a app abre sem autenticação.
- **RF-TTS-002:** as vozes disponíveis dependem do motor instalado no telemóvel.
- **RF-INT-007 (videoconferências):** só se grava o microfone. Uma videoconferência no próprio telemóvel ocupa o microfone e a gravação pausa;
  para a gravar, deve usar-se outro dispositivo com altifalante (documentado no ecrã de Ajuda).
- **Custos:** a transcrição e a geração de acta são pagas à OpenAI pela conta do utilizador.
- **Build:** o projeto foi escrito num ambiente sem JDK nem Android SDK; a compilação e os testes correm no GitHub Actions.
