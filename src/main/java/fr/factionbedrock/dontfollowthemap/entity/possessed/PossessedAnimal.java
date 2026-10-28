package fr.factionbedrock.dontfollowthemap.entity.possessed;

/** An animal Herobrine took over: it forwards its AI, hurt, interaction and save hooks to its {@link Possession}. */
public interface PossessedAnimal
{
    Possession getPossession();
}
