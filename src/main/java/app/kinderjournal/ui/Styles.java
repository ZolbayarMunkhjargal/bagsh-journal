package app.kinderjournal.ui;

import java.net.URL;

/** Locates the application stylesheet. */
public final class Styles {

    private static final String PATH = "/app/kinderjournal/ui/app.css";
    private static URL cached;

    private Styles() {
    }

    /** The stylesheet URL, or an empty array when it is missing (never fatal). */
    public static String[] stylesheet() {
        URL url = url();
        return url == null ? new String[0] : new String[]{url.toExternalForm()};
    }

    public static URL url() {
        if (cached == null) {
            cached = Styles.class.getResource(PATH);
        }
        return cached;
    }

    /** Inline CSS for the printed report, so reports keep their own look. */
    public static String reportCss() {
        return """
                * { box-sizing: border-box; }
                body {
                    font-family: "Segoe UI", Arial, sans-serif;
                    font-size: 10.5pt;
                    color: #1f2933;
                    margin: 0;
                    padding: 0;
                }
                h1 { font-size: 19pt; margin: 0 0 2mm 0; color: #0f3d3e; }
                h2 { font-size: 13pt; margin: 6mm 0 2mm 0; color: #0f3d3e;
                     border-bottom: 0.4mm solid #cfd8dc; padding-bottom: 1mm; }
                h3 { font-size: 11pt; margin: 4mm 0 1mm 0; color: #37474f; }
                p { margin: 0 0 2mm 0; line-height: 1.45; }
                .report-header { border-bottom: 0.8mm solid #0f3d3e; padding-bottom: 3mm; margin-bottom: 5mm; }
                .report-header .subtitle { color: #52606d; font-size: 10pt; }
                .meta-line { color: #52606d; font-size: 9pt; }
                table { width: 100%; border-collapse: collapse; margin: 0 0 3mm 0; }
                th, td { border: 0.3mm solid #b0bec5; padding: 1.6mm 2mm; text-align: left;
                         vertical-align: top; font-size: 10pt; }
                th { background: #eceff1; font-weight: 600; }
                tr { page-break-inside: avoid; }
                .entry { page-break-inside: avoid; border-left: 1mm solid #90a4ae;
                         padding: 0 0 0 3mm; margin: 0 0 4mm 0; }
                .entry .entry-date { font-weight: 600; color: #0f3d3e; }
                .entry .entry-kind { color: #52606d; font-size: 9pt; text-transform: uppercase;
                                     letter-spacing: 0.4pt; }
                .summary-grid { display: flex; flex-wrap: wrap; gap: 3mm; margin-bottom: 4mm; }
                .summary-box { border: 0.3mm solid #b0bec5; border-radius: 1.5mm;
                               padding: 2mm 3mm; min-width: 28mm; }
                .summary-box .value { font-size: 15pt; font-weight: 600; color: #0f3d3e; }
                .summary-box .label { font-size: 8.5pt; color: #52606d; }
                .photos { display: flex; flex-wrap: wrap; gap: 3mm; }
                .photo { width: 48mm; page-break-inside: avoid; }
                .photo img { width: 48mm; border: 0.3mm solid #b0bec5; }
                .photo .caption { font-size: 8.5pt; color: #52606d; }
                .photo-grid { display: flex; flex-wrap: wrap; gap: 2mm; }
                .photo-grid .photo-small { width: 34mm; }
                .photo-grid .photo-small img { width: 34mm; border: 0.3mm solid #b0bec5; }
                .status-present { color: #1b5e20; font-weight: 600; }
                .status-absent { color: #b71c1c; font-weight: 600; }
                .status-late { color: #e65100; font-weight: 600; }
                .status-excused { color: #4527a0; font-weight: 600; }
                .signature { margin-top: 10mm; border-top: 0.3mm solid #90a4ae; padding-top: 2mm;
                             color: #52606d; font-size: 9pt; }
                .footer-note { margin-top: 6mm; padding-top: 2mm; border-top: 0.3mm solid #cfd8dc;
                               color: #78909c; font-size: 8.5pt; }
                .page-break { page-break-before: always; }
                .note-block { white-space: pre-wrap; }
                .confidential { color: #b71c1c; font-weight: 600; font-size: 9pt; }
                """;
    }
}
