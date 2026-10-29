# Don't Follow the Map

A Herobrine horror mod for **Fabric / Minecraft 26.3**. *Le français suit l'anglais.*

Something is haunting your world. At first it only watches, from afar. Then it comes closer: to your torches,
to your bed, to your house. One day it leaves you a map. You really shouldn't follow it.

## How it goes

The haunting grows with the time you spend in the Overworld (one Minecraft day = 20 minutes):

| Stage | When (default) | What happens |
|---|---|---|
| Quiet | first day | nothing |
| Watched | days 1 to 3 | Herobrine is seen in the distance, gone before you can reach him (or if you stare at him too long). Sounds behind you that only you hear: footsteps, a creeper's hiss, a zombie getting hit, someone mining, a tool breaking (one every 5 to 10 minutes, 3 to 7 once haunted, 2 to 5 once hunted; twice as long by day). Torches out of sight turn into redstone torches for a while. He may appear for an instant on what you just killed, or next to your bed when you try to sleep. |
| Haunted | days 3 to 6 | He leaves you **the map**. Signs and small constructions appear around your home. A sheep, a cow, a pig or a chicken starts staring at you (if you killed them all, one comes from where you can't see)... Someone who looks like a player (a friend of yours, if they are online) uses your crafting table and your chests. Something invisible follows you for a while and steals what you mine: it can only be caught once it has what it came for, while it runs away. While you dig a straight 2x1 tunnel, blocks may be put back behind you, loudly: dig through the top one quickly and you will see who did it; dig the bottom one first, or wait too long, and only a redstone torch will be left. You may wake up in a grave. |
| Hunted | after day 6, or once he is free | Everything happens more often, and he waits right behind you while the fog slowly thickens. |

While Herobrine watches, whoever is close to him or looks at him, and only them, hears his ambience and sees their screen slowly turn into an old,
worn film that glitches now and then, fainter from afar or in the corner of the eye (scaled by the distortion effects accessibility setting);
he appears suddenly with his own sound, and the screen jolts all at once.
He also plays with the debug screen: while something of his is around, F3 shows random coordinates, and a haunted player who shows the hitboxes
(F3 + B) sees some that belong to nothing (the thief, being invisible, never has one).

The map leads to a **seal shrine**. It isn't part of the world: it rises 250 to 500 blocks away when the first map is given
(nobody stumbles upon it by chance), away from the players and their homes, and every map leads to it. Showing the map to the seal frees Herobrine:
a three-phase fight with its own music, which starts as soon as the seal breaks (blinks, marked lightning strikes, shades, darkness).
Defeating him brings peace to every player on the server for 10 days, even those who weren't there or come back later;
those who fought him get their stolen items back. If you run away, he goes back into the open seal; right-click it to call him again.
Once he is defeated, the shrine falls into ruins as soon as nobody is looking, and the maps leading to it burn when they leave a chest
or an item frame; the next map leads to a new shrine.

The mod is needed on the server and on the clients (entities, sounds, screen effects).

## Configuration

`config/dontfollowthemap.json` (created on first launch): every event can be turned off (each possessable animal
too), chances and durations can be changed, and `eventFrequency` speeds the haunting up or slows it down.

## Commands (operators)

- `/dontfollowthemap status [player]`
- `/dontfollowthemap stage <player> <0-3>`
- `/dontfollowthemap trigger <event> [player]`: `sighting`, `stalk`, `torches`, `sign`, `build`, `imitator`, `map`, `grave`,
  `thief` (from the next block the player breaks in survival, the thief follows them and steals what falls),
  `tunnel` (the player must stand in a straight 2x1 tunnel, facing where they dig: it is walled up behind them)
- `/dontfollowthemap trigger whisper [sound] [player]`: `footsteps`, `creeper`, `zombie`, `mining` or `broken_tool`, any of them when no sound is given
- `/dontfollowthemap trigger possession [entity] [player]`: possesses an animal of that type (`minecraft:sheep`, `cow`, `pig`
  or `chicken`) near the player, or any of them when no type is given (for another player: `/execute as <player> run dontfollowthemap trigger possession`)
- `/dontfollowthemap reset <player>`
- `/dontfollowthemap locate-seal`: where the seal shrine stands (or will rise); an error while no place is picked
- `/dontfollowthemap generate-seal [position]`: a new shrine replaces the current one, somewhere around (built once its place is loaded),
  or right at the given position (its center), now

## Building and testing

Requires JDK 25.

```
./gradlew build               # jar in build/libs
./gradlew runClient           # play
./gradlew runGameTest         # server game tests (every event, every possessed animal, the shrine, the fight)
./gradlew runClientGameTest   # renders everything, screenshots in build/run/clientGameTest/screenshots
```

Textures are drawn by `tools/TextureGen.java` (`java tools/TextureGen.java`). Herobrine wears the game's own
default skin with glowing eyes on top. His ambience, his appearance sound and the fight music are the mod's own
(`assets/dontfollowthemap/sounds`, edited in `tools/sounds/HerobrineSounds.kdenlive`); the other sounds are vanilla
sounds reshaped in `sounds.json`.

## License

All rights reserved, see [LICENSE](LICENSE).

---

# Don't Follow the Map (FR)

Un mod d'horreur sur Herobrine pour **Fabric / Minecraft 26.3**. Quelque chose hante ton monde : d'abord de loin,
puis de plus en plus près (tes torches, ton lit, ta maison). Un jour, il te laisse une carte. Tu ne devrais vraiment
pas la suivre : elle mène à un sceau qui le retient, et le briser le libère pour un vrai combat.

La progression, la configuration et les commandes sont décrites ci-dessus. Compilation : `./gradlew build` (JDK 25),
tests en jeu : `./gradlew runGameTest` et `./gradlew runClientGameTest`.
