import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Draws the TV shelf banner (320x180) and launcher icon (192x192) into a res folder, run by build.sh.
 *   java tools/Art.java <res dir> <app name>                          name as text
 *   java tools/Art.java <res dir> <app name> <picture> [x y size]     square crop of a picture (default: centre)
 */
class Art {
    static final Color BG = new Color(28, 28, 36), TEXT = Color.WHITE;

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0], "drawable-nodpi");
        dir.mkdirs();
        String name = a[1];
        BufferedImage face = null;
        if (a.length > 2) {
            BufferedImage pic = ImageIO.read(new File(a[2]));
            if (pic == null) throw new IllegalArgumentException(a[2] + ": not a PNG, JPEG, GIF or BMP picture");
            int size = Math.min(pic.getWidth(), pic.getHeight());
            int x = (pic.getWidth() - size) / 2, y = (pic.getHeight() - size) / 2;
            if (a.length > 5) {
                x = Integer.parseInt(a[3]);
                y = Integer.parseInt(a[4]);
                size = Integer.parseInt(a[5]);
            }
            face = pic.getSubimage(x, y, size, size);
        }

        BufferedImage icon = canvas(192, 192);
        Graphics2D g = icon.createGraphics();
        smooth(g);
        if (face != null) g.drawImage(face, 0, 0, 192, 192, null);
        else centered(g, initials(name), 0, 0, 192, 192, 96);
        ImageIO.write(icon, "png", new File(dir, "icon.png"));

        BufferedImage banner = canvas(320, 180);
        g = banner.createGraphics();
        smooth(g);
        if (face != null) {
            g.drawImage(face, 0, 0, 180, 180, null);
            centered(g, name, 180, 0, 140, 180, 30);
        } else {
            centered(g, name, 0, 0, 320, 180, 56);
        }
        ImageIO.write(banner, "png", new File(dir, "banner.png"));
    }

    /** "Web TV" -> "WT", "Wikipedia" -> "W". */
    static String initials(String name) {
        StringBuilder s = new StringBuilder();
        for (String word : name.trim().split("\\s+")) {
            if (!word.isEmpty() && s.length() < 2) s.appendCodePoint(Character.toUpperCase(word.codePointAt(0)));
        }
        return s.toString();
    }

    /** Text centred in the box, one line, shrunk from maxSize until it fits with a small margin. */
    static void centered(Graphics2D g, String text, int x, int y, int w, int h, int maxSize) {
        g.setColor(TEXT);
        Font font = new Font("SansSerif", Font.BOLD, maxSize);
        FontMetrics fm = g.getFontMetrics(font);
        while (fm.stringWidth(text) > w - 16 && font.getSize() > 10) {
            font = font.deriveFont((float) font.getSize() - 1);
            fm = g.getFontMetrics(font);
        }
        g.setFont(font);
        g.drawString(text, x + (w - fm.stringWidth(text)) / 2, y + (h - fm.getHeight()) / 2 + fm.getAscent());
    }

    static BufferedImage canvas(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(BG);
        g.fillRect(0, 0, w, h);
        return img;
    }

    static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }
}
