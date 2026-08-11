# TorneoCosmic

Plugin de Minecraft **26.2** (Paper) para gestionar 1v1 de torneo, hecho sobre
tu plantilla base (Gradle + Paper API 26.2, Java 25).

## Compilar

```bash
./gradlew build
```

El JAR final queda en `build/libs/`. Necesitas conexión a internet la primera
vez (descarga Gradle y la API de Paper).

## Comandos (solo operadores / ops)

- `/tournament set pos1` — Guarda la posición 1 del 1v1 en el lugar donde estás parado.
- `/tournament set pos2` — Guarda la posición 2 del 1v1.
- `/tournament pvp <jugador1> <jugador2> [fase]` — Inicia un 1v1 entre dos jugadores conectados.
  - `fase` es opcional. Acepta: `final`, `semis`, `cuartos`, `octavos`, `dieciseisavos`,
    o cualquier texto libre (se mostrará tal cual, en mayúsculas).
  - Ejemplos: `/tournament pvp Critycal Pepito semis` o simplemente `/tournament pvp Critycal Pepito`.
- `/tournament pvp stop` — Detiene el combate en curso (si lo hay) y devuelve a ambos peleadores a su estado anterior.

Solo puede haber **un** 1v1 corriendo a la vez.

## Cómo funciona un 1v1

1. Se valida que ambas posiciones (`pos1`/`pos2`) estén configuradas y que ambos jugadores estén conectados.
2. Se guarda el **estado completo** de cada peleador: inventario, armadura, offhand, vida, hambre, experiencia (nivel y progreso), modo de juego, efectos de poción y ubicación exacta.
3. Se teletransportan a `pos1` y `pos2`, mirándose de frente.
4. Se les entrega el **kit configurado en `config.yml`** (ver abajo), con vida y hambre al máximo.
5. Se corre una secuencia de anuncios/animación con `/title` (visible para todo el servidor):
   - Nombre de la fase (si se especificó) + "Jugador1 VS Jugador2"
   - "¡Prepárense en sus posiciones!"
   - "Por un lado tenemos a Jugador1!!!!"
   - "Y por el otro tenemos a Jugador2!!!!"
   - Cuenta regresiva 3, 2, 1
   - "¡PELEA!"
   - Durante toda esta introducción los peleadores están congelados (no pueden moverse ni recibir daño).
6. Empieza el combate real. Aparece un contador regresivo en la hotbar (action bar) de ambos peleadores, con la duración configurada en `config.yml` (por defecto 5 minutos).
7. El combate termina por:
   - **Muerte**: nunca ocurre una muerte real (se cancela el golpe letal); se declara ganador automáticamente al rival.
   - **Tiempo**: gana quien haya hecho más daño. Si el daño es igual, gana quien terminó con más vida. Si también empatan en vida, se declara **empate** (no hay ganador automático — repite la ronda ejecutando `/tournament pvp` de nuevo, tal como pediste).
8. Al terminar (por cualquier motivo), **ambos peleadores vuelven exactamente a su estado previo**: inventario, armadura, vida, hambre, experiencia, posición, etc., como si nada hubiera pasado.

### Protecciones incluidas
- Nadie puede interferir en el combate: solo cuenta el daño hecho por el rival o por el entorno (caídas, fuego, etc.).
- Si un peleador se desconecta a mitad de combate, se declara ganador al rival automáticamente, y al peleador que se desconectó se le restaura su estado apenas vuelva a conectarse.
- Si el servidor se apaga con un combate en curso, se detiene y se restauran los jugadores conectados antes de cerrar.

## Configurar el kit (`config.yml`)

En `src/main/resources/config.yml` puedes definir el casco/armadura y cada slot
del inventario (0-8 hotbar, 9-35 el resto) que se entrega al iniciar un 1v1:

```yaml
kit:
  armadura:
    helmet:
      material: DIAMOND_HELMET
    chestplate:
      material: DIAMOND_CHESTPLATE
    leggings:
      material: DIAMOND_LEGGINGS
    boots:
      material: DIAMOND_BOOTS
  slots:
    "0":
      material: DIAMOND_SWORD
      amount: 1
      name: "&b&lEspada del Torneo"
      enchantments:
        sharpness: 2
    "1":
      material: BOW
    "2":
      material: ARROW
      amount: 32
    "8":
      material: GOLDEN_APPLE
      amount: 3
```

- `material`: nombre del `Material` de Bukkit (ej: `DIAMOND_SWORD`, `BOW`, `ARROW`).
- `amount`: cantidad (opcional, por defecto 1).
- `name` / `lore`: opcional, admite colores con `&`.
- `enchantments`: opcional, usa el **nombre moderno en minúscula** del encantamiento (ej: `sharpness`, `protection`, `power`, `unbreaking`).

También puedes ajustar la duración del combate:

```yaml
duelo:
  duracion-segundos: 300
```

Después de editar el `config.yml` del servidor (`plugins/TorneoCosmic/config.yml`,
creado tras la primera ejecución), reinicia el plugin o el servidor para que tome los cambios.

## Notas
- Los comandos son solo para **ops**; cualquier otro jugador recibe un mensaje de "sin permiso".
- Las posiciones se guardan en el `config.yml` del servidor, así que sobreviven a reinicios.
