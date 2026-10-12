# KinderJournal

A desktop journal for kindergarten and preschool teachers. It replaces the
handwritten journal and the loose sheets of paper with one organised program:
attendance, notes on each child, interviews with parents, activity planning, a
child's complete history, and print-ready documents.

Everything is stored **on the one computer it runs on**. There is no account, no
cloud storage, no synchronisation, and no internet connection is used at any
point. Photographs are copied into the program's own folder on the same disk.

---

## Contents

- [What it does](#what-it-does)
- [Getting started](#getting-started)
  - [For teachers: install and run](#for-teachers-install-and-run)
  - [For developers: build from source](#for-developers-build-from-source)
- [The screens](#the-screens)
- [Printing and reports](#printing-and-reports)
- [Where your data lives](#where-your-data-lives)
- [Backup and restore](#backup-and-restore)
- [Privacy](#privacy)
- [Project layout](#project-layout)
- [Development notes](#development-notes)
- [Command-line options](#command-line-options)

---

## What it does

| Area | What the program gives you |
| --- | --- |
| **Classes** | Several kindergarten groups in one program. Create, describe, archive and delete classes, and move a child between them without losing any history. |
| **Children** | A profile per child: name, date of birth and age, class, guardians and contacts, allergies, notes, and a photo. |
| **Attendance** | A daily register with Present / Absent / Late / Excused, a reason or note per child, arrival and departure times, a month calendar and per-child totals for any period. |
| **Observations** | Dated notes about one child, filed under a developmental area (physical, language, social, emotional, cognitive, participation, behaviour, achievements, areas needing support) or left uncategorised, with photos attached. |
| **Interviews** | Parent, guardian, child and professional conversations: participants, topics, what was shared, your conclusions, and follow-up actions with a due date that the dashboard reminds you about. |
| **Lesson & activity plans** | Day, week and month views. Objectives, materials, the planned sequence, notes for the day, a status, and a reflection written afterwards. Any plan can be saved as a template or copied to another date. |
| **Child history** | Everything recorded about one child merged into a single timeline, newest first, with filters for period and record type. |
| **Advanced search** | One search across observations, interview notes, activity descriptions and child profiles, with combined filters for class, child, date range, category and record type. |
| **Print & reports** | Eleven document types, previewed in the program and printed through the Windows print dialog, which also saves to PDF. Any report can also be saved as an editable Word or Excel file. |
| **English and Mongolian** | The whole interface and every printed report can be switched between English and Mongolian with one button, and the choice is remembered. |
| **Data & backup** | One-file backups of the database and every photo, restore with a safety copy taken first, a health check, and a list of any photos whose file has gone missing. |

The program deliberately **does not** draw conclusions about a child. It organises
what the teacher wrote and lays it out for printing; every word in a report is
either the teacher's own text or a date, a count or a category label.

---

## Getting started

### For teachers: install and run

You need the folder that was built for you (ask whoever prepared it, or see the
developer instructions below to make one).

1. Copy the `KinderJournal` folder onto the computer, for example into
   `Documents`.
2. Double-click **`KinderJournal.lnk`** - the one with the journal icon.
3. Read `READ ME FIRST.txt` in the same folder - it repeats the important points
   about where your data is kept.

No Java installation is required: the folder carries its own runtime.

**The program is a window, not a console.** `KinderJournal.exe` is built as a
Windows GUI program, so no black command window appears while it runs and none
flashes when it starts. It reports problems in a message box and in
`logs\kinderjournal.log`; there is nothing for it to print to a console.

Three files in the folder mean you never have to go looking for the executable:

| File | What it is for |
| --- | --- |
| `KinderJournal.lnk` | The one to double-click. |
| `Put a shortcut on my Desktop.vbs` | Run once after copying the folder to another computer. A shortcut stores an absolute path, so the packaged one still points at the original location; this puts a correct shortcut on the Desktop for the copy you are using. |
| `Start KinderJournal.cmd` | For anyone who prefers a script, or needs to pass options such as an alternative records folder. |

If it is your first time, the Dashboard offers to create a class. A class holds
the children; everything else hangs off the child.

### For developers: build from source

Requirements:

- **JDK 21 or newer** (a full JDK, not a JRE - `javac` must be present).
- **Node.js 18 or newer** - used *once* to download the two dependencies.
  Nothing is installed with `npm`.

Then, from the project folder:

```bat
build.bat          :: download dependencies (first time) and compile
run.bat            :: build if needed, then start the program
```

The dependencies - the JavaFX runtime for Windows and the SQLite JDBC driver,
four files in total - are fetched from Maven Central into `.tools\lib` on the
first build and verified by checksum. After that the project builds and runs with
no network access at all. The exact versions are pinned in `tools\bootstrap.mjs`
and recorded, with their SHA-256 hashes, in `.tools\deps.properties`.

Useful commands:

```bat
build.bat                  :: compile into build\classes
build.bat -Clean           :: compile from scratch
build.bat -Force           :: re-download the dependencies
run.bat                    :: start the program
tools\test.ps1             :: run the headless data-layer tests (145 checks)
run.bat --ui-test          :: build every screen on a real toolkit (74 checks)
package.bat                :: produce the self-contained folder for a teacher
package.bat -Installer     :: ...and a Windows installer
```

### Making a version to hand over

```bat
package.bat -Zip
```

This compiles the sources, bundles them with a trimmed Java runtime (including
JavaFX and the SQLite native library), and writes the result to
`release\KinderJournal-1.0.0-windows`. That folder is self-contained: a teacher
double-clicks `KinderJournal.lnk` and needs nothing else installed. `-Zip` also
writes a `.zip` of the folder.

The program icon comes from `assets\icon.ico`, which `tools\make_icon.py`
generates; re-run that script after editing it. It is used for the executable,
the shortcut and (if an installer is ever built) the installer.

```bat
package.bat -NoInstaller -Zip              :: the folder and a zip
package.bat -DataDir D:\KinderJournal      :: shortcut keeps records in D:
package.bat -Installer                      :: a Windows installer (needs WiX)
```

#### About the installer

An `.msi` gives Start Menu and Desktop entries plus an entry in *Add or remove
programs*, and needs the **WiX Toolset** installed - jpackage refuses to build
`msi` or `exe` installers without it. If `package.bat -Installer` reports that
jpackage failed, install WiX and try again:

```powershell
Start-Process -FilePath .\.tools\wix314.exe -ArgumentList "/install","/quiet","/norestart" -Verb RunAs -Wait
```

For a single computer the folder and its shortcut are enough, which is why the
shortcut is created either way.

**Two things about a double-clicked Windows program are easy to get wrong**, and
`tools\package.ps1` handles both:

- The runtime must include `java.sql` and `java.desktop` alongside the JavaFX
  modules, or the program starts and then fails the first time it saves.
- The SQLite driver normally unpacks its own native library into the temporary
  folder on first use. A program launched from a shortcut is not always given a
  writable temporary folder, so the packaging step extracts that library next to
  the executable, where the driver finds it immediately.

A third one is worth knowing because it is invisible until someone looks:
`--win-console` makes the executable a console program, which shows a black
window and routes the program's output there instead of to a message box. The
packaging script does not pass it.

---

## The screens

**Dashboard** - the selected class at a glance: how many children, who is present
today, how many observations were written today, what is planned this week, and
which follow-ups are due. It also shows what still needs doing (today's register,
a first observation) and offers one-click shortcuts.

**Children** - the roster on the left, the selected child's profile on the right:
contacts, allergies, notes, photos, recent records, and buttons to add an
observation, record an interview, open the full history or print a report.
Children can be moved between classes; their past records stay with the class they
were recorded under, so old registers and reports remain accurate.

**Classes** - create, describe, archive and delete classes, with a per-class
activity summary. A class cannot be deleted while it still has children, so no
child's history can be lost by tidying up.

**Attendance** - a daily register, a month calendar showing what has been
recorded, and a per-child summary for any period. Marking a child again replaces
the earlier mark, so correcting a mistake is a single click. Changing the date
saves automatically.

**Observations** - filter by child, category and date; read the full text and its
photos; add, edit or delete. Category chips across the top show how many notes
exist in each developmental area, and clicking one filters to it.

**Interviews** - the same list-and-detail layout, with a **Follow-ups due** view
that shows only the outstanding actions whose date has arrived.

**Child history** - pick a child and a period and see attendance, observations,
interviews and the class's activities woven into one timeline grouped by month.
Each entry opens in full on the right, photos included.

**Lesson plans** - day, week and month views. A plan covering several days appears
on every day it runs. After the activity, mark it completed and write a short
reflection; templates keep the weekly routine from being retyped.

**Search** - one box for keywords plus filters for class, child, date range,
category, interview type and plan status. Results show the date, the type, the
child and a snippet with the match highlighted.

**Print & reports** - choose a document, who it covers, the period and what to
include, see how many records it will contain, then preview and print. Reports
can also be saved as Word or Excel files; see below.

### Saving a report as a Word or Excel file

Besides printing, a report can be saved as an editable file:

| Button | What you get |
| --- | --- |
| **Save as Word** | A `.docx` with real Word headings and tables, so it can be edited, signed off, or put in a child's folder. |
| **Save as Excel** | A `.xlsx` with each table on its own sheet, numbers stored as numbers, so columns can be sorted and totalled. |

Which buttons appear depends on the report, because a register and an observation
report are different kinds of document:

| Report | Word | Excel |
| --- | :---: | :---: |
| Daily attendance sheet, Attendance summary, Monthly attendance grid | yes | yes |
| Class report for a period, Search results | yes | yes |
| Child profile sheet | yes | no |
| Observation report, Interview summary | yes | no |
| Child history report, Daily plan, Weekly plan | yes | no |

Excel is offered where the report is a table of one row per child; a spreadsheet
of prose is no use to anyone, and the child history is a dated list of entries
rather than a table. If a report has no table at all, the Excel button is not
shown rather than producing an empty file.

#### Why there is no document library

`.docx` and `.xlsx` are ZIP archives of XML, so the program writes them directly
and carries no third-party document library. Apache POI would add roughly 17MB to
the download - most of it for a feature used only when a report is handed to
somebody else.

The trade-off is that this is code we maintain rather than a library everyone
trusts, so it is checked two ways: `tools\test.ps1` writes files and reads the
package back, checking every required part is present and that the XML parses, and

```bat
python tools\check_office_files.py <folder>
```

opens the written files with `python-docx` and `openpyxl` - independent
implementations of the same formats - and confirms the text, headings and numbers
come back out. That is the check that would catch a file Word would refuse.

The report is rendered to HTML for the preview pane, then parsed back out of that
HTML to fill the Word or Excel file, so a saved report is the same document that
was on screen. Every report type gained both formats from that one change, and a
layout change shows up in all three outputs at once.

To write a whole set of files without opening the window:

```bat
run.bat --export-office build\reports
run.bat --export-reports build\reports
```

**Data & backup** - where the data is, how big it is, backup and restore, a
health check, and the teacher's name for report headings. It also holds a
language selector.

Keyboard shortcuts: `Ctrl+1`..`Ctrl+8` for the record screens, `Ctrl+F` search,
`Ctrl+P` reports, `Ctrl+R` refresh, `Ctrl+D` dashboard.

---

## Language

The interface is available in **English** and **Mongolian** (Cyrillic).

There is a language button at the right-hand end of the status bar. It shows the
language it will switch *to*, written in that language's own script - so
"Монгол" when the interface is English, and "English" when it is Mongolian.
Pressing it rebuilds the whole window, including the printed reports, and the
choice is stored in the database so the next start comes up in the same language.
The Data & backup screen has a language selector too.

A few details worth knowing:

- **Month and weekday names are supplied by the program.** The JDK ships no
  Mongolian locale data, so a standard date formatter silently falls back to
  English. `Dates` therefore carries the Mongolian names itself.
- **Attendance short codes are translated too** - the register buttons read
  И / Х / Т / Ч rather than P / L / A / E.
- **The teacher's own words are never translated.** Notes, reasons, plan titles,
  child names and class names are stored exactly as typed. Only the interface
  around them changes language.
- **Reports are titled in the chosen language**, and the `lang` attribute of the
  generated HTML follows it, so a browser hyphenates and spaces the text
  correctly.

### How the text is organised

| File | Purpose |
| --- | --- |
| `src/main/resources/app/kinderjournal/i18n/messages_en.properties` | The master list. Every key the interface uses. |
| `src/main/resources/app/kinderjournal/i18n/messages_mn.properties` | The Mongolian translation. |
| `src/main/java/app/kinderjournal/i18n/Messages.java` | Looks text up, substitutes `{0}` placeholders, and notifies the window when the language changes. |
| `src/main/java/app/kinderjournal/i18n/Lang.java` | The languages themselves. |
| `src/main/java/app/kinderjournal/util/Dates.java` | Date, month and weekday formatting per language. |

Text lives in plain `.properties` files with `#` comments, so it can be corrected
without touching Java. A missing translation falls back to English rather than
showing a blank label, which means a partial translation still leaves a usable
program - but a key missing from *English* is a mistake, and the tests fail on it.

### Adding or correcting a translation

1. Add or edit the line in both `.properties` files, keeping any `{0}`
   placeholders. Do not translate the placeholder itself.
2. Run `tools\test.ps1`. It reports how complete each language is, refuses an
   English key that the interface asks for but the file does not define, and
   fails if a translation drops or renumbers a placeholder - which would
   otherwise print a stray `{0}` on screen.

To add a third language, add a constant to `Lang`, copy
`messages_en.properties` to `messages_<code>.properties`, and translate. Nothing
else needs to change; screens build their labels from the catalogue, so the new
language appears in the toggle automatically.

---

## Printing and reports

Eleven documents are available:

| Document | Typical use |
| --- | --- |
| Daily attendance sheet | The register for one day, with a signature line. |
| Attendance summary | Per-child totals and class totals for a period. |
| Monthly attendance grid | A day-by-day grid of marks for a whole month. |
| Child profile sheet | Details and contacts, for the class file. |
| Observation report | One child's observations, grouped by developmental area. |
| Child history report | Everything about one child in a period, in date order. |
| Interview summary | Interviews with conclusions, plus outstanding follow-ups. |
| Daily activity plan | One day's activities with objectives, sequence and materials. |
| Weekly activity plan | The week at a glance, one column per day (landscape). |
| Class report for a period | Attendance, observations, interviews and plans together. |
| Search results | The records matching a search, listed with their content. |

How it works: the program generates a print-ready HTML document, shows it in a
preview window, and hands it to the Windows print dialog. Long reports break
across pages automatically, table headers repeat, and page numbers are added by
the print dialog. Choosing **Microsoft Print to PDF** there saves the report as a
PDF - no extra software needed. Photos are embedded in the document itself, so a
saved report still shows its pictures after being copied elsewhere.

A PDF is not produced silently: the teacher always sees the preview and chooses
the printer, which is also the point at which the content can be reviewed.

---

## Where your data lives

```
%APPDATA%\KinderJournal\
    kinderjournal.db      the SQLite database
    photos\               attached images, one file each
    backups\              backups, including the weekly automatic one
    reports\              reports opened for printing
    logs\                 the error log, written only when something fails
```

The exact path is shown at the bottom of the window and on the
**Data & backup** screen, with a button that opens the folder.

If that folder cannot be written to, the program falls back to
`%USERPROFILE%\.kinderjournal` and tells you at start-up, so a locked-down
machine still works.

---

## Backup and restore

**Backup** writes a single `.kjbackup` file containing the database *and* every
attached photo. The database part is copied using SQLite's own `VACUUM INTO`,
which produces a consistent snapshot even mid-write.

**Restore** unpacks the archive into a staging folder first, checks the database
inside it with SQLite's integrity check, *then* takes a safety copy of your
current data, and only after all of that replaces anything. A corrupt or
unrelated file is therefore rejected without touching what you already have.

The program also takes an **automatic backup at start-up** when the newest one is
more than a week old, keeping the last five automatic copies and never deleting a
backup you made yourself.

Backups contain children's personal information. Treat the `.kjbackup` file the
way you would treat any other student record.

---

## Privacy

- No network connections of any kind: no accounts, no telemetry, no sync.
- Nothing is uploaded, and no data leaves the computer unless you export it.
- Photos are copied into the program's folder, never referenced from their
  original location, so tidying up your Pictures folder cannot break an
  attachment.
- There is no password on the program itself. Because the records identify
  children, protect the computer with a Windows user account, and keep backups on
  encrypted media if your school requires it.

---

## Project layout

```
build.bat / run.bat / package.bat / demo.bat   one-click scripts
assets\                      optional program icon (see assets\README.txt)
tools\
  bootstrap.mjs     downloads and checksums the pinned dependencies
  build.ps1         compiles and copies resources
  run.ps1           launches, with optional -DataDir for a separate data folder
  test.ps1          runs the data-layer tests
  seed-demo.ps1     creates sample data
  package.ps1       builds the self-contained application
src\main\java\app\kinderjournal\
  App.java          entry point, window, diagnostics
  model\            domain objects: Group, Child, Attendance, Observation,
                    Interview, LessonPlan, Photo, HistoryEntry, Choices
  db\               SQLite: Db (schema, transactions, backup), one DAO per
                    record type, and Repository as the single entry point
  ui\               the window shell, shared widgets, calendar, photo strip
  ui\screen\        one class per screen
  report\           report types, options, HTML renderer and print preview
  backup\           backup and restore of the whole data folder
  util\             dates, text, data-folder resolution
  tools\            self-tests, demo data, batch report export
src\main\resources\app\kinderjournal\ui\app.css   the application stylesheet
```

The database schema is created on first run and versioned (`PRAGMA user_version`),
so a future release can migrate an existing file in place. Foreign keys are on and
`ON DELETE CASCADE` is used where a record cannot exist without its child, so an
orphaned observation or photo cannot be created.

---

## Development notes

**Tests.** `tools\test.ps1` runs 145 checks over the data layer with no display
attached: record-to-child-to-date linkage, correcting an attendance mark, merging
a child's history, searching every record type, rejecting a record for an unknown
child, and deleting a child without leaving anything behind. `run.bat --ui-test`
adds 74 checks that build every screen twice on a real JavaFX toolkit, generate all
eleven report types, and confirm reports embed their photos.

**Sample data.** `demo.bat` fills the data folder with two classes, six children,
six weeks of attendance, observations in every category, interviews with
follow-ups, a planned week and a couple of photos - useful for trying the program
or checking a change. Pass a folder (`demo.bat C:\Temp\kjdemo`) to keep the sample
data away from real records.

**Report export.** `run.bat --export-reports <folder>` writes all eleven documents
as HTML files without opening the window.

**Diagnostics.** `run.bat --diagnostics` prints the data folder, the database
integrity check, record counts and any missing photo files.

**Style.** The code favours long, descriptive names and comments that explain why
something is done rather than what the line does; formatting follows the usual
Java conventions. Currency of the UI is handled by a single `Refreshable`
contract, so a screen never needs to know which other screen changed a record.

---

## Command-line options

```
run.bat --diagnostics               print a report about the local data file
run.bat --diagnostics --verbose     ...including a full stack trace on failure
run.bat --version                   print the version and data folder
run.bat --seed-demo                 fill the data folder with sample data
run.bat --export-reports <folder>   write every report type as HTML
run.bat --ui-test                   build every screen and run the interface checks
run.bat --show mn                   open the window on sample data, in Mongolian
run.bat --data-dir <folder>         use an alternative data folder
```

`--show <language>` is for looking at the interface rather than asserting about
it: it opens on a full set of sample records and writes a child-history report per
language, which is how the printed output is compared.

The same options work on the packaged executable, and
`KINDERJOURNAL_DATA_DIR` can be set as an environment variable instead of passing
`--data-dir`.
