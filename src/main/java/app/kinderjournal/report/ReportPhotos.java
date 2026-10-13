package app.kinderjournal.report;

import app.kinderjournal.util.Text;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pulls the photos out of a rendered report.
 *
 * <p>The report prints a photo as {@code <img src="data:image/png;base64,...">}
 * inside a {@code div.photos} gallery, with the caption in a following
 * {@code div.caption}. That is right for HTML and for the print preview, but Word
 * and Excel cannot hold a data URI - a document has to carry the picture as a real
 * file inside its package. This finds them and hands the bytes over.
 *
 * <p>Kept apart from the report parser because the galleries are nested divs, which
 * a flat scan of block tags cannot match: the whole gallery is lifted out first and
 * its photos are read from it.
 */
final class ReportPhotos {

    /** The opening tag of a gallery. */
    private static final Pattern GALLERY_OPEN =
            Pattern.compile("<div class=\"photos\"[^>]*>");

    /** Any div opening or closing tag, used to find where a gallery ends. */
    private static final Pattern DIV_TAG = Pattern.compile("<(/?)div\\b[^>]*>");

    /** One photo inside a gallery, with its optional caption. */
    private static final Pattern ONE_PHOTO = Pattern.compile(
            "(?s)<div class=\"photo\"[^>]*>\\s*"
                    + "<img[^>]*src=\"data:(?<mime>[^;\"]+);base64,(?<data>[^\"]+)\"[^>]*>"
                    + "(?:\\s*<div class=\"caption\"[^>]*>(?<caption>.*?)</div>)?");

    /** A photo printed on its own, outside a gallery, such as a profile picture. */
    static final Pattern LOOSE_PHOTO =
            Pattern.compile("(?s)<img[^>]*src=\"data:(?<mime>[^;\"]+);base64,(?<data>[^\"]+)\"[^>]*>");

    private ReportPhotos() {
    }

    /**
     * Every gallery in the body, each as the list of photos it holds.
     *
     * <p>The end of a gallery is found by counting div tags rather than by matching
     * a closing tag, because a gallery holds a div per photo and a div per caption:
     * a lazy match stops at the first inner close and leaves the rest of the gallery
     * behind, where its photos are then read a second time.
     */
    static List<List<ReportDocument.ImageBlock>> galleries(String body) {
        List<List<ReportDocument.ImageBlock>> found = new ArrayList<>();
        for (int[] span : gallerySpans(body)) {
            List<ReportDocument.ImageBlock> photos = new ArrayList<>();
            Matcher photo = ONE_PHOTO.matcher(body.substring(span[0], span[1]));
            while (photo.find()) {
                ReportDocument.ImageBlock image = decode(photo.group("mime"),
                        photo.group("data"), photo.group("caption"));
                if (image != null) {
                    photos.add(image);
                }
            }
            if (!photos.isEmpty()) {
                found.add(photos);
            }
        }
        return found;
    }

    /** The start and end offsets of each gallery in the body, in order. */
    private static List<int[]> gallerySpans(String body) {
        List<int[]> spans = new ArrayList<>();
        Matcher open = GALLERY_OPEN.matcher(body);
        while (open.find()) {
            int start = open.start();
            int depth = 0;
            Matcher tag = DIV_TAG.matcher(body);
            tag.region(open.start(), body.length());
            int end = body.length();
            while (tag.find()) {
                if (!tag.group(1).isEmpty()) {
                    // A closing tag.
                    depth--;
                    if (depth == 0) {
                        end = tag.end();
                        break;
                    }
                } else if (!tag.group().endsWith("/>")) {
                    depth++;
                }
            }
            spans.add(new int[]{start, end});
        }
        return spans;
    }

    /** The galleries taken out of the body, so they are not parsed as text. */
    static String withoutGalleries(String body) {
        List<int[]> spans = gallerySpans(body);
        if (spans.isEmpty()) {
            return body;
        }
        StringBuilder sb = new StringBuilder(body.length());
        int cursor = 0;
        for (int[] span : spans) {
            sb.append(body, cursor, span[0]);
            cursor = span[1];
        }
        return sb.append(body, cursor, body.length()).toString();
    }

    /** A photo with no gallery around it, or null when the data is unusable. */
    static ReportDocument.ImageBlock loose(Matcher matcher) {
        return decode(matcher.group("mime"), matcher.group("data"), null);
    }

    /** True when an offset falls inside one of a body's galleries. */
    static boolean insideGallery(List<int[]> spans, int offset) {
        for (int[] span : spans) {
            if (offset >= span[0] && offset < span[1]) {
                return true;
            }
        }
        return false;
    }

    /** The offsets of every gallery in a body. */
    static List<int[]> spansOf(String body) {
        return gallerySpans(body);
    }

    /** Decodes one data URI into an image block. */
    private static ReportDocument.ImageBlock decode(String mime, String base64, String caption) {
        if (mime == null || base64 == null) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
            if (bytes.length == 0) {
                return null;
            }
            String plainCaption = caption == null ? ""
                    : Text.unescapeHtml(caption.replaceAll("(?s)<[^>]+>", "")).trim();
            return new ReportDocument.ImageBlock(extensionFor(mime), bytes, plainCaption);
        } catch (IllegalArgumentException ex) {
            // A malformed data URI is not worth failing the whole report over.
            return null;
        }
    }

    /** The file extension for a MIME type, as used inside an Office package. */
    static String extensionFor(String mime) {
        String type = mime.toLowerCase(java.util.Locale.ROOT).trim();
        return switch (type) {
            case "image/jpeg", "image/jpg" -> "jpeg";
            case "image/gif" -> "gif";
            case "image/bmp" -> "bmp";
            case "image/webp" -> "webp";
            default -> "png";
        };
    }

    /**
     * The size of an image, read from its own header.
     *
     * <p>Needed because a document must state how big a picture is, and the report
     * only says how wide it should print. Reading the real size keeps the shape
     * right; if it cannot be read, a square is assumed so the picture still appears.
     *
     * @return width and height in pixels, or {0, 0} when they cannot be read
     */
    static int[] pixelSize(byte[] data) {
        if (data == null || data.length < 24) {
            return new int[]{0, 0};
        }
        // PNG: the size is at a fixed place in the IHDR chunk.
        if (data.length > 24 && (data[0] & 0xFF) == 0x89 && data[1] == 'P'
                && data[2] == 'N' && data[3] == 'G') {
            return new int[]{readInt(data, 16), readInt(data, 20)};
        }
        // JPEG: walk the markers until a start-of-frame is found.
        if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8) {
            int i = 2;
            while (i + 9 < data.length) {
                if ((data[i] & 0xFF) != 0xFF) {
                    i++;
                    continue;
                }
                int marker = data[i + 1] & 0xFF;
                int length = ((data[i + 2] & 0xFF) << 8) | (data[i + 3] & 0xFF);
                // Any of the start-of-frame markers carries the size.
                if (marker >= 0xC0 && marker <= 0xCF
                        && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                    int height = ((data[i + 5] & 0xFF) << 8) | (data[i + 6] & 0xFF);
                    int width = ((data[i + 7] & 0xFF) << 8) | (data[i + 8] & 0xFF);
                    return new int[]{width, height};
                }
                i += 2 + Math.max(2, length);
            }
        }
        return new int[]{0, 0};
    }

    /** A big-endian 32-bit value, as PNG stores its dimensions. */
    private static int readInt(byte[] data, int offset) {
        return ((data[offset] & 0xFF) << 24) | ((data[offset + 1] & 0xFF) << 16)
                | ((data[offset + 2] & 0xFF) << 8) | (data[offset + 3] & 0xFF);
    }
}
