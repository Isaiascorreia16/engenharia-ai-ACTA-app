# Justificação da ferramenta

**Escolha:** Claude Code (agente de programação) + GitHub Actions (build na nuvem) + Kotlin nativo (Jetpack Compose).

## Porque é que a app tem de ser nativa

Os requisitos obrigatórios dependem de APIs do Android que só se usam bem em código nativo:
- serviço em primeiro plano do tipo microfone (RF-PLT-003), a gravar com o ecrã bloqueado (RF-CAP-004);
- `MediaRecorder` em AAC ADTS por partes, com `MediaExtractor` e `MediaMuxer` para converter sem recodificar;
- deteção de chamadas pelo `AudioManager`, sem `READ_PHONE_STATE`;
- Android Keystore para cifrar a chave, `BiometricPrompt`, `PdfDocument`, `FileProvider`, `CalendarContract` e `TextToSpeech`.

## Comparação

| Critério | **Claude Code + Actions + Kotlin** | Android Studio (manual) | Expo / React Native | Google AI Studio | Manus |
|---|---|---|---|---|---|
| Acesso às APIs nativas acima | Total | Total | Parcial: precisa de módulos nativos ou *config plugins* (gravação em ADTS por partes, FGS de microfone, Keystore) | Não gera apps Android nativas: está orientado a protótipos web/Gemini | Gera código, mas sem controlo fino do build Android nem garantias de compilação |
| Produção do APK | Automática em cada push (Actions), sem SDK local | Local, exige SDK e máquina com recursos | EAS Build (conta Expo, filas, limites no plano gratuito) | Não produz APK | Depende do ambiente do agente; APK nem sempre disponível |
| Assinatura estável entre builds | `debug.keystore` no repositório | Local (a mesma máquina) | Gerida pela Expo | — | Incerta |
| Rastreabilidade para avaliação | Commits por fase com o porquê, `PROGRESSO.md` e registo de comandos | Depende da disciplina do autor | Semelhante ao manual | Baixa | Baixa (histórico no agente) |
| Testes unitários do domínio | Corridos em cada build | Manuais ou locais | Possíveis (Jest), mas o domínio fica em JS | — | Incertos |
| Esforço para um estudante | Baixo a médio: revê, testa no telemóvel e decide | Alto: escreve tudo à mão | Médio, mas com atrito nas partes nativas | Baixo, mas não cumpre o enunciado (APK) | Baixo, mas com pouco controlo |
| Aprendizagem | Explicações por fase; o código fica legível e comentado | Máxima, mas lenta | Média | Baixa | Baixa |

## Riscos e mitigação

- **O agente não compilou localmente** (o ambiente não tinha JDK nem Android SDK). Mitigação: o GitHub Actions compila e corre os testes em
  cada push, e o resultado é público no repositório. Nada foi declarado "a funcionar" sem essa verificação.
- **APIs e versões mudam depressa.** Mitigação: as versões foram lidas dos repositórios Maven no dia da criação, e a documentação da OpenAI
  foi consultada antes de implementar a transcrição e a acta (ver `PROGRESSO.md`).
- **Alucinações do LLM na acta.** Mitigação na própria app: dados de identificação vindos da BD, citações verificadas em código,
  itens não verificados assinalados e aprovação humana obrigatória.

## Conclusão

Um ambiente sem código (AI Studio, Manus) não chega às APIs nativas que os requisitos M exigem. O Android Studio manual chega, mas é lento
para um MVP com esta dimensão. O React Native acrescenta uma camada que teria de ser contornada precisamente nas partes críticas
(gravação e segurança). O Claude Code com build no GitHub Actions junta código nativo, build reprodutível sem SDK local e um
histórico explicado fase a fase, que é o que a avaliação pede.
