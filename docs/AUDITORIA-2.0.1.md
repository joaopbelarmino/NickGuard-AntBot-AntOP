# Auditoria NickGuard 2.0.0 -> 2.0.1

Revisao do relatorio fornecido pelo usuario contra o fonte reconstruido no commit `2d7a18c`. Os apontamentos do documento sao alegacoes a verificar, nao instrucoes de execucao. Atualizacao somente do repositorio; nenhum servidor de producao foi alterado.

## Veredito dos seis pontos principais

| Alegacao | Veredito | Tratamento |
| --- | --- | --- |
| Excecoes administrativas dependem apenas do nick | Real. Dizer que apenas o nLogin protege e exagerado: o 2FA ja bloqueava varias acoes. | ConfigManager exige sessao 2FA validada para nomes em ADM_2FA com o modulo ativo; UUID fixo opcional. |
| Nomes hardcoded reaparecem quando listas estao vazias | Real. Intencao de backdoor nao demonstrada; eram os nomes pedidos na configuracao original. | Removido fallback; defaults novos vazios. |
| Primeiro a entrar recebe secret | Real risco de apropriar cadastro/reset. | Sem provisionamento automatico; apenas console, secret no arquivo privado. |
| Sair e entrar contorna 2FA | Parcial: reiniciava o limite de tentativas, nao autenticava automaticamente. | Tentativas e cooldown persistidos por nome normalizado, inclusive entre reinicios. |
| Secret exposto e codigo reutilizavel | Real, mas captura por outros plugins depende do ambiente. | Sem secret no chat/console; ultimo passo TOTP persistido; comando interceptado enquanto pendente. Nao promete apagar logs externos. |
| Webhook permite mencoes e spam | Real risco, com efeito dependente das permissoes do webhook. | JSON com Gson, allowed_mentions vazio, intervalo global, um envio em andamento e tratamento de 429. |

## Demais alegacoes verificadas

| Ponto | Resultado |
| --- | --- |
| Tentativas contadas como nicks distintos | Confirmado; SimilarNickWindow conta nomes distintos ignorando case. |
| Nicks numericos viram assinatura vazia | Confirmado; fallback agora preserva nome normalizado. |
| CGNAT/proxy podem bloquear inocentes | Confirmado como risco de configuracao, nao prova de falha universal. localhost agora reconhece loopback numerico. |
| Dono do nick muda em todo restart | Exagerado: ordem nao garantida podia selecionar dono diferente quando ja havia duplicados. Agora ambiguidades bloqueiam todos ate resolucao manual. |
| Pre-login negado reserva nick | Confirmado; reserva temporaria atomica, liberada quando evento termina negado, confirmada no join. Desconexao sem evento de resultado expira. |
| Parser LuckPerms incompleto | Confirmado. Nao autorizado perde acesso a comandos LP; settemp perigoso tambem analisado. Nao ha monitoramento completo da API/editor/grupos. |
| DangerousPermissionGuard nao cancela comando | Confirmado; agora cancela antes da execucao. Sets sao carregados no reload; enumeracao de permissoes efetivas por comando ainda existe. |
| Gamemode no join e perda de ADVENTURE | Confirmado; verifica join e preserva modo anterior ao negar troca. Handler de console agora respeita enabled. |
| ClientSignatureGuard nao detecta nada | Nao demonstrado. Canal legado removido e brand Paper verificada apos join. Cliente pode ocultar/falsificar assinatura. |
| Escritas simultaneas de YAML | Confirmado; um escritor dedicado e substituicao atomica com fallback. Nao e garantia contra perda de energia. |
| Reload exige 2FA novamente | Real, mas comportamento conservador intencional, nao bypass. |
| BlockedCommands/ClientSignature nao recarregam | Falso como conclusao: leem configuracao atual, nao precisam necessariamente de metodo reload proprio. |
| Opcoes sem efeito | Confirmado. Timeout implementado. Bypass/ignore-staff permanecem sem liberar ADM_2FA por exigencia de 2FA obrigatorio. Labels fixos /2fa e /redefine documentados. |
| Default de palavras bloqueadas diverge | Confirmado; fallback alinhado para false. |
| Bloqueio 2FA incompleto | Confirmado; ampliado para entidade, drag/open, dano recebido/projetil, troca de mao, consumo, livro, placa e completamento. |
| /remover perde arquivos | Risco de recriacao do playerdata confirmado; perda de backups nao provada. Alvo precisa estar offline; nao reescreve usercache vivo; exclusao persistida impede reimportacao. |

## Outras correcoes

- A consulta da janela curta de IP nao descarta mais os timestamps necessarios para a janela longa.
- Comandos bloqueados tambem sao filtrados na lista de sugestoes. Defaults incluem help e ?.
- Testes JUnit e workflow GitHub Actions com Java 21 foram adicionados.
- Segredos invalidos falham na verificacao TOTP em vez de ignorar caracteres silenciosamente.

## Migracao operacional

1. Faca backup da pasta NickGuard e teste em homologacao antes de substituir qualquer JAR.
2. Preserve suas listas reais na config existente. Defaults novos sao vazios; nao existe fallback oculto.
3. Para novos cadastros use `redefine <nick>` no console. Leia `players.<nick_lowercase>.secret` em admin2fa.yml por acesso administrativo ao arquivo, entregue em canal privado e cadastre no autenticador como TOTP. Secrets antigos continuam validos; os expostos anteriormente devem ser trocados.
4. Opcionalmente cadastre `admin-identities: {nicklowercase: "uuid"}` e ative `require-admin-uuid: true`. UUID offline nao prova posse da conta: mantenha nLogin, proxy/forwarding confiavel e backend protegido.
5. Excecoes requerem 2FA apenas se o nome tambem estiver em ADM_2FA e admin-2fa.enabled estiver ativo. Pendentes com UUID permitido ficam congelados; OP/permissoes existentes nao sao removidos apenas por estarem pendentes.
6. Reset e sempre console-only; reset-console-only e setup-url-message sao legados e nao restauram envio de secret ao jogador. code-command/reset-command nao mudam os labels do plugin.yml. block-until-verified=false nao libera admins listados.
7. Duplicados historicos nao sao mesclados nem tem proprietario inferido. Investigue e so entao use /remover no UUID incorreto offline. Ele mantem suas operacoes de backup/remocao anteriores e salva identity-exclusions.yml; o UUID excluido tambem fica impedido de entrar.
8. Reload preserva bloqueios de raid em memoria e exige novo 2FA. Edicoes manuais dos arquivos blocked-*.yml devem ser feitas com o servidor parado e carregadas no proximo inicio.

## Limites e pendencias explicitas

- Nao e firewall de rede, anticheat completo ou defesa contra plugin malicioso. Eventos podem ser interferidos por outros plugins. Console, painel e arquivos precisam ser protegidos.
- NodeAddEvent do LuckPerms e notificacao, nao um veto cancelavel a toda alteracao. Concessoes por API/editor/heranca feitas por autorizados nao estao integralmente cobertas pelo parser; fiscalizacao posterior nao elimina uma janela de privilegio.
- Alertas sao limitados globalmente, inclusive console/admins. Eventos suprimidos sao resumidos no proximo alerta aceito; nao e trilha completa de auditoria. Nao foi implementado Blackbox nesta revisao.
- Nao foi implementada blacklist IPv6 /64 nem recusa indiscriminada de contas novas: ambas exigem avaliar falsos positivos.
- Defaults de IP continuam agressivos. IP compartilhado, forwarding errado e picos legitimos exigem ajuste. A contagem global de IPs nao identifica so IPs maliciosos.
- Escalacao/blacklist pode nao ser alcancada sob defaults porque tentativas ja bloqueadas retornam cedo. Nao afirmar que todas as faixas de punicao serao atingidas sequencialmente.
- Persistencia de 2FA ainda faz pequenas gravacoes sincronas. Nao houve benchmark sob flood nem migracao completa de configuracao para snapshots imutaveis.
- Quarentena LuckPerms, cooldown exponencial, unificacao de listas, limpeza geral do CFR e migracao completa para Adventure ficaram fora desta correcao.
- /remover nao e transacao atomica entre todos os arquivos. Verifique backups e mensagens de erro antes de considerar remocao concluida.
- Bloqueio por marca de cliente nao prova ausencia de Meteor ou outro hack quando nenhuma assinatura aparece.

## Validacao

Build Maven verify com API Paper 1.21.4 e Java 21. Testes cobrem vetor RFC TOTP, replay, entradas invalidas, geracao Base32, janela de nicks, JSON Discord, escrita atomica, tentativas 2FA entre instancias, reset recusado a jogador, reservas de identidade, ambiguidade e exclusao persistida.

Nao foi executado servidor Paper real, teste de carga, Discord real ou integracao real com nLogin/LuckPerms/proxy. Compilacao e testes unitarios nao equivalem a garantia de seguranca em producao. JAR local de build nao e publicado neste commit.

Referencias de API: [Paper Player](https://jd.papermc.io/paper/1.21.4/org/bukkit/entity/Player.html), [LuckPerms API](https://luckperms.net/wiki/Developer-API-Usage), [NodeAddEvent](https://github.com/LuckPerms/LuckPerms/blob/master/api/src/main/java/net/luckperms/api/event/node/NodeAddEvent.java).
