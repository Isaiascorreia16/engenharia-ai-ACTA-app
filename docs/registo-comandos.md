# Registo de comandos

Só os comandos executados de facto, com a sua finalidade.

| # | Comando | Finalidade |
|---|---|---|
| 1 | `which java keytool gradle git gh`; `java -version`; `ls` de pastas de JDK e SDK Android | Verificar as ferramentas disponíveis. Resultado: sem Java, `keytool`, Gradle, SDK e `gh` |
| 2 | `curl -sS -o /dev/null -w "%{http_code}" <Google Maven / Maven Central / Gradle / OpenAI>` | Verificar o acesso à rede para dependências e documentação |
| 3 | `curl -s <repositório>/maven-metadata.xml` (AGP, Compose BOM, Room, DataStore, OkHttp, Kotlin, KSP, …) | Obter as últimas versões estáveis de cada biblioteca |
| 4 | `curl -s https://services.gradle.org/versions/current` | Saber a versão atual do Gradle |
| 5 | `curl -s .../frameworks/av/.../MediaWriter.h?format=TEXT \| base64 -d \| grep setNextFd` (e `AACWriter.cpp`, `MPEG4Writer.cpp`) | Confirmar no AOSP que `setNextOutputFile` não é suportado em ADTS |
| 6 | `curl -s .../gradle-9.4.1.pom \| grep kotlin` | Saber que versão do Kotlin Gradle Plugin o AGP 9.4.1 traz |
| 7 | `curl -s .../compose-compiler-gradle-plugin-2.4.20.pom` | Confirmar que o plugin Compose 2.4.20 traz o KGP 2.4.20 (versões alinhadas) |
| 8 | `openssl version` | Verificar se havia `openssl` como alternativa ao `keytool` |
| 9 | `openssl req -x509 -newkey rsa:2048 -sha256 -days 10950 -nodes -subj "/C=US/O=Android/CN=Android Debug" ...` | Criar o certificado e a chave de debug (os mesmos dados do Android) |
| 10 | `openssl pkcs12 -export -name androiddebugkey -passout pass:android ... -out app/debug.keystore` | Empacotar a chave num keystore PKCS12 com as credenciais debug convencionais |
| 11 | `openssl pkcs12 -info -in app/debug.keystore -passin pass:android -nokeys` | Verificar o alias e os algoritmos do keystore |
| 12 | `git init -b main`; `git config user.name/user.email` (local) | Criar o repositório local para os commits por fase |
| 13 | `git add -A && git commit -m "..."` (um por fase) | Registar cada fase no histórico |
| 14 | `curl -s .../fragment-ktx/maven-metadata.xml`; `curl -s .../biometric-1.1.0.pom` | Ver que o biometric traz o fragment 1.2.5 e escolher uma versão recente |
| 15 | `awk -f imports.awk $(find . -name "*.kt")` | Verificação estática de imports em falta (substitui a compilação, que não era possível) |
| 16 | `grep` por padrões `sk-…` e `Bearer …` | Revisão de segredos antes dos commits |
| 17 | `git archive --format=zip -o ../acta-projeto.zip HEAD` | Empacotar o projeto num ZIP para upload manual |
