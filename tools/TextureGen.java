import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Random;

/**
 * Draws every texture of the mod (they are pixel art, so they are written as character grids).
 * Run from the project root: java tools/TextureGen.java
 */
public class TextureGen
{
    private static final String ASSETS = "src/main/resources/assets/dontfollowthemap/";

    //seal palette
    private static final int STONE = 0x1D1D24, RING = 0xC9CCDA, GLOW = 0xFFFFFF, GROOVE = 0x6E7080, HOLE = 0x0B0B0E, EDGE = 0x34343F, MORTAR = 0x111116;

    public static void main(String[] args) throws IOException
    {
        writeEyes();
        writeGrid("textures/block/seal_top_sealed.png", STONE, Map.of('r', RING, 'g', GROOVE, 'k', HOLE, 'w', GLOW),
                ".......rr.......",
                ".......rr.......",
                ".....grrrrg.....",
                "....rg....gr....",
                "...r........r...",
                "..g...gggg...g..",
                "..r..g.kk.g..r..",
                "rrr.g.kwwk.g.rrr",
                "rrr.g.kwwk.g.rrr",
                "..r..g.kk.g..r..",
                "..g...gggg...g..",
                "...r........r...",
                "....rg....gr....",
                ".....grrrrg.....",
                ".......rr.......",
                ".......rr.......");
        writeGrid("textures/block/seal_top_open.png", STONE, Map.of('r', RING, 'g', GROOVE, 'k', HOLE, 'w', GLOW),
                ".......rr.......",
                ".......r........",
                ".....gr..rg.....",
                "....r.....kr....",
                "...r.....k..r...",
                "..g..k.ww..k.g..",
                "..r...wkkw....r.",
                "rr...wkkkkw..rrr",
                "rrr..wkkkkw...rr",
                ".r....wkkw...r..",
                "..g.k..ww.k..g..",
                "...r.k.......r..",
                "....r.....kr....",
                ".....grr..g.....",
                "........r.......",
                ".......rr.......");
        writeGrid("textures/block/seal_top_shattered.png", STONE, Map.of('g', GROOVE, 'k', HOLE),
                ".......g........",
                "........g.......",
                ".....g....g.....",
                "....g.......k...",
                "...g...k........",
                "..........k..g..",
                "..g...kkkk......",
                "gg...kkkkkk...gg",
                "g....kkkkkk..ggg",
                "......kkkk...g..",
                "..g.k........g..",
                "...........k....",
                "....g.k....g....",
                ".....g...gg.....",
                "........g.......",
                ".......g........");
        writeGrid("textures/block/seal_side.png", STONE, Map.of('d', EDGE, 'c', MORTAR, 'g', GROOVE, 'r', RING),
                "dddddddddddddddd",
                "d..............c",
                "d..............c",
                "d..............c",
                "d..............c",
                "cccccccccccccccc",
                "..g...g...g...g.",
                ".grg.grg.grg.grg",
                ".grg.grg.grg.grg",
                "..g...g...g...g.",
                "cccccccccccccccc",
                "d..............c",
                "d..............c",
                "d..............c",
                "d..............c",
                "cccccccccccccccc");
        writeGrid("textures/item/seal_fragment.png", -1, Map.of('k', HOLE, 's', 0x2C2C36, 'r', RING, 'w', GLOW),
                "                ",
                "                ",
                "         kk     ",
                "        kssk    ",
                "       ksrwsk   ",
                "      ksrssssk  ",
                "     kssrssssk  ",
                "    kssrsssssk  ",
                "   kssrssssssk  ",
                "   ksrsssssskk  ",
                "  kssrsssskk    ",
                "  ksrssskk      ",
                "  ksrskk        ",
                "   kkk          ",
                "                ",
                "                ");
        writeEgg("textures/item/herobrine_spawn_egg.png", 0x1F8C8C, 0xF4F4FF);
        writeIcon();
    }

    /** Four white pixels over the eyes of the default skin (head front face: x 8-15, y 8-15). */
    private static void writeEyes() throws IOException
    {
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        for (int x : new int[] {9, 10, 13, 14}) {image.setRGB(x, 12, 0xFFFFFFFF);}
        write(image, "textures/entity/herobrine_eyes.png");
    }

    /** '.' is the base color with a little noise, ' ' is transparent; base -1 means no base at all. */
    private static void writeGrid(String path, int base, Map<Character, Integer> palette, String... rows) throws IOException
    {
        Random random = new Random(path.hashCode());
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++)
        {
            for (int x = 0; x < 16; x++)
            {
                char c = rows[y].charAt(x);
                if (c == ' ') {continue;}
                int color = c == '.' ? base : palette.get(c);
                if (c == '.' || c == 's') {color = shade(color, 0.85F + random.nextFloat() * 0.3F);}
                image.setRGB(x, y, 0xFF000000 | color);
            }
        }
        write(image, path);
    }

    private static void writeEgg(String path, int base, int spots) throws IOException
    {
        String[] rows = {
                "                ",
                "      ####      ",
                "     #bbbb#     ",
                "    #bbobbb#    ",
                "    #bbbbbo#    ",
                "   #bobbbbbb#   ",
                "   #bbbbobbb#   ",
                "  #bbbbbbbbbo#  ",
                "  #bobbbbbbbb#  ",
                "  #bbbbbobbbb#  ",
                "  #bbbbbbbbob#  ",
                "   #bbobbbbb#   ",
                "   #bbbbbbbb#   ",
                "    #bbbobb#    ",
                "     ######     ",
                "                "};
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++)
        {
            for (int x = 0; x < 16; x++)
            {
                char c = rows[y].charAt(x);
                if (c == ' ') {continue;}
                int color = c == '#' ? shade(base, 0.45F) : c == 'o' ? spots : base;
                //light from the top left
                if (c != '#') {color = x + y < 11 ? mix(color, 0xFFFFFF, 0.2F) : x + y > 19 ? shade(color, 0.78F) : color;}
                image.setRGB(x, y, 0xFF000000 | color);
            }
        }
        write(image, path);
    }

    /** 32x32 pixel art scaled up 4 times: a map with a red cross, and someone peeking from behind it. */
    private static void writeIcon() throws IOException
    {
        int size = 32;
        int[][] pixels = new int[size][size];
        Random random = new Random(42);
        for (int y = 0; y < size; y++)
        {
            for (int x = 0; x < size; x++)
            {
                double distance = Math.hypot(x - 15.5, y - 15.5) / 22.0;
                pixels[y][x] = shade(0x1B1B24, (float) (1.0 - distance * 0.6));
            }
        }
        //the map, slightly torn
        for (int y = 5; y <= 27; y++)
        {
            for (int x = 3; x <= 24; x++)
            {
                boolean border = x == 3 || x == 24 || y == 5 || y == 27;
                if (border && random.nextInt(5) == 0) {continue;}
                pixels[y][x] = border ? 0x8C7A55 : shade(0xD9C9A3, 0.9F + random.nextFloat() * 0.15F);
            }
        }
        //dotted path to the cross
        int[][] path = {{6, 24}, {8, 23}, {10, 22}, {11, 20}, {12, 18}, {14, 17}, {16, 17}, {17, 15}};
        for (int[] dot : path) {pixels[dot[1]][dot[0]] = 0x7A6845;}
        for (int i = -2; i <= 2; i++)
        {
            pixels[12 + i][19 + i] = 0xB01E1E;
            pixels[12 + i][19 - i] = 0xB01E1E;
            pixels[12 + i][20 + i] = 0x8A1414;
        }
        //the watcher, behind the right edge of the map
        for (int y = 7; y < size; y++)
        {
            for (int x = 22; x < size; x++)
            {
                boolean head = y >= 7 && y <= 14 && x >= 23 && x <= 30;
                boolean body = y >= 15 && x >= 22;
                if (head || body) {pixels[y][x] = (x == 22 || y == 7 || x == 31) ? 0x050507 : 0x0C0C10;}
            }
        }
        pixels[11][24] = 0xFFFFFF; pixels[11][25] = 0xFFFFFF;
        pixels[11][28] = 0xFFFFFF; pixels[11][29] = 0xFFFFFF;

        BufferedImage image = new BufferedImage(size * 4, size * 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size * 4; y++) {for (int x = 0; x < size * 4; x++) {image.setRGB(x, y, 0xFF000000 | pixels[y / 4][x / 4]);}}
        write(image, "icon.png");
    }

    private static int shade(int color, float factor)
    {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor)), g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor)), b = Math.min(255, (int) ((color & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int color, int other, float amount)
    {
        int r = (int) (((color >> 16) & 0xFF) * (1 - amount) + ((other >> 16) & 0xFF) * amount);
        int g = (int) (((color >> 8) & 0xFF) * (1 - amount) + ((other >> 8) & 0xFF) * amount);
        int b = (int) ((color & 0xFF) * (1 - amount) + (other & 0xFF) * amount);
        return (r << 16) | (g << 8) | b;
    }

    private static void write(BufferedImage image, String path) throws IOException
    {
        File file = new File(ASSETS + path);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
    }
}
