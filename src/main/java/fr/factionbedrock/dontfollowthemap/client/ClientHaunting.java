package fr.factionbedrock.dontfollowthemap.client;

/** Whether this client's player is haunted, as the server last said (HauntedPayload). */
public class ClientHaunting
{
    private static boolean haunted;

    public static boolean isHaunted() {return haunted;}

    public static void set(boolean value) {haunted = value;}
}
