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
# Streets
# ---------------------------------------------------------------------------

STREETS_BY_LANG = {
    "en": ["Oak Street","Maple Avenue","Cedar Lane","Pine Road","Elm Drive","Main Street",
           "Church Road","High Street","Station Road","Park Avenue","River Road","Hill Street",
           "Green Lane","Bridge Street","Mill Road","Victoria Road","King Street","Queen Street",
           "Market Street","London Road","North Street","South Street","East Road",
           "Collins Street","Swanston Street","Grafton Street","O'Connell Street",
           "Dundas Street","Yonge Street"],
    "fr": ["Rue de Rivoli","Avenue des Champs-Élysées","Boulevard Saint-Germain",
           "Rue du Faubourg Saint-Honoré","Avenue Victor Hugo","Rue de la République",
           "Boulevard Haussmann","Rue Saint-Denis","Avenue de l'Opéra","Rue Montmartre",
           "Boulevard des Capucines","Rue de la Paix","Avenue de Breteuil","Rue du Commerce",
           "Boulevard de la Bastille","Avenue Jean Jaurès","Rue Beaubourg","Boulevard Voltaire",
           "Rue de Charonne","Avenue de la Nation","Rue de Sèvres","Avenue de Wagram",
           "Boulevard Raspail"],
    "de": ["Hauptstraße","Bahnhofstraße","Kirchstraße","Gartenstraße","Schillerstraße",
           "Goethestraße","Friedrichstraße","Wilhelmstraße","Bismarckstraße","Kantstraße",
           "Unter den Linden","Kurfürstendamm","Jungfernstieg","Mönckebergstraße",
           "Theatinerstraße","Maximilianstraße","Königstraße","Schadowstraße",
           "Kaiserstraße","Rathausplatz","Marktplatz","Schloßstraße","Ringstraße"],
    "es": ["Calle Mayor","Gran Vía","Paseo de la Castellana","Calle de Alcalá",
           "Avenida de la Constitución","Calle de Serrano","Paseo del Prado",
           "Calle Marqués de Larios","Avenida Diagonal","Calle de Goya",
           "Calle de Fuencarral","Paseo de Gracia","Calle de Hortaleza",
           "Avenida de América","Calle de Toledo","Calle del Príncipe",
           "Calle de la Paz","Avenida de Andalucía","Rambla de Catalunya","Calle Nueva"],
    "ru": ["ул. Тверская","пр. Невский","ул. Арбат","ул. Ленина","пр. Мира",
           "ул. Садовая","ул. Советская","пр. Кирова","ул. Горького","ул. Гагарина",
           "ул. Пушкина","пр. Победы","ул. Молодёжная","ул. Центральная",
           "пр. Строителей","ул. Школьная","пр. Комсомольский","ул. Рабочая",
           "ул. Революции","ул. Мира"],
    "ar": ["شارع التحرير","شارع الجمهورية","شارع رمسيس","شارع صلاح سالم","شارع الهرم",
           "شارع الملك فيصل","شارع عبد الناصر","شارع بورسعيد","شارع المعز","شارع ابن خلدون",
           "شارع الملك عبدالعزيز","شارع الأمير محمد","شارع العروبة","شارع الملك فهد",
           "شارع الأمير سلطان","شارع الستين","شارع المدينة المنورة","شارع الوزراء",
           "شارع الخليج","شارع السلام"],
    "he": ["רחוב הרצל","רחוב ויצמן","רחוב בן גוריון","רחוב ז'בוטינסקי","רחוב יהודה המכבי",
           "רחוב הנביאים","רחוב שלמה המלך","רחוב המלך ג'ורג'","רחוב דיזנגוף","רחוב ביאליק",
           "רחוב רוטשילד","שדרות ירושלים","רחוב הגליל","רחוב הנשיא","שדרות בן גוריון",
           "רחוב כצנלסון","רחוב פינסקר",'רחוב רמב"ם',"רחוב הבנים","רחוב העצמאות"],
    "zh": ["长安街","南京路","解放路","人民路","中山路","建国路","新华路","和平路",
           "建设路","文化路","兴华路","振兴路","胜利路","幸福路","前进路",
           "团结路","民主路","光明路","友谊路","劳动路"],
}

def generate_streets() -> None:
    all_streets: list[str] = []
    for lang, street_list in STREETS_BY_LANG.items():
        _write(f"streets_{lang}.csv", street_list)
        all_streets.extend(street_list)
    random.shuffle(all_streets)
    _write("streets_multilang.csv", all_streets)
    print(f"  streets written for {list(STREETS_BY_LANG.keys())} + multilang")

# ---------------------------------------------------------------------------
# Postal codes
# ---------------------------------------------------------------------------

ZIP_CODES_BY_LANG = {
    "en": ["10001","10036","20001","30303","33101","60601","77002","90210","94102","98101",
           "02101","07030","11201","19103","22202","28202","37201","44101","46201","48201",
           "55401","63101","70112","78701","85001",
           "SW1A 1AA","EC1A 1BB","W1A 0AA","M1 1AE","B1 1BB","LS1 1BA","EH1 1AA","CF10 1EP"],
    "fr": ["75001","75008","75016","75018","69001","69007","13001","13008","06000","31000",
           "33000","67000","59000","44000","35000","76000","34000","29200","21000","51100",
           "54000","57000","38000","42000","64000"],
    "de": ["10115","10178","20095","20457","80331","80636","50667","60311","70173","40213",
           "44137","04109","28195","01067","30159","90402","47051","45127","99084","68159",
           "76131","55116","97070","66111","24103"],
    "es": ["28001","28013","08001","08025","46001","41001","50001","29001","30001","07001",
           "03001","14001","47001","36001","33001","48001","20001","18001","15001","37001",
           "02001","23001","39001","04001","22001"],
    "ru": ["101000","190000","630001","620014","603000","420111","454000","644000","443001",
           "344006","450001","660000","614001","400005","394006","664003","680000","163000",
           "460000","656049","690001","150000","385000","248001","305000"],
    "ar": ["11511","11521","21514","42111","41111","65111","83111","85511","61111","71111",
           "31111","35511","51111","11321","12312",
           "12271","21451","11564","13311","21361","24211","14211","11793","31611","41411"],
    "he": ["6100001","6473101","3190500","9777401","8401004","4350101","7670101","5710001",
           "5900001","7100001","4101001","2101001","1025500","7400001","6900001","4250001",
           "2600001","7030000","9308001","3080001","4000001","8400001","5200001","2308001","3780001"],
    "zh": ["100000","100010","100020","200000","200010","510000","510050","518000","518050",
           "400000","400010","300000","300050","610000","610050","420100","710000","310000",
           "210000","430100","250000","150000","130000","450000","230000"],
}

def generate_zip_codes() -> None:
    all_zips: list[str] = []
    for lang, zip_list in ZIP_CODES_BY_LANG.items():
        _write(f"zip_codes_{lang}.csv", zip_list)
        all_zips.extend(zip_list)
    random.shuffle(all_zips)
    _write("zip_codes_multilang.csv", all_zips)
    print(f"  zip_codes written for {list(ZIP_CODES_BY_LANG.keys())} + multilang")

# ---------------------------------------------------------------------------
# Country codes
# ---------------------------------------------------------------------------

COUNTRY_CODES_BY_LANG = {
    "en": ["US","US","US","US","US","GB","GB","GB","AU","AU","CA","CA","NZ","IE","ZA","SG"],
    "fr": ["FR","FR","FR","FR","FR","FR","BE","BE","CH","CH","LU","MC","SN","CI","MG","CM","MA","TN","DZ"],
    "de": ["DE","DE","DE","DE","DE","DE","AT","AT","AT","CH","CH","LU","LI"],
    "es": ["ES","ES","ES","ES","ES","MX","MX","AR","AR","CL","CO","PE","VE","EC","UY","PY","BO","CU","DO","GT"],
    "ru": ["RU","RU","RU","RU","RU","RU","BY","BY","KZ","KZ","UA","AM","GE","AZ","MD","KG","TJ","UZ"],
    "ar": ["EG","EG","EG","SA","SA","AE","AE","JO","LB","IQ","KW","BH","QA","OM",
           "MA","MA","DZ","DZ","TN","LY","SD","YE","PS","SY"],
    "he": ["IL","IL","IL","IL","IL","IL","IL","IL","IL","IL","IL","IL","US","GB"],
    "zh": ["CN","CN","CN","CN","CN","CN","CN","CN","CN","TW","TW","HK","HK","SG","SG","MY"],
}

def generate_country_codes() -> None:
    all_codes: list[str] = []
    seen: set[str] = set()
    for lang, code_list in COUNTRY_CODES_BY_LANG.items():
        _write(f"country_codes_{lang}.csv", code_list)
        for c in code_list:
            if c not in seen:
                all_codes.append(c)
                seen.add(c)
    _write("country_codes_multilang.csv", all_codes)
    print(f"  country_codes written for {list(COUNTRY_CODES_BY_LANG.keys())} + multilang")

# ---------------------------------------------------------------------------
# Currencies
# ---------------------------------------------------------------------------

CURRENCIES_BY_LANG = {
    "en": ["USD","USD","USD","USD","GBP","GBP","AUD","CAD","NZD","ZAR","SGD"],
    "fr": ["EUR","EUR","EUR","EUR","EUR","CHF","CHF","MAD","TND","XOF"],
    "de": ["EUR","EUR","EUR","EUR","EUR","CHF","CHF","HUF","CZK","PLN"],
    "es": ["EUR","EUR","EUR","MXN","MXN","ARS","CLP","COP","PEN","UYU"],
    "ru": ["RUB","RUB","RUB","RUB","KZT","BYN","UAH","AMD","GEL"],
    "ar": ["EGP","EGP","SAR","SAR","AED","AED","KWD","BHD","QAR","OMR","JOD","LBP","MAD","DZD","TND"],
    "he": ["ILS","ILS","ILS","ILS","ILS","USD","USD","EUR"],
    "zh": ["CNY","CNY","CNY","CNY","CNY","HKD","HKD","TWD","SGD","MYR"],
}

def generate_currencies() -> None:
    all_currencies: list[str] = []
    seen: set[str] = set()
    for lang, currency_list in CURRENCIES_BY_LANG.items():
        _write(f"currencies_{lang}.csv", currency_list)
        for c in currency_list:
            if c not in seen:
                all_currencies.append(c)
                seen.add(c)
    _write("currencies_multilang.csv", all_currencies)
    print(f"  currencies written for {list(CURRENCIES_BY_LANG.keys())} + multilang")

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

    print("\n=== Streets ===")
    generate_streets()

    print("\n=== Postal codes ===")
    generate_zip_codes()

    print("\n=== Country codes ===")
    generate_country_codes()

    print("\n=== Currencies ===")
    generate_currencies()

    print("\nDone.")


if __name__ == "__main__":
    main()
