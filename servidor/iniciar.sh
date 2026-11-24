#!/bin/bash
# Sobe os dois processos do servidor no mesmo container:
#   1. servidor de sockets (easyagenda.sockets.Servidor): agenda, cancela e remarca aulas
#   2. API HTTP (easyagenda.Main): atende o site e repassa esses pedidos ao servidor de sockets
# Se um dos dois parar, o outro é encerrado e o container termina (o Docker/Render reinicia).

CLASSPATH="app.jar:lib/*"

# Sem teclado aqui: o "desativar" não existe, mas o docker stop dispara o mesmo aviso de desligamento
java $MEMORIA_SOCKETS -cp "$CLASSPATH" easyagenda.sockets.Servidor < /dev/null &
SOCKETS=$!

java $MEMORIA_API -cp "$CLASSPATH" easyagenda.Main &
API=$!

trap 'kill -TERM $API $SOCKETS 2>/dev/null' TERM INT

wait -n
STATUS=$?
kill -TERM $API $SOCKETS 2>/dev/null
wait
exit $STATUS
