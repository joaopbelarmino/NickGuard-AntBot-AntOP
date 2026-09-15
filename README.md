# NickGuard AntBot & AntOP

Plugin de seguranca para servidores Minecraft Paper, com protecao de identidade, 2FA administrativo, anti-OP, controle de permissoes perigosas e mitigacoes anti-bot/anti-raid.

## Projeto

- Versao base: `2.0.0`
- API: Paper `1.21.4`
- Java: `21`
- Build: Maven
- Classe principal: `me.zetra.nickguard.NickGuardPlugin`

O codigo-fonte foi reconstruido a partir do binario oficial usado pelo servidor e validado por recompilacao. O binario original nao e armazenado neste repositorio.

## Compilar

```bash
./mvnw clean package
```

No Windows, use `.\mvnw.cmd clean package`.

O arquivo compilado sera gerado em `target/NickGuard-2.0.0.jar`.

## Estrutura

- `src/main/java`: codigo-fonte do plugin.
- `src/main/resources/plugin.yml`: comandos, permissoes e metadados Paper.
- `src/main/resources/config.yml`: configuracao padrao de seguranca.
- `src/main/resources/admin2fa.yml`: estrutura inicial da persistencia 2FA, sem secrets.

## Seguranca

Arquivos de runtime, secrets 2FA, bloqueios persistidos, backups de UUID e logs nao devem ser enviados ao Git. Antes de atualizar o servidor, faca backup de `plugins/NickGuard`.
