# Don't Follow the Map

A Herobrine horror mod for **Fabric / Minecraft 26.3**. *Le français suit l'anglais.*

Something is haunting your world. At first it only watches, from afar. Then it comes closer: to your torches,
to your bed, to your house. One day it leaves you a map. You really shouldn't follow it.

## How it goes

The haunting grows with the time you spend in the Overworld (one Minecraft day = 20 minutes):

| Stage | When (default) | What happens |
|---|---|---|
| Quiet | first day | nothing |
| Watched | days 1 to 3 | Herobrine is seen in the distance: a low rumble when you get closer, gone before you can reach him (or if you stare at him too long). Whispers and footsteps behind you. Torches out of sight turn into redstone torches for a while. He may appear for an instant on what you just killed, or next to your bed when you try to sleep. |
| Haunted | days 3 to 6 | He leaves you **the map**. Signs and small constructions appear around your home. A sheep, a cow, a pig or a chicken starts staring at you... Someone who looks like a player (a friend of yours, if they are online) uses your crafting table and your chests. Something invisible follows you for a while and steals what you mine: it can only be caught once it has what it came for, while it runs away. You may wake up in a grave. |
| Hunted | after day 6, or once he is free | Everything happens more often, and he waits right behind you while the fog slowly thickens. |

The map leads to a **seal shrine**. Showing the map to the seal frees Herobrine: a three-phase fight
(blinks, marked lightning strikes, shades, darkness). Defeat him to get your stolen items back and some peace.
If you run away, he goes back into the open seal; right-click it to call him again.

Everything is server side: the mod is needed on the server, and on clients to see the entities.

## Configuration

`config/dontfollowthemap.json` (created on first launch): every event can be turned off (each possessable animal
too), chances and durations can be changed, and `eventFrequency` speeds the haunting up or slows it down.

## Commands (operators)

- `/dontfollowthemap status [player]`
- `/dontfollowthemap stage <player> <0-3>`
- `/dontfollowthemap trigger <event> [player]`: `sighting`, `stalk`, `whisper`, `torches`, `sign`, `build`, `imitator`, `map`, `grave`,
  `thief` (from the next block the player breaks in survival, the thief follows them and steals what falls)
- `/dontfollowthemap trigger possession [entity] [player]`: possesses an animal of that type (`minecraft:sheep`, `cow`, `pig`
  or `chicken`) near the player, or any of them when no type is given (for another player: `/execute as <player> run dontfollowthemap trigger possession`)
- `/dontfollowthemap reset <player>`
- `/locate structure dontfollowthemap:seal`

## Building and testing

Requires JDK 25.

```
./gradlew build               # jar in build/libs
./gradlew runClient           # play
./gradlew runGameTest         # server game tests (every event, every possessed animal, the shrine, the fight)
./gradlew runClientGameTest   # renders everything, screenshots in build/run/clientGameTest/screenshots
```

Textures are drawn by `tools/TextureGen.java` (`java tools/TextureGen.java`). Herobrine wears the game's own
default skin with glowing eyes on top; every sound is a vanilla sound reshaped in `sounds.json`.

## License

All rights reserved, see [LICENSE](LICENSE).

---

# Don't Follow the Map (FR)

Un mod d'horreur sur Herobrine pour **Fabric / Minecraft 26.3**. Quelque chose hante ton monde : d'abord de loin,
puis de plus en plus près (tes torches, ton lit, ta maison). Un jour, il te laisse une carte. Tu ne devrais vraiment
pas la suivre : elle mène à un sceau qui le retient, et le briser le libère pour un vrai combat.

La progression, la configuration et les commandes sont décrites ci-dessus. Compilation : `./gradlew build` (JDK 25),
tests en jeu : `./gradlew runGameTest` et `./gradlew runClientGameTest`.
