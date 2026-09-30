# NickGuard AntBot & AntOP

Plugin de seguranca para servidores Minecraft Paper, com protecao de identidade, 2FA administrativo, anti-OP, controle de permissoes perigosas e mitigacoes anti-bot/anti-raid.

## Projeto

- Versao atual: `2.0.2` (base reconstruida: `2.0.0`)
- API: Paper `1.21.4`
- Java: `21`
- Build: Maven
- Classe principal: `me.zetra.nickguard.NickGuardPlugin`

O codigo-fonte foi reconstruido a partir do binario fornecido pelo administrador e validado por recompilacao. A arvore atual nao inclui JARs; o historico antigo pode conter o binario inicialmente enviado.

## Compilar

```bash
./mvnw clean package
```

No Windows, use `.\mvnw.cmd clean package`.

O arquivo compilado sera gerado em `target/NickGuard-2.0.2.jar`. Use `./mvnw verify` para executar os testes. O CI valida o fonte sem publicar binarios.

## Correcoes 2.0.2

Veja [docs/CORRECOES-2.0.2.md](docs/CORRECOES-2.0.2.md).

- Anti-LuckPerms reconhece todos os aliases (`/perm`, `/perms`, `/permission`, `/permissions`, namespaced e aliases customizados).
- Log do console registra todos os alertas; o limite de chat/Discord e por categoria, entao spam de comando bloqueado nao esconde alertas de 2FA/identidade.
- Aviso no console para nicks com excecao administrativa fora de `ADM_2FA`; `require-2fa-for-exceptions: true` remove essas excecoes.

## Auditoria e migracao 2.0.1

Leia [o relatorio completo](docs/AUDITORIA-2.0.1.md) antes de instalar. Ele distingue falhas confirmadas, exageros e limites ainda existentes.

- Listas vazias agora significam nenhum administrador, sem nomes embutidos como fallback.
- Cadastro/reset 2FA somente pelo console: `redefine <nick>`. O secret fica em `admin2fa.yml`; entregue ao titular por canal privado. Nao e mostrado automaticamente no chat ou console.
- Tentativas, cooldown e ultimo intervalo TOTP usado sao persistidos. Reload exige nova validacao.
- `admin-identities` permite fixar UUID por nick lowercase; `require-admin-uuid: true` torna esse cadastro obrigatorio para as excecoes administrativas. Primeiro configure e teste seus UUIDs reais.
- Historicos de nick ambiguos sao bloqueados sem escolher um dono automaticamente. `/remover` exige alvo offline e persiste uma exclusao do UUID.
- Configuracoes existentes nao sao sobrescritas. Compare com `src/main/resources/config.yml` antes de migrar.

## Estrutura

- `src/main/java`: codigo-fonte do plugin.
- `src/main/resources/plugin.yml`: comandos, permissoes e metadados Paper.
- `src/main/resources/config.yml`: configuracao padrao de seguranca.
- `src/main/resources/admin2fa.yml`: estrutura inicial da persistencia 2FA, sem secrets.

## Seguranca

Arquivos de runtime, secrets 2FA, bloqueios persistidos, backups de UUID e logs nao devem ser enviados ao Git. Antes de atualizar o servidor, faca backup de `plugins/NickGuard`.
