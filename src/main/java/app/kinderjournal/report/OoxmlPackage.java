package app.kinderjournal.report;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writing a Word or Excel file by hand.
 *
 * <p>Both formats are ZIP archives of XML parts, so the JDK has everything needed
 * to produce them and the program does not have to carry a document library: the
 * libraries are around 17MB, which would be most of the download for a feature
 * used only when a teacher wants to hand a report to somebody else.
 *
 * <p>Deliberately minimal. Only the parts a reader actually requires are written,
 * and only the XML those parts need - no theme, no font table, no settings.
 */
final class OoxmlPackage implements AutoCloseable {

    private final ZipOutputStream zip;

    OoxmlPackage(Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.zip = new ZipOutputStream(Files.newOutputStream(file),
                StandardCharsets.UTF_8);
        // A reader may inspect any entry, so the whole archive is deflated; the
        // first entry is stored because that is what the specification asks for.
        this.zip.setLevel(6);
    }

    OoxmlPackage(OutputStream out) {
        this.zip = new ZipOutputStream(out, StandardCharsets.UTF_8);
        this.zip.setLevel(6);
    }

    /** Writes one part of the package. */
    void part(String name, String xml) throws IOException {
        part(name, xml, false);
    }

    /**
     * Writes one part of the package.
     *
     * @param stored true to store the entry uncompressed, which the format requires
     *               of the content-types part
     */
    void part(String name, String xml, boolean stored) throws IOException {
        byte[] bytes = xml.getBytes(StandardCharsets.UTF_8);
        ZipEntry entry = new ZipEntry(name);
        // The date is fixed so that saving the same report twice gives the same
        // bytes, which makes the tests able to compare them.
        entry.setTime(1_700_000_000_000L);
        if (stored) {
            entry.setMethod(ZipEntry.STORED);
            entry.setSize(bytes.length);
            entry.setCompressedSize(bytes.length);
            CRC32 crc = new CRC32();
            crc.update(bytes);
            entry.setCrc(crc.getValue());
        }
        zip.putNextEntry(entry);
        zip.write(bytes);
        zip.closeEntry();
    }

    @Override
    public void close() throws IOException {
        zip.close();
    }

    /**
     * Escapes text for XML content.
     *
     * <p>Control characters are dropped rather than escaped: they are not legal in
     * XML at all, and a stray one pasted into a note would otherwise make the whole
     * document unreadable.
     */
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> {
                    if (c == '\t' || c == '\n' || c == '\r' || c >= 0x20) {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    /** True when a string holds a character XML cannot carry. */
    static boolean hasIllegalCharacters(String value) {
        if (value == null) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\t' && c != '\n' && c != '\r' && c < 0x20) {
                return true;
            }
        }
        return false;
    }
}
