"""
Builds a throwaway KinderJournal data folder with three full classes.

This is a scratch tool: it writes the same SQLite schema the application creates
for itself, fills it with three classes of 30-45 children each, and leaves it
somewhere you can point the program at with:

    run.bat --data-dir data\\tryout
    KinderJournal.exe --data-dir <folder>

Nothing in the application is modified. Delete the folder when you are done with
it, or keep several and pass a different --data-dir each time. The real records in
%APPDATA%\\KinderJournal are never touched.

Everything is deterministic, so running it twice produces the same children, and
regenerating into the same folder starts from a clean copy of that folder.

    python tools/make_tryout_data.py --out data/tryout
"""

from __future__ import annotations

import argparse
import base64
import random
import shutil
import sqlite3
from datetime import date, timedelta
from pathlib import Path

# ---------------------------------------------------------------- name pools
# Deliberately mixed, so the class lists look like a real group in an
# English-speaking kindergarten rather than one naming tradition.

GIRLS = [
    "Amelia", "Ava", "Chloe", "Ella", "Emily", "Emma", "Eva", "Freya", "Grace",
    "Hannah", "Harper", "Imogen", "Isabella", "Isla", "Ivy", "Jasmine", "Layla",
    "Lily", "Lucy", "Maya", "Mia", "Millie", "Nora", "Olivia", "Phoebe", "Rosa",
    "Ruby", "Sara", "Scarlett", "Sienna", "Sofia", "Summer", "Tilly", "Willow",
    "Zara", "Aisha", "Fatima", "Leila", "Noor", "Yasmin", "Anaya", "Priya",
    "Mei", "Linh", "Sana", "Aria", "Bonnie", "Daisy", "Elsie", "Heidi",
]

BOYS = [
    "Aaron", "Adam", "Alfie", "Archie", "Arthur", "Ben", "Callum", "Charlie",
    "Daniel", "Dylan", "Ethan", "Felix", "Finley", "George", "Harry", "Henry",
    "Hugo", "Isaac", "Jack", "Jacob", "James", "Jonah", "Joseph", "Kai",
    "Leo", "Liam", "Logan", "Lucas", "Mason", "Max", "Milo", "Nathan", "Noah",
    "Oliver", "Oscar", "Owen", "Reuben", "Riley", "Rory", "Samuel", "Sebastian",
    "Theo", "Thomas", "Toby", "Tommy", "William", "Zachary", "Amir", "Idris",
    "Ravi", "Kenji", "Marco", "Tomas", "Andrei", "Yusuf",
]

SURNAMES = [
    "Adams", "Ahmed", "Ali", "Anderson", "Baker", "Bennett", "Brown", "Byrne",
    "Campbell", "Carter", "Chen", "Clark", "Collins", "Cooper", "Cox", "Davies",
    "Doyle", "Edwards", "Ellis", "Evans", "Fisher", "Fleming", "Foster", "Fraser",
    "Gallagher", "Garcia", "Gibson", "Grant", "Gray", "Green", "Griffiths",
    "Hall", "Harris", "Harrison", "Hayes", "Henderson", "Hill", "Holmes",
    "Hughes", "Hussain", "Jackson", "James", "Jenkins", "Johnson", "Jones",
    "Kaur", "Kelly", "Kennedy", "Khan", "King", "Knight", "Kowalski", "Lee",
    "Lewis", "Lloyd", "Lynch", "MacDonald", "Martin", "Mason", "Matthews",
    "McCarthy", "McDonald", "Miller", "Mitchell", "Moore", "Morgan", "Morris",
    "Murphy", "Murray", "Nelson", "Nguyen", "Novak", "O'Brien", "Owen", "Palmer",
    "Parker", "Patel", "Pearce", "Peters", "Phillips", "Powell", "Price",
    "Quinn", "Rees", "Reid", "Reynolds", "Rice", "Richardson", "Roberts",
    "Robertson", "Robinson", "Rogers", "Ross", "Russell", "Ryan", "Sanders",
    "Scott", "Shah", "Shaw", "Simpson", "Singh", "Smith", "Spencer", "Stewart",
    "Stone", "Sullivan", "Taylor", "Thomas", "Thompson", "Turner", "Walker",
    "Wallace", "Walsh", "Ward", "Watson", "Webb", "West", "White", "Wilkinson",
    "Williams", "Wilson", "Wood", "Wright", "Young",
]

STREETS = [
    "Acacia Avenue", "Albert Road", "Ash Close", "Baker Street", "Beech Way",
    "Birch Lane", "Bridge Street", "Cedar Drive", "Chapel Lane", "Cherry Tree Road",
    "Church Street", "Clover Close", "Daisy Bank Road", "Elm Grove", "Field Lane",
    "Fir Tree Avenue", "George Street", "Granary Close", "Green Lane", "Hawthorn Road",
    "High Street", "Holly Bank", "Ivy House Lane", "King Street", "Laburnum Road",
    "Larkspur Way", "Laurel Gardens", "Lime Kiln Lane", "Maple Avenue", "Market Street",
    "Meadow Close", "Mill Lane", "New Road", "Oakfield Road", "Orchard Way",
    "Park Road", "Pine Crescent", "Primrose Hill", "Queen Street", "Rectory Lane",
    "Rosewood Drive", "Sandpiper Close", "School Lane", "Springfield Road",
    "Station Road", "Sycamore Avenue", "The Green", "Vicarage Road", "Willow Bank",
    "Windsor Crescent",
]

RELATIONS = ["mother", "father", "grandmother", "grandfather", "aunt", "uncle",
             "foster mother", "foster father", "stepmother", "stepfather"]

ALLERGIES = [
    "Peanuts - EpiPen kept in the office",
    "Tree nuts",
    "Lactose intolerance - soya milk provided",
    "Eggs",
    "Gluten - coeliac, separate snacks supplied",
    "Bee and wasp stings - antihistamine in the medical box",
    "Penicillin",
    "Strawberries",
    "Sesame",
    "Animal hair - mild asthma, inhaler in bag",
]

# --------------------------------------------------------------- observation
# (category, title, body, context)

OBSERVATIONS = [
    ("language", "Used a full sentence to ask for help",
     "Asked \"can you help me zip my coat please\" without prompting, and waited for an answer.",
     "home corner"),
    ("language", "Retold the story in the right order",
     "Put the six picture cards in sequence and retold the story with beginning, middle and end.",
     "story time"),
    ("language", "New words at circle time",
     "Used three new words from this week's story and asked a follow-up question about it.",
     "morning circle"),
    ("language", "Wrote own name without a model",
     "Wrote their first name from memory, forming all the letters correctly.",
     "writing table"),
    ("social", "Invited a friend to play",
     "Asked another child to join the block corner and shared the pieces without being prompted.",
     "free play"),
    ("social", "Took turns without a reminder",
     "Waited for a turn on the bikes for several minutes and reminded a friend to wait too.",
     "outdoor play"),
    ("social", "Resolved a disagreement by talking",
     "When two others wanted the same tractor, suggested they take turns and counted the laps.",
     "sand tray"),
    ("social", "Played cooperatively in a small group",
     "Built a shared garage with two others and agreed who would do which part.",
     "construction area"),
    ("emotional", "Settled quickly after goodbye",
     "Was sad when mum left, then joined the puzzle table within a few minutes and stayed settled.",
     "arrival"),
    ("emotional", "Talked about feeling frustrated",
     "Said \"I'm cross because it won't fit\", then accepted help and finished the model.",
     "block corner"),
    ("emotional", "Comforted another child",
     "Noticed a friend was upset and brought them a tissue and a book.",
     "book corner"),
    ("emotional", "Confident on the climbing frame",
     "Climbed to the top rung and came down carefully, checking footing on each step.",
     "outdoor play"),
    ("physical", "Held a pencil with a tripod grip",
     "Used a comfortable grip for the whole drawing and stayed inside the lines.",
     "writing table"),
    ("physical", "Balanced along the bench",
     "Walked the whole length of the bench without stepping off, arms out for balance.",
     "hall"),
    ("physical", "Pedalled the trike around the circuit",
     "Steered around the cones and stopped at the marked line.",
     "outdoor play"),
    ("physical", "Cut along a curved line",
     "Cut a circle out of card, turning the paper rather than the scissors.",
     "art table"),
    ("physical", "Fastened coat buttons independently",
     "Did up all four buttons and the zip without help at home time.",
     "cloakroom"),
    ("cognitive", "Sorted by two attributes",
     "Sorted the buttons by colour and then by size, and explained the rule out loud.",
     "maths table"),
    ("cognitive", "Counted accurately to ten",
     "Counted ten bears into a pot, touching each one once.",
     "maths table"),
    ("cognitive", "Noticed a repeating pattern",
     "Continued a red-blue-red-blue pattern with beads and said what would come next.",
     "maths table"),
    ("cognitive", "Predicted what would happen next",
     "Said the ice would melt in the water tray, and checked back on it later.",
     "science table"),
    ("cognitive", "Solved a twelve-piece puzzle",
     "Completed the puzzle alone, using the picture on the box to check the edges.",
     "quiet table"),
    ("participation", "Helped set the table",
     "Carried the cups to the snack table and counted them out for each chair.",
     "snack time"),
    ("participation", "Joined in the singing action song",
     "Did all the actions and sang the chorus with the group.",
     "music time"),
    ("participation", "Volunteered to be the helper",
     "Put a hand up straight away to hand out the aprons.",
     "art table"),
    ("participation", "Sat and listened for the whole story",
     "Stayed focused through a ten-minute story and answered two questions about it.",
     "story time"),
    ("achievement", "First time on the big slide",
     "Climbed up alone, paused at the top, then slid down and went straight back for another turn.",
     "outdoor play"),
    ("achievement", "Learned to write the letter S",
     "Practised on the whiteboard and then wrote it without a model.",
     "writing table"),
    ("achievement", "Finished a whole week's growing diary",
     "Drew the bean shoot on all five days and told the group what had changed.",
     "science table"),
    ("behaviour", "Restless after lunch",
     "Found it hard to sit through the story; settled once given a job to do.",
     "afternoon story"),
    ("behaviour", "Found sharing the bikes difficult",
     "Wanted to stay on the bike; accepted the timer after a reminder.",
     "outdoor play"),
    ("support", "Needs help with scissors",
     "Found cutting along a line difficult; will practise with thicker paper.",
     "art table"),
    ("support", "Working on saying sounds clearly",
     "Speech sounds for 'th' and 'r' are still developing; practising with picture cards.",
     "small group"),
    ("support", "Confident in English, quieter in first language",
     "Joins in well in English; parents would like the home language kept up too.",
     "small group"),
    ("general", "Enjoyed the nature walk",
     "Collected six different leaves and wanted to know the name of each tree.",
     "outdoor play"),
]

INTERVIEW_TOPICS = [
    ("Settling in, sleep routine, friendships",
     "Says mornings are easier now and that they have a friend they look for at the gate.",
     "No concerns about the transition. Tiredness is normal for the first term.",
     "Start sending the weekly activity note home on Fridays", False),
    ("Progress with talking and listening",
     "Talked about how much they enjoy story time and asked what books we use.",
     "Language is coming on well. Will keep the parents posted on the sounds we practise.",
     "Send home the picture cards we use for 'th' and 'r'", False),
    ("Toilet training and independence",
     "A few accidents at home at the weekend; none at nursery.",
     "Consistent with what we see. Will keep prompting at the same times.",
     "", True),
    ("Friendships and turn taking",
     "Mentioned a falling out over the bikes and wanted to know how we handle it.",
     "Agreed to use the sand timer for the bikes so turns are visible to everyone.",
     "Put the timer by the bike store and show it to the children", False),
    ("Getting ready for school",
     "Asked what the reception class expects and whether we think they are ready.",
     "Doing well with self-care and listening. Will focus on changing for PE.",
     "Practise putting on and taking off a coat and shoes", False),
    ("Allergies and snack time",
     "Wanted to check which snacks are safe and where the EpiPen is kept.",
     "Reviewed the snack list together and showed where the medical box is.",
     "Send the updated snack list to all parents in the class", False),
    ("Screen time and sleep",
     "Concerned that late screen time is making mornings difficult.",
     "Suggested a wind-down routine; will note how the child seems in the mornings.",
     "", False),
    ("A trip to the speech and language service",
     "Referred by the health visitor and asked what we can support in the setting.",
     "Happy to run the same games here and to share what we notice.",
     "Agree a shared record sheet with the speech therapist", False),
]

PLANS = [
    ("All about me", "theme", "Talk about families, homes and what makes each of us different.",
     "Family photos, mirrors, large paper, felt pens", 5),
    ("Autumn walk and leaf hunt", "outdoor", "Notice seasonal change and collect natural materials.",
     "Collecting bags, magnifiers, wellington boots", 3),
    ("Counting to ten", "maths", "Count objects accurately and match a numeral to a quantity.",
     "Buttons, pots, numeral cards, dice", 5),
    ("Story sequencing", "language", "Retell a story in the right order.",
     "Picture cards, felt board, The Enormous Turnip", 4),
    ("Colour mixing", "art", "Explore what happens when two colours meet.",
     "Powder paint, palettes, thick brushes, aprons", 4),
    ("Obstacle course", "motor", "Practise balancing, jumping and crawling.",
     "Cones, bench, mats, hoops", 6),
    ("Making bread", "science", "Notice how dough changes as it is mixed and baked.",
     "Flour, yeast, bowls, oven (staff only), butter", 3),
    ("Rhymes and clapping games", "music", "Keep a steady beat and join in with a group.",
     "Claves, tambourines, song cards", 4),
    ("Building a den", "play", "Plan together, then test and change the plan.",
     "Sheets, pegs, large blocks, rope", 5),
    ("Keeping safe and healthy", "routine", "Talk about hand washing, roads and who helps us.",
     "Soap, picture cards, role-play uniforms", 4),
]

# --------------------------------------------------------------- sample photo
# A neutral 64x64 picture, so a few children have a profile photo and the round
# avatar crops can be looked at.


def sample_png() -> bytes:
    """A tiny valid PNG with a soft two-tone blob, built by hand."""
    size = 64
    rows = []
    for y in range(size):
        row = bytearray([0])  # PNG filter byte
        for x in range(size):
            dx, dy = x - 32, y - 32
            inside = dx * dx + dy * dy < 26 * 26
            if inside:
                row += bytes((15, 118, 110, 255))
            else:
                row += bytes((227, 242, 239, 255))
        rows.append(bytes(row))
    raw = b"".join(rows)

    import zlib
    import struct

    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    header = struct.pack(">IIBBBBB", size, size, 8, 6, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", header)
            + chunk(b"IDAT", zlib.compress(raw, 9))
            + chunk(b"IEND", b""))


# ------------------------------------------------------------------- helpers

def iso(day: date) -> str:
    return day.isoformat()


def now_stamp(day: date, hour: int = 9) -> str:
    return f"{day.isoformat()} {hour:02d}:00:00"


def birth_date(rng: random.Random, window_from: date, window_to: date) -> date:
    """A date of birth inside the class's window, with a day that varies."""
    span = max(0, (window_to - window_from).days)
    return window_from + timedelta(days=rng.randint(0, span))


def email_for(first: str, last: str, rng: random.Random) -> str:
    style = rng.randint(0, 3)
    first_l = first.lower().replace("'", "")
    last_l = last.lower().replace("'", "").replace(" ", "")
    if style == 0:
        return f"{first_l}.{last_l}@example.com"
    if style == 1:
        return f"{first_l}{last_l}@example.com"
    if style == 2:
        return f"{first_l[0]}.{last_l}@example.com"
    return f"{last_l}.family@example.com"


def phone(rng: random.Random) -> str:
    return f"07{rng.randint(100, 999)} {rng.randint(100000, 999999)}"


# -------------------------------------------------------------------- builder

def build(out: Path, seed: int, force: bool) -> None:
    rng = random.Random(seed)
    today = date.today()
    term_start = today - timedelta(days=48)
    monday = today - timedelta(days=today.weekday())

    if out.exists():
        if not force:
            raise SystemExit(f"{out} already exists. Pass --force to rebuild it.")
        shutil.rmtree(out)
    (out / "photos").mkdir(parents=True)
    (out / "logs").mkdir(parents=True)
    (out / "backups").mkdir(parents=True)

    db_path = out / "kinderjournal.db"
    db = sqlite3.connect(db_path)
    db.execute("PRAGMA foreign_keys = ON")

    # The schema the application creates for itself, copied from Db.java.
    db.executescript("""
        CREATE TABLE groups (
            id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
            description TEXT NOT NULL DEFAULT '', age_range TEXT NOT NULL DEFAULT '',
            room TEXT NOT NULL DEFAULT '', teacher TEXT NOT NULL DEFAULT '',
            school_year TEXT NOT NULL DEFAULT '', archived INTEGER NOT NULL DEFAULT 0,
            sort_order INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT '',
            updated_at TEXT NOT NULL DEFAULT '');
        CREATE TABLE children (
            id INTEGER PRIMARY KEY AUTOINCREMENT, first_name TEXT NOT NULL DEFAULT '',
            last_name TEXT NOT NULL DEFAULT '', birth_date TEXT,
            gender TEXT NOT NULL DEFAULT '', group_id INTEGER REFERENCES groups(id) ON DELETE SET NULL,
            start_date TEXT, guardian_name TEXT NOT NULL DEFAULT '',
            guardian_relation TEXT NOT NULL DEFAULT '', guardian_phone TEXT NOT NULL DEFAULT '',
            guardian_email TEXT NOT NULL DEFAULT '', second_contact TEXT NOT NULL DEFAULT '',
            address TEXT NOT NULL DEFAULT '', allergies TEXT NOT NULL DEFAULT '',
            notes TEXT NOT NULL DEFAULT '', photo_file TEXT,
            archived INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT '',
            updated_at TEXT NOT NULL DEFAULT '');
        CREATE TABLE attendance (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
            group_id INTEGER REFERENCES groups(id) ON DELETE SET NULL, date TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'present', reason TEXT NOT NULL DEFAULT '',
            note TEXT NOT NULL DEFAULT '', arrival_time TEXT NOT NULL DEFAULT '',
            departure_time TEXT NOT NULL DEFAULT '', recorded_at TEXT NOT NULL DEFAULT '');
        CREATE UNIQUE INDEX idx_attendance_child_date ON attendance(child_id, date);
        CREATE INDEX idx_attendance_date ON attendance(date);
        CREATE INDEX idx_attendance_group_date ON attendance(group_id, date);
        CREATE TABLE observations (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
            group_id INTEGER REFERENCES groups(id) ON DELETE SET NULL, date TEXT NOT NULL,
            category TEXT NOT NULL DEFAULT 'general', title TEXT NOT NULL DEFAULT '',
            body TEXT NOT NULL DEFAULT '', context TEXT NOT NULL DEFAULT '',
            author TEXT NOT NULL DEFAULT '', confidential INTEGER NOT NULL DEFAULT 0,
            created_at TEXT NOT NULL DEFAULT '', updated_at TEXT NOT NULL DEFAULT '');
        CREATE INDEX idx_observations_child_date ON observations(child_id, date);
        CREATE INDEX idx_observations_date ON observations(date);
        CREATE INDEX idx_observations_category ON observations(category);
        CREATE TABLE interviews (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
            group_id INTEGER REFERENCES groups(id) ON DELETE SET NULL, date TEXT NOT NULL,
            type TEXT NOT NULL DEFAULT 'parent', mode TEXT NOT NULL DEFAULT 'in-person',
            participants TEXT NOT NULL DEFAULT '', topics TEXT NOT NULL DEFAULT '',
            discussed TEXT NOT NULL DEFAULT '', conclusions TEXT NOT NULL DEFAULT '',
            follow_up TEXT NOT NULL DEFAULT '', follow_up_date TEXT,
            follow_up_done INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT '',
            updated_at TEXT NOT NULL DEFAULT '');
        CREATE INDEX idx_interviews_child_date ON interviews(child_id, date);
        CREATE INDEX idx_interviews_date ON interviews(date);
        CREATE TABLE lesson_plans (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            group_id INTEGER REFERENCES groups(id) ON DELETE SET NULL,
            title TEXT NOT NULL DEFAULT '', date TEXT NOT NULL, end_date TEXT,
            start_time TEXT NOT NULL DEFAULT '', end_time TEXT NOT NULL DEFAULT '',
            area TEXT NOT NULL DEFAULT 'other', objectives TEXT NOT NULL DEFAULT '',
            description TEXT NOT NULL DEFAULT '', materials TEXT NOT NULL DEFAULT '',
            sequence TEXT NOT NULL DEFAULT '', instructions TEXT NOT NULL DEFAULT '',
            status TEXT NOT NULL DEFAULT 'planned', reflection TEXT NOT NULL DEFAULT '',
            template INTEGER NOT NULL DEFAULT 0, recurring INTEGER NOT NULL DEFAULT 0,
            sort_order INTEGER NOT NULL DEFAULT 0, created_at TEXT NOT NULL DEFAULT '',
            updated_at TEXT NOT NULL DEFAULT '');
        CREATE INDEX idx_plans_date ON lesson_plans(date);
        CREATE INDEX idx_plans_group_date ON lesson_plans(group_id, date);
        CREATE INDEX idx_plans_template ON lesson_plans(template);
        CREATE TABLE photos (
            id INTEGER PRIMARY KEY AUTOINCREMENT, owner_type TEXT NOT NULL,
            owner_id INTEGER NOT NULL,
            child_id INTEGER REFERENCES children(id) ON DELETE CASCADE,
            file TEXT NOT NULL, caption TEXT NOT NULL DEFAULT '',
            sort_order INTEGER NOT NULL DEFAULT 0, added_at TEXT NOT NULL DEFAULT '');
        CREATE INDEX idx_photos_owner ON photos(owner_type, owner_id);
        CREATE INDEX idx_photos_child ON photos(child_id);
        CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL DEFAULT '');
    """)
    # Db.SCHEMA_VERSION. The application refuses to open a file whose user_version
    # is higher than it knows, so this must match.
    db.execute("PRAGMA user_version = 1")

    photo_name = "sample-photo.png"
    (out / "photos" / photo_name).write_bytes(sample_png())

    # Three classes, one for each year of the kindergarten. The date window is
    # what makes the ages match the label: a class described as 3-4 should not
    # contain a child who is still 2, so each window is set around the age reached
    # during the current school year rather than around the calendar year.
    classes = [
        ("Acorn Class", "3\u20134", "Room 1", "Ms Farah Osman",
         date(2022, 9, 1), date(2023, 3, 31), 34),
        ("Sunflower Class", "4\u20135", "Room 2", "Mr Daniel Whitfield",
         date(2021, 9, 1), date(2022, 3, 31), 41),
        ("Oak Class", "5\u20136", "Room 3", "Mrs Priya Raman",
         date(2020, 9, 1), date(2021, 3, 31), 38),
    ]

    group_ids = {}
    for order, (name, ages, room, teacher, _lo, _hi, _count) in enumerate(classes, start=1):
        cur = db.execute(
            "INSERT INTO groups(name, description, age_range, room, teacher, school_year,"
            " archived, sort_order, created_at, updated_at)"
            " VALUES(?,?,?,?,?,?,0,?,?,?)",
            (name, f"A sample class for trying the program out. {ages} year olds.",
             ages, room, teacher, "2025/2026", order,
             now_stamp(term_start - timedelta(days=30)), now_stamp(today)))
        group_ids[name] = cur.lastrowid

    child_ids: dict[str, list[int]] = {}
    active_ids: dict[str, list[int]] = {}
    used_names: set[tuple[str, str]] = set()
    written = {"children": 0, "attendance": 0, "observations": 0,
               "interviews": 0, "plans": 0, "photos": 0}

    for class_name, ages, room, teacher, lo, hi, count in classes:
        gid = group_ids[class_name]
        child_ids[class_name] = []
        active_ids[class_name] = []
        for index in range(count):
            # A unique name, so the class list has no duplicate rows.
            for _attempt in range(400):
                first = rng.choice(GIRLS if rng.random() < 0.5 else BOYS)
                last = rng.choice(SURNAMES)
                if (first, last) not in used_names:
                    used_names.add((first, last))
                    break
            gender = "F" if first in GIRLS else "M"
            born = birth_date(rng, lo, hi)
            relation = rng.choice(RELATIONS)
            guardian_first = rng.choice(GIRLS + BOYS)
            guardian = f"{guardian_first} {last}" if relation in (
                "mother", "father", "stepmother", "stepfather", "foster mother",
                "foster father") else f"{rng.choice(GIRLS + BOYS)} {rng.choice(SURNAMES)}"

            allergies = ""
            if rng.random() < 0.16:
                allergies = rng.choice(ALLERGIES)
            notes = ""
            if allergies:
                notes = "Allergy plan on file. See the medical folder."
            elif rng.random() < 0.18:
                notes = rng.choice([
                    "Attends speech and language on Tuesday mornings.",
                    "Collected by grandmother on Wednesdays.",
                    "Only attends mornings; goes home after lunch.",
                    "Joined us from another nursery this term.",
                    "Parents would like a copy of the weekly plan by email.",
                    "Has a hearing check booked for next month.",
                    "Sleeping badly at the moment; parents are aware.",
                    "Speaks Polish at home and English here.",
                    "Needs to sit near the front at story time.",
                    "Will be away for two weeks in November.",
                ])

            started = term_start - timedelta(days=rng.randint(0, 240)) \
                if rng.random() < 0.75 else term_start + timedelta(days=rng.randint(1, 20))

            # A few archived children per class, spread through the list, so the
            # "Include archived" filter has something to show.
            archived = 1 if index % 11 == 7 else 0
            has_photo = rng.random() < 0.22
            cur = db.execute(
                "INSERT INTO children(first_name, last_name, birth_date, gender, group_id,"
                " start_date, guardian_name, guardian_relation, guardian_phone,"
                " guardian_email, second_contact, address, allergies, notes, photo_file,"
                " archived, created_at, updated_at)"
                " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                (first, last, iso(born), gender, gid, iso(started), guardian, relation,
                 phone(rng), email_for(guardian.split()[0], last, rng),
                 rng.choice(["", phone(rng)]),
                 f"{rng.randint(1, 180)} {rng.choice(STREETS)}",
                 allergies, notes, photo_name if has_photo else None,
                 archived, now_stamp(started), now_stamp(today)))
            cid = cur.lastrowid
            child_ids[class_name].append(cid)
            if not archived:
                # Only children still on roll get a term's worth of records; an
                # archived child having a full register would look wrong.
                active_ids[class_name].append(cid)
            written["children"] += 1

            if has_photo:
                db.execute(
                    "INSERT INTO photos(owner_type, owner_id, child_id, file, caption,"
                    " sort_order, added_at) VALUES('child',?,?,?,?,0,?)",
                    (cid, cid, photo_name, "Profile picture", now_stamp(today)))
                written["photos"] += 1

            # Attendance for the last seven weeks of weekdays: mostly present,
            # with an occasional absence, lateness or excused day.
            for day in range(49):
                the_day = term_start + timedelta(days=day)
                if the_day.weekday() >= 5 or the_day > today:
                    continue
                if archived and the_day > today - timedelta(days=14):
                    continue
                roll = rng.random()
                if roll < 0.055:
                    status, reason = "absent", rng.choice(
                        ["unwell", "family appointment", "holiday", "no reason given"])
                    arrival = departure = ""
                elif roll < 0.10:
                    status, reason = "late", rng.choice(
                        ["late bus", "overslept", "appointment", "weather"])
                    arrival, departure = f"09:{rng.randint(10, 45)}", "15:30"
                elif roll < 0.125:
                    status, reason = "excused", rng.choice(
                        ["medical appointment", "family bereavement", "religious holiday",
                         "hospital appointment"])
                    arrival = departure = ""
                else:
                    status, reason = "present", ""
                    arrival, departure = "08:45", rng.choice(["15:30", "15:30", "12:30"])
                db.execute(
                    "INSERT INTO attendance(child_id, group_id, date, status, reason, note,"
                    " arrival_time, departure_time, recorded_at) VALUES(?,?,?,?,?,'',?,?,?)",
                    (cid, gid, iso(the_day), status, reason, arrival, departure,
                     now_stamp(the_day, 9)))
                written["attendance"] += 1

            # Most children have a few observations; a minority have none, which is
            # realistic and exercises the "nothing recorded" states.
            for _ in range(rng.choice([0, 1, 1, 2, 2, 3])):
                cat, title, body, where = rng.choice(OBSERVATIONS)
                when = term_start + timedelta(days=rng.randint(0, 47))
                db.execute(
                    "INSERT INTO observations(child_id, group_id, date, category, title, body,"
                    " context, author, confidential, created_at, updated_at)"
                    " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                    (cid, gid, iso(when), cat, title, body, where, teacher,
                     1 if rng.random() < 0.08 else 0,
                     now_stamp(when, 14), now_stamp(when, 14)))
                written["observations"] += 1

            for _ in range(rng.choice([0, 0, 0, 1, 1, 2])):
                topics, discussed, conclusions, follow_up, done = rng.choice(INTERVIEW_TOPICS)
                when = term_start + timedelta(days=rng.randint(2, 45))
                db.execute(
                    "INSERT INTO interviews(child_id, group_id, date, type, mode, participants,"
                    " topics, discussed, conclusions, follow_up, follow_up_date, follow_up_done,"
                    " created_at, updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    (cid, gid, iso(when),
                     rng.choice(["parent", "child", "guardian", "professional"]),
                     rng.choice(["in-person", "telephone", "video", "written"]),
                     f"{guardian} ({relation}), {teacher}",
                     topics, discussed, conclusions, follow_up,
                     iso(when + timedelta(days=14)) if follow_up else None,
                     1 if done else 0, now_stamp(when, 16), now_stamp(when, 16)))
                written["interviews"] += 1

        # A week of plans per class, plus a couple of reusable templates.
        for plan_index, (title, area, objectives, materials, _sessions) in enumerate(
                rng.sample(PLANS, 6)):
            when = monday + timedelta(days=plan_index)
            status = "completed" if when < today else "planned"
            reflection = ""
            if status == "completed":
                reflection = rng.choice([
                    "Went well; the group stayed with it for the whole session.",
                    "Needed more materials out on the table.",
                    "Some children finished quickly - add an extension next time.",
                    "Good discussion; several children made links to home.",
                ])
            db.execute(
                "INSERT INTO lesson_plans(group_id, title, date, start_time, end_time, area,"
                " objectives, description, materials, sequence, status, reflection, template,"
                " sort_order, created_at, updated_at)"
                " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0,?,?,?)",
                (gid, title, iso(when), "09:30", "10:15", area, objectives,
                 f"A {area} session for the whole class.", materials,
                 "1. Gather on the carpet\n2. Introduce the activity\n3. Work in small groups\n"
                 "4. Tidy up together\n5. Review at the end",
                 status, reflection, plan_index, now_stamp(when - timedelta(days=3), 15),
                 now_stamp(when, 11)))
            written["plans"] += 1

        for title, area, objectives, materials, _sessions in rng.sample(PLANS, 2):
            db.execute(
                "INSERT INTO lesson_plans(group_id, title, date, start_time, end_time, area,"
                " objectives, description, materials, sequence, status, reflection, template,"
                " recurring, sort_order, created_at, updated_at)"
                " VALUES(?,?,?,?,?,?,?,?,?,?,'planned','',1,0,0,?,?)",
                (gid, title, iso(monday), "09:30", "10:15", area, objectives,
                 "Reusable template - adapt the materials and timings.", materials,
                 "1. Warm up\n2. Main activity\n3. Extension\n4. Review",
                 now_stamp(monday - timedelta(days=7), 15), now_stamp(monday, 12)))
            written["plans"] += 1

    # A few observation photos, so the photo tiles have something in them.
    for class_name, ids in active_ids.items():
        for cid in rng.sample(ids, min(3, len(ids))):
            obs = db.execute(
                "SELECT id FROM observations WHERE child_id = ? ORDER BY date DESC LIMIT 1",
                (cid,)).fetchone()
            if obs:
                db.execute(
                    "INSERT INTO photos(owner_type, owner_id, child_id, file, caption,"
                    " sort_order, added_at) VALUES('observation',?,?,?,?,0,?)",
                    (obs[0], cid, photo_name,
                     rng.choice(["Ordering the story cards", "Our bean shoots",
                                 "Building the den", "Leaf printing"]),
                     now_stamp(today)))
                written["photos"] += 1

    db.executemany("INSERT INTO settings(key, value) VALUES(?,?)", [
        ("created_at", now_stamp(today)),
        ("language", "en"),
        ("teacher_name", "Ms Farah Osman"),
        ("last_group_id", str(group_ids["Sunflower Class"])),
    ])
    db.commit()

    counts = db.execute(
        "SELECT (SELECT count(*) FROM groups), (SELECT count(*) FROM children),"
        " (SELECT count(*) FROM attendance), (SELECT count(*) FROM observations),"
        " (SELECT count(*) FROM interviews), (SELECT count(*) FROM lesson_plans),"
        " (SELECT count(*) FROM photos)").fetchone()
    per_class = db.execute(
        "SELECT g.name, count(c.id) FROM groups g LEFT JOIN children c"
        " ON c.group_id = g.id GROUP BY g.id ORDER BY g.sort_order").fetchall()
    db.close()

    print(f"Built a try-out data folder at {out.resolve()}")
    print(f"  classes      : {counts[0]}")
    print(f"  children     : {counts[1]}   (per class: "
          + ", ".join(f"{n.split()[0]} {c}" for n, c in per_class) + ")")
    print(f"  attendance   : {counts[2]} rows")
    print(f"  observations : {counts[3]}")
    print(f"  interviews   : {counts[4]}")
    print(f"  plans        : {counts[5]}")
    print(f"  photos       : {counts[6]}")
    print()
    print("Open it with:")
    print(f"  run.bat --data-dir {out}")
    print("or double-click KinderJournal.exe and pass the same --data-dir.")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", default="data/tryout",
                        help="folder to build (default: data/tryout)")
    parser.add_argument("--seed", type=int, default=20261012,
                        help="change this for a different set of children")
    parser.add_argument("--force", action="store_true",
                        help="rebuild even if the folder already exists")
    args = parser.parse_args()
    build(Path(args.out), args.seed, args.force)


if __name__ == "__main__":
    main()
