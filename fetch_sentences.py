#!/usr/bin/env python3
"""
Fetches public-domain texts and generates per-language value CSV files for
elastic-lab test data generation. Writes into:
    backend/src/main/resources/testdata/values/

Run once from anywhere in the repo:
    python3 fetch_sentences.py

Sources:
  sentences — Project Gutenberg (EN/DE/ES) + Wikipedia extracts (FR/RU/AR/HE/ZH)
  words     — Wikipedia random article titles per language
  cities    — Wikipedia category members per language
  colors    — hardcoded translations
"""

import json
import random
import re
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

OUT_DIR = Path(__file__).parent / "backend/src/main/resources/testdata/values"

LANG_CODES = ["en", "fr", "de", "es", "ru", "ar", "he", "zh"]

SENTENCES_PER_LANG = 150
MIN_SENTENCE_LEN = 40
MAX_SENTENCE_LEN = 200

WORDS_PER_LANG = 200
MIN_WORD_LEN = 3
MAX_WORD_LEN = 25

CITIES_PER_LANG = 150

# ---------------------------------------------------------------------------
# Sources
# ---------------------------------------------------------------------------

GUTENBERG_SENTENCES = [
    ("en", "https://www.gutenberg.org/files/1342/1342-0.txt"),  # Pride and Prejudice
    ("de", "https://www.gutenberg.org/files/2229/2229-0.txt"),  # Faust
    ("es", "https://www.gutenberg.org/files/2000/2000-0.txt"),  # Don Quijote
]

WIKIPEDIA_SENTENCES = [
    ("fr", "https://fr.wikipedia.org/w/api.php?action=query&titles=France&prop=extracts&exsectionformat=plain&format=json"),
    ("ru", "https://ru.wikipedia.org/w/api.php?action=query&titles=%D0%A0%D0%BE%D1%81%D1%81%D0%B8%D1%8F&prop=extracts&exsectionformat=plain&format=json"),
    ("ar", "https://ar.wikipedia.org/w/api.php?action=query&titles=%D9%85%D8%B5%D8%B1&prop=extracts&exsectionformat=plain&format=json"),
    ("he", "https://he.wikipedia.org/w/api.php?action=query&titles=%D7%99%D7%A9%D7%A8%D7%90%D7%9C&prop=extracts&exsectionformat=plain&format=json"),
    ("zh", "https://zh.wikipedia.org/w/api.php?action=query&titles=%E4%B8%AD%E5%8D%8E%E4%BA%BA%E6%B0%91%E5%85%B1%E5%92%8C%E5%9B%BD&prop=extracts&exsectionformat=plain&format=json&redirects=1"),
]

CITIES_BY_LANG = {
    "en": ["Amsterdam","Athens","Auckland","Bangkok","Barcelona","Beijing","Berlin","Brussels",
           "Buenos Aires","Cairo","Cape Town","Chicago","Copenhagen","Dallas","Delhi","Dubai",
           "Dublin","Frankfurt","Hamburg","Helsinki","Hong Kong","Istanbul","Jakarta","Johannesburg",
           "Karachi","Lagos","Lima","Lisbon","London","Los Angeles","Madrid","Manila","Melbourne",
           "Mexico City","Miami","Milan","Montreal","Moscow","Mumbai","Munich","Nairobi","New York",
           "Oslo","Paris","Prague","Rome","San Francisco","Santiago","São Paulo","Seoul",
           "Shanghai","Singapore","Stockholm","Sydney","Taipei","Tokyo","Toronto","Vienna",
           "Warsaw","Zurich"],
    "fr": ["Paris","Lyon","Marseille","Bordeaux","Toulouse","Nice","Nantes","Strasbourg",
           "Montpellier","Lille","Rennes","Reims","Grenoble","Dijon","Angers","Nîmes",
           "Le Mans","Aix-en-Provence","Clermont-Ferrand","Brest","Tours","Amiens","Limoges",
           "Toulon","Metz","Besançon","Perpignan","Orléans","Rouen","Mulhouse","Caen","Nancy"],
    "de": ["Berlin","Hamburg","München","Köln","Frankfurt","Stuttgart","Düsseldorf","Dortmund",
           "Essen","Leipzig","Bremen","Dresden","Hannover","Nürnberg","Duisburg","Bochum",
           "Wuppertal","Bielefeld","Bonn","Münster","Mannheim","Karlsruhe","Augsburg",
           "Wiesbaden","Gelsenkirchen","Mönchengladbach","Braunschweig","Kiel","Aachen",
           "Chemnitz","Halle","Magdeburg","Freiburg","Krefeld","Lübeck","Oberhausen","Erfurt"],
    "es": ["Madrid","Barcelona","Valencia","Sevilla","Zaragoza","Málaga","Murcia","Palma",
           "Las Palmas","Bilbao","Alicante","Córdoba","Valladolid","Vigo","Gijón","Granada",
           "Elche","Oviedo","Badalona","Cartagena","Terrassa","Jerez de la Frontera","Sabadell",
           "Santa Cruz de Tenerife","Pamplona","Almería","Fuenlabrada","Leganés","San Sebastián",
           "Burgos","Salamanca","Albacete","Alcalá de Henares","Getafe","Logroño","Badajoz"],
    "ru": ["Москва","Санкт-Петербург","Новосибирск","Екатеринбург","Нижний Новгород","Казань",
           "Челябинск","Омск","Самара","Ростов-на-Дону","Уфа","Красноярск","Пермь","Волгоград",
           "Воронеж","Краснодар","Саратов","Тюмень","Тольятти","Ижевск","Барнаул","Ульяновск",
           "Владивосток","Ярославль","Иркутск","Хабаровск","Махачкала","Новокузнецк","Оренбург",
           "Томск","Кемерово","Астрахань","Рязань","Набережные Челны","Пенза","Липецк","Тула"],
    "ar": ["القاهرة","الإسكندرية","الجيزة","شبرا الخيمة","بورسعيد","السويس","الأقصر","الزقازيق",
           "المنصورة","طنطا","الفيوم","المنيا","الإسماعيلية","قنا","سوهاج","أسيوط","أسوان",
           "دمياط","بني سويف","الغردقة","الرياض","جدة","مكة المكرمة","المدينة المنورة","الدمام",
           "بغداد","البصرة","الموصل","كربلاء","النجف","دمشق","حلب","بيروت","عمان","الكويت"],
    "he": ["ירושלים","תל אביב","חיפה","ראשון לציון","פתח תקווה","אשדוד","נתניה","באר שבע",
           "בני ברק","רמת גן","חולון","בת ים","מודיעין","כפר סבא","אשקלון","הרצליה","חדרה",
           "רעננה","ראש העין","לוד","עפולה","נהריה","רמת השרון","קריית גת","אילת","טבריה",
           "עכו","נצרת","דימונה","קריית ביאליק","קריית אתא","אור יהודה","יבנה","גבעתיים"],
    "zh": ["北京","上海","广州","深圳","重庆","天津","成都","武汉","西安","杭州","沈阳","郑州",
           "南京","济南","哈尔滨","青岛","苏州","长春","昆明","大连","厦门","无锡","宁波",
           "福州","南宁","合肥","南昌","贵阳","乌鲁木齐","石家庄","长沙","太原","兰州",
           "呼和浩特","海口","银川","西宁","拉萨","香港","澳门","台北","高雄","台中"],
}

COLORS_BY_LANG = {
    "en": ["black","white","silver","gray","red","blue","green","yellow","orange","purple","pink","brown","gold","navy","teal","charcoal","ivory","beige","crimson","indigo"],
    "fr": ["noir","blanc","argent","gris","rouge","bleu","vert","jaune","orange","violet","rose","brun","or","marine","sarcelle","anthracite","ivoire","beige","cramoisi","indigo"],
    "de": ["schwarz","weiß","silber","grau","rot","blau","grün","gelb","orange","lila","rosa","braun","gold","marineblau","türkis","anthrazit","elfenbein","beige","karmesin","indigo"],
    "es": ["negro","blanco","plateado","gris","rojo","azul","verde","amarillo","naranja","morado","rosa","marrón","dorado","azul marino","verde azulado","gris carbón","marfil","beige","carmesí","índigo"],
    "ru": ["чёрный","белый","серебряный","серый","красный","синий","зелёный","жёлтый","оранжевый","фиолетовый","розовый","коричневый","золотой","тёмно-синий","бирюзовый","антрацит","слоновая кость","бежевый","малиновый","индиго"],
    "ar": ["أسود","أبيض","فضي","رمادي","أحمر","أزرق","أخضر","أصفر","برتقالي","بنفسجي","وردي","بني","ذهبي","كحلي","أزرق مائي","رمادي داكن","عاجي","بيج","قرمزي","نيلي"],
    "he": ["שחור","לבן","כסוף","אפור","אדום","כחול","ירוק","צהוב","כתום","סגול","ורוד","חום","זהב","נייבי","טורקיז","פחם","שנהב","בז","ארגמן","אינדיגו"],
    "zh": ["黑色","白色","银色","灰色","红色","蓝色","绿色","黄色","橙色","紫色","粉红色","棕色","金色","海军蓝","青色","炭灰色","象牙色","米色","深红色","靛蓝色"],
}

# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def fetch_bytes(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": "elastic-lab-testdata/1.0"})
    for attempt in range(5):
        try:
            with urllib.request.urlopen(req, timeout=30) as r:
                return r.read()
        except urllib.error.HTTPError as e:
            if e.code == 429:
                wait = 2 ** attempt * 5  # 5, 10, 20, 40, 80 seconds
                print(f"    rate-limited, waiting {wait}s …", file=sys.stderr)
                time.sleep(wait)
            else:
                raise
    raise RuntimeError(f"Failed after retries: {url}")


def fetch_text(url: str) -> str:
    raw = fetch_bytes(url)
    for enc in ("utf-8", "utf-8-sig", "latin-1"):
        try:
            return raw.decode(enc)
        except UnicodeDecodeError:
            continue
    return raw.decode("utf-8", errors="replace")


def fetch_wikipedia_extract(url: str) -> str:
    data = json.loads(fetch_bytes(url))
    pages = data.get("query", {}).get("pages", {})
    for page in pages.values():
        return page.get("extract", "")
    return ""

# ---------------------------------------------------------------------------
# Text processing
# ---------------------------------------------------------------------------

def strip_gutenberg_header_footer(text: str) -> str:
    start = re.search(r"\*\*\* ?START OF (THE|THIS) PROJECT GUTENBERG", text, re.IGNORECASE)
    end = re.search(r"\*\*\* ?END OF (THE|THIS) PROJECT GUTENBERG", text, re.IGNORECASE)
    if start:
        text = text[start.end():]
    if end:
        text = text[:end.start()]
    return text


def strip_html(text: str) -> str:
    text = re.sub(r"<[^>]+>", " ", text)
    return re.sub(r"\s{2,}", " ", text)


def extract_sentences(text: str) -> list[str]:
    parts = re.split(r"(?<=[.!?؟।])\s+|(?<=[。！？])", text)
    result = []
    for part in parts:
        s = " ".join(part.split())
        if MIN_SENTENCE_LEN <= len(s) <= MAX_SENTENCE_LEN and not re.search(r"https?://", s) and not s.isupper():
            result.append(s)
    return result

# ---------------------------------------------------------------------------
# Sentences
# ---------------------------------------------------------------------------

def generate_sentences() -> None:
    all_sentences: list[str] = []

    for lang, url in GUTENBERG_SENTENCES:
        print(f"  sentences/{lang} from Gutenberg …")
        try:
            text = strip_gutenberg_header_footer(fetch_text(url))
            sentences = extract_sentences(text)
            _write(f"sentences_{lang}.csv", sentences[:SENTENCES_PER_LANG])
            all_sentences.extend(sentences[:SENTENCES_PER_LANG])
            time.sleep(1)
        except Exception as e:
            print(f"    ERROR: {e}", file=sys.stderr)

    for lang, url in WIKIPEDIA_SENTENCES:
        print(f"  sentences/{lang} from Wikipedia …")
        try:
            text = strip_html(fetch_wikipedia_extract(url))
            sentences = extract_sentences(text)
            _write(f"sentences_{lang}.csv", sentences[:SENTENCES_PER_LANG])
            all_sentences.extend(sentences[:SENTENCES_PER_LANG])
            time.sleep(1)
        except Exception as e:
            print(f"    ERROR: {e}", file=sys.stderr)

    random.shuffle(all_sentences)
    _write("sentences_multilang.csv", all_sentences)

# ---------------------------------------------------------------------------
# Words
# ---------------------------------------------------------------------------

def fetch_wikipedia_random_titles(lang: str, want: int) -> list[str]:
    collected: list[str] = []
    bad = re.compile(r"[:()\[\]/|]|\d{4}")
    for _ in range(4):  # up to 4 batches of 500
        if len(collected) >= want * 2:
            break
        url = f"https://{lang}.wikipedia.org/w/api.php?action=query&list=random&rnnamespace=0&rnlimit=500&format=json"
        data = json.loads(fetch_bytes(url))
        for item in data.get("query", {}).get("random", []):
            title = item.get("title", "").strip()
            if MIN_WORD_LEN <= len(title) <= MAX_WORD_LEN and not bad.search(title):
                collected.append(title)
        time.sleep(2)
    seen: set[str] = set()
    unique = [t for t in collected if not (t in seen or seen.add(t))]  # type: ignore[func-returns-value]
    return unique


def generate_words() -> None:
    all_words: list[str] = []
    for lang in LANG_CODES:
        print(f"  words/{lang} from Wikipedia random titles …")
        try:
            titles = fetch_wikipedia_random_titles(lang, WORDS_PER_LANG)
            if len(titles) < 20:
                print(f"    WARNING: only {len(titles)} words for {lang}")
            _write(f"words_{lang}.csv", titles[:WORDS_PER_LANG])
            all_words.extend(titles[:WORDS_PER_LANG])
        except Exception as e:
            print(f"    ERROR: {e}", file=sys.stderr)
    random.shuffle(all_words)
    _write("words_multilang.csv", all_words)

# ---------------------------------------------------------------------------
# Cities
# ---------------------------------------------------------------------------

def generate_cities() -> None:
    all_cities: list[str] = []
    for lang, city_list in CITIES_BY_LANG.items():
        _write(f"cities_{lang}.csv", city_list)
        all_cities.extend(city_list)
    random.shuffle(all_cities)
    _write("cities_multilang.csv", all_cities)
    print(f"  cities written for {list(CITIES_BY_LANG.keys())} + multilang")

# ---------------------------------------------------------------------------
# Colors
# ---------------------------------------------------------------------------

def generate_colors() -> None:
    all_colors: list[str] = []
    for lang, color_list in COLORS_BY_LANG.items():
        _write(f"colors_{lang}.csv", color_list)
        all_colors.extend(color_list)
    random.shuffle(all_colors)
    _write("colors_multilang.csv", all_colors)
    print(f"  colors written for {list(COLORS_BY_LANG.keys())} + multilang")

# ---------------------------------------------------------------------------
# Utilities
# ---------------------------------------------------------------------------

def _write(filename: str, lines: list[str]) -> None:
    path = OUT_DIR / filename
    path.write_text("\n".join(lines), encoding="utf-8")
    print(f"    wrote {len(lines)} entries -> {path.name}")


def main() -> None:
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    print("=== Sentences ===")
    generate_sentences()

    print("\n=== Words ===")
    generate_words()

    print("\n=== Cities ===")
    generate_cities()

    print("\n=== Colors ===")
    generate_colors()

    print("\nDone.")


if __name__ == "__main__":
    main()
