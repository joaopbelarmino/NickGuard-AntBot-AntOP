# NickGuard 2.0.1 -> 2.0.2

Correcoes de tres pendencias encontradas na revisao da 2.0.1.

## 1. Anti-LuckPerms contornavel por aliases

O LuckPerms declara `aliases: [lp, perm, perms, permission, permissions]` no `plugin.yml`, mas o guard so reconhecia `lp` e `luckperms`. Comandos como `/perm user X parent set admin` ou `/perms editor` passavam sem verificacao, inclusive pelo console.

- O comando e identificado pelo dono real no command map (`PluginIdentifiableCommand` do plugin LuckPerms), o que cobre aliases customizados e formas `luckperms:<alias>`.
- Sem dono resolvido, vale a lista fixa dos seis labels.
- Um comando `perm` pertencente a outro plugin nao e tratado como LuckPerms.
- O subcomando de permissao aceita `permission`, `perm` e `perms`.

## 2. Alertas reais podiam ser escondidos

O limite global de 1 alerta a cada 5 s valia para todos os tipos e tambem para o log do console. Um jogador enviando `/pl` a cada 5 s ocupava a vaga, e alertas de 2FA, identidade ou permissao perigosa eram descartados sem registro.

- O log do console registra todos os alertas, sem limite.
- `alerts.minimum-interval-seconds` agora vale por categoria (`2fa`, `identity`, `dangerous-permission`, `client-signature`, `raid-block`, `raid-mode`, `blocked-command`) e so para chat e Discord.
- Comando bloqueado gera no maximo 1 alerta por jogador a cada `blocked-commands.alert-cooldown-seconds` (padrao 10), com a contagem de tentativas suprimidas.
- O Discord continua com um envio por vez e respeita o 429; mensagens nao enviadas sao contadas no proximo envio.

## 3. Excecoes administrativas fora do ADM_2FA

Nicks em `security-admins`, `anti-op.allowed`, `anti-luckperms-wildcard.allowed` ou `anti-gamemode-creative.allowed` que nao estao em `ADM_2FA` eram liberados so pelo nick, sem aviso.

- Ao iniciar e no `/nickguard reload`, o console lista esses nicks.
- `require-2fa-for-exceptions: true` faz esses nicks perderem a excecao. O padrao e `false` para nao trancar configuracoes existentes.

## Validacao

`./mvnw verify`: 13 testes (4 novos em `HardeningTest`). Nao foi testado em servidor Paper real nem com LuckPerms carregado; o caminho do command map depende do servidor e so a lista fixa e coberta pelos testes.
