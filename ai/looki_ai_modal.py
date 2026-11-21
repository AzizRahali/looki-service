"""Looki AI model, served on Modal.

One small model loaded once on one GPU, used by three endpoints:

* ``parse``: turns a natural-language search (English, French or Tunisian
  Arabic) into structured filters. The backend validates them and searches
  the database itself.
* ``insights``: writes a short summary and suggestions for a salon owner from
  numbers the backend already computed. It never sees client data, and the
  backend rejects any text that contains a number not in those facts.
* ``support``: answers help questions using only the help articles the backend
  sends. The backend checks that the answer cites real articles and contains
  no numbers, emails or links that are not in them.

Deploy:  modal deploy ai/looki_ai_modal.py
Requires a Modal secret named "looki-ai" containing API_TOKEN.
"""

import hmac
import json
import os
import re

import modal

MODEL_ID = "Qwen/Qwen3-4B-Instruct-2507"
MAX_QUERY_LENGTH = 300
MAX_FACTS = 30
MAX_FACT_LENGTH = 300

app = modal.App("looki-ai")

image = (
    modal.Image.debian_slim(python_version="3.11")
    .pip_install(
        "torch==2.7.1",
        "transformers==4.55.4",
        "accelerate==1.10.0",
        "fastapi[standard]==0.116.1",
    )
    .env({"HF_HOME": "/cache/huggingface"})
)

model_cache = modal.Volume.from_name("looki-model-cache", create_if_missing=True)

SYSTEM_PROMPT = """You extract salon search filters from a user's query.
Queries may be in English, French, Tunisian Arabic (Arabic script or Latin \
"arabizi" such as "nheb n9os chaari ghodwa"), or a mix.

Reply with ONE JSON object and nothing else, using exactly these keys:
{"service": string|null, "category": string|null, "location": string|null,
 "maxPrice": number|null, "day": string|null, "time": string|null}

Rules:
- "service": the closest entry from SERVICES, copied exactly, only if the user
  clearly asks for that specific service. If the service depends on gender
  (e.g. men's vs women's haircut) and the gender is not stated, use null.
- "category": the entry from CATEGORIES that the request belongs to, copied
  exactly. Otherwise null.
- "location": the city or neighbourhood mentioned, written in Latin script as
  commonly spelled in Tunisia (e.g. "Tunis", "La Marsa", "Sousse"). Otherwise null.
- "maxPrice": the maximum budget as a number in Tunisian dinars, only if the
  user states a budget or upper limit. Otherwise null.
- "day": one of "today", "tomorrow", "day_after_tomorrow", "monday", "tuesday",
  "wednesday", "thursday", "friday", "saturday", "sunday", or an explicit date
  as "YYYY-MM-DD". Otherwise null.
- "time": an exact time as 24-hour "HH:MM", or one of "morning", "afternoon",
  "evening", only if the user mentions a time. Otherwise null.
- Never invent information that is not in the query. Use null when unsure.

Tunisian Arabic vocabulary (Arabic script / arabizi):
- lyoum, el youm, اليوم = today; ghodwa, ghodwe, غدوة = tomorrow;
  ba3d ghodwa, بعد غدوة = day_after_tomorrow.
- sbe7, الصباح = morning; la3chiya, العشية = afternoon; lil, fel lil, الليل = evening.
- n9os, nqoss, نقص = cut; cha3r, cha3ri, شعر = hair; 7jem, حجم = haircut (men);
  7wejeb, حواجب = eyebrows; lahya, لحية = beard; ma9 / mani, مانيكير = manicure.
- dinar, dt, د = Tunisian dinars; a9al men, أقل من, ta7t = under.

Tunisian places in Arabic script (always a location, even if the word has
another meaning): تونس = Tunis, المرسى = La Marsa, قرطاج = Carthage,
المنزه = El Menzah, أريانة = Ariana, بن عروس = Ben Arous, سوسة = Sousse,
الحمامات = Hammamet, نابل = Nabeul, المنستير = Monastir, صفاقس = Sfax,
بنزرت = Bizerte, جربة = Djerba, القيروان = Kairouan, قابس = Gabes."""

EXAMPLES = [
    (
        "women's haircut in La Marsa tomorrow afternoon under 40 dinars",
        {"service": "Haircut (Women's)", "category": "Hair", "location": "La Marsa",
         "maxPrice": 40, "day": "tomorrow", "time": "afternoon"},
    ),
    (
        "manucure à Sousse samedi vers 15h",
        {"service": "Manicure", "category": "Nails", "location": "Sousse",
         "maxPrice": None, "day": "saturday", "time": "15:00"},
    ),
    (
        "nheb na3mel barbe f tunis lyoum",
        {"service": "Beard Trim", "category": "Beard & Shave", "location": "Tunis",
         "maxPrice": None, "day": "today", "time": None},
    ),
    (
        "nheb n9os cha3ri ghodwa la3chiya",
        {"service": None, "category": "Hair", "location": None,
         "maxPrice": None, "day": "tomorrow", "time": "afternoon"},
    ),
    (
        "massage relaxant à Nabeul dimanche",
        {"service": None, "category": "Massage", "location": "Nabeul",
         "maxPrice": None, "day": "sunday", "time": None},
    ),
    (
        "نحب نقص شعري في صفاقس",
        {"service": None, "category": "Hair", "location": "Sfax",
         "maxPrice": None, "day": None, "time": None},
    ),
    (
        "haircut for men in Bizerte at 18:30",
        {"service": "Haircut (Men's)", "category": "Hair", "location": "Bizerte",
         "maxPrice": None, "day": None, "time": "18:30"},
    ),
    (
        "نحب نعمل ماساج في سوسة بأقل من 80 دينار",
        {"service": None, "category": "Massage", "location": "Sousse",
         "maxPrice": 80, "day": None, "time": None},
    ),
]

RESULT_KEYS = ("service", "category", "location", "maxPrice", "day", "time")

INSIGHT_LANGUAGES = {
    "en": "English",
    "fr": "French",
    "ar": "Tunisian Arabic (Derja), written in Arabic script",
}

INSIGHTS_PROMPT = """You are a business advisor for a beauty salon in Tunisia.
You receive FACTS about the salon's last 28 days, computed from its bookings.
Write in English for the salon owner, addressing them directly.

Reply in exactly this format, with nothing before or after:
SUMMARY: <at most 2 short sentences on the most important trends>
SUGGESTION: <one short, concrete action>
SUGGESTION: <one short, concrete action>
SUGGESTION: <optional third action>

Choose suggestions only from these, and only when the matching fact is present:
- Quietest open time: suggest a promotion or offer for that exact day and time.
- Busiest time: suggest making sure enough staff are available then.
- No-shows or cancellations: suggest reminders before appointments.
- Most booked services: suggest highlighting them in the salon profile or photos.
- Returning clients: suggest a loyalty reward if few clients return,
  or thanking loyal clients if many return.

Strict rules:
- Use ONLY the facts. Never invent numbers, prices, discount amounts, names,
  days, times, or reasons. Do not explain why something changed.
- Any number you write must be copied exactly from the facts, using Western
  digits (0-9). Prefer fewer numbers over more.
- Keep service names exactly as they appear in the facts.
- No greetings, no markdown, no emojis, no JSON.

Example (for English):
FACTS:
- Bookings: 40 (previous period: 50), down 20%.
- No-shows: 6 (18% of appointments that were due).
- Quietest open time: Wednesday morning (about 0.5 bookings per week).
- Most booked services: Manicure (15 bookings).
Answer:
SUMMARY: Bookings are down 20% compared with the previous period, and 18% of due appointments were no-shows.
SUGGESTION: Send clients a reminder the day before their appointment to reduce no-shows.
SUGGESTION: Run an offer for Wednesday mornings, your quietest time.
SUGGESTION: Feature Manicure prominently in your salon profile and photos."""

INSIGHTS_REQUEST = "FACTS:\n{facts}"

TRANSLATE_PROMPT = """Translate each line into {language}.
Rules:
- Output exactly one translated line for each input line, in the same order,
  and nothing else.
- Keep every number, "%" and "TND" exactly as written, with Western digits (0-9).
- Keep service names exactly as written (for example "Haircut (Women's)").
- Translate app button, tab and status names using exactly these terms, and
  use them for other words too: {glossary}."""

GLOSSARIES = {
    "fr": '"no-shows" = "absences", "cancellations" = "annulations", '
          '"bookings" = "réservations", "revenue" = "chiffre d\'affaires", '
          '"reliability score" = "score de fiabilité", "waitlist" = "liste d\'attente", '
          '"late cancellation" = "annulation tardive", "reschedule" = "reprogrammer"',
    "ar": '"no-shows" = "الغيابات", "cancellations" = "الإلغاءات", '
          '"bookings" = "الحجوزات", "revenue" = "المداخيل", '
          '"reliability score" = "نقاط الموثوقية", "waitlist" = "قائمة الانتظار", '
          '"cancelling" = "الإلغاء", "late cancellation" = "إلغاء متأخر", '
          '"reschedule" = "تبديل الوقت"',
}

SUPPORT_PROMPT = """You are the support assistant of Looki, a salon booking app in Tunisia.
Answer the user's latest question using ONLY the HELP ARTICLES below.

Reply in exactly this format, with nothing before or after:
SOURCES: <comma-separated ids of the articles you used, for example book, cancel>
ANSWER: <your answer in English>

If the articles do not answer the question, or the message is not about using
Looki, reply exactly:
SOURCES: NONE
ANSWER: NONE

Rules:
- Use only facts written in the articles. Never invent or guess numbers, times,
  prices, fees, refunds, rules, buttons, screens or contact details.
- Include every relevant fact from the articles that answers the question.
  Never say something is impossible, unavailable or has no solution unless an
  article says so explicitly.
- Be short: at most 5 sentences, or a few steps, each step on its own line
  starting with "1.", "2.", and so on.
- You cannot see the user's account, bookings or payments, and you cannot
  change anything. If asked about their own booking or account, explain how
  they can check or do it in the app, using the articles.
- The user may write in English, French or Tunisian Arabic (Arabic script or
  Latin "arabizi"). Always write the ANSWER in English. Common Tunisian words:
  nfasa5 / fasakh / فسّخ / نفسخ = cancel or delete; nbaddel / بدّل = change;
  na7jez / نحجز = book; nkhallas / خلّص = pay; rendez-vous / موعد = appointment;
  ma najjamch / ما نجمش = I can't; 3lech / علاش = why; kifech / كيفاش = how.
- The latest question may refer to earlier messages (for example "it" or
  "that"). Work out what it refers to, then answer it from the articles.
- Use button and tab names exactly as written in the articles.
- No greetings, no markdown headings, no emojis."""

MAX_ARTICLES = 40
MAX_ARTICLES_LENGTH = 20_000
MAX_SUPPORT_MESSAGES = 8
MAX_SUPPORT_MESSAGE_LENGTH = 500
MAX_TERMS = 100

_SOURCES_PREFIX = re.compile(r"^\W*SOURCES?\s*:\s*", re.IGNORECASE)
_ANSWER_PREFIX = re.compile(r"^\W*ANSWER\s*:\s*", re.IGNORECASE)


def _parse_support(text: str) -> tuple[list[str], str | None]:
    """Returns (source ids, answer); answer is None when the format is wrong.

    An empty source list means "no answer". The "ANSWER:" label is optional:
    everything after the SOURCES line is the answer.
    """
    lines = text.strip().splitlines()
    for index, line in enumerate(lines):
        if _SOURCES_PREFIX.match(line):
            value = _SOURCES_PREFIX.sub("", line).strip()
            sources = [] if value.upper().startswith("NONE") else [
                s.strip().strip("[]") for s in value.split(",") if s.strip()]
            answer = "\n".join(lines[index + 1:]).strip()
            answer = _ANSWER_PREFIX.sub("", answer, count=1).strip()
            break
    else:
        return [], None
    if not sources or not answer or answer.upper() == "NONE":
        return [], answer or "NONE"
    return sources, answer

_BULLET = re.compile(r"^\s*[-•*]\s*")


def _translated_lines(text: str) -> list[str]:
    return [_BULLET.sub("", line).strip() for line in text.splitlines() if line.strip()]

_SUMMARY_PREFIX = re.compile(r"^\W*SUMMARY\s*:\s*", re.IGNORECASE)
_SUGGESTION_PREFIX = re.compile(r"^\W*SUGGESTION\s*\d*\s*:\s*", re.IGNORECASE)


def _parse_insights(text: str) -> tuple[str | None, list[str]]:
    summary = None
    suggestions = []
    for line in text.splitlines():
        line = line.strip()
        if _SUMMARY_PREFIX.match(line) and summary is None:
            summary = _SUMMARY_PREFIX.sub("", line).strip() or None
        elif _SUGGESTION_PREFIX.match(line):
            suggestion = _SUGGESTION_PREFIX.sub("", line).strip()
            if suggestion:
                suggestions.append(suggestion)
    return summary, suggestions


def _check_token(body: dict):
    from fastapi import HTTPException

    expected = os.environ.get("API_TOKEN", "")
    provided = str(body.get("token", ""))
    if not expected or not hmac.compare_digest(provided, expected):
        raise HTTPException(status_code=401, detail="Unauthorized")


def _extract_json(text: str) -> dict:
    match = re.search(r"\{.*\}", text, re.DOTALL)
    if not match:
        return {}
    try:
        parsed = json.loads(match.group(0))
    except json.JSONDecodeError:
        return {}
    return parsed if isinstance(parsed, dict) else {}


@app.cls(
    image=image,
    gpu="L4",
    volumes={"/cache": model_cache},
    secrets=[modal.Secret.from_name("looki-ai")],
    # Cost controls: one container at most, released after 5 idle minutes.
    max_containers=1,
    scaledown_window=300,
    timeout=120,
)
class LookiModel:
    @modal.enter()
    def load_model(self):
        import torch
        from transformers import AutoModelForCausalLM, AutoTokenizer

        self.tokenizer = AutoTokenizer.from_pretrained(MODEL_ID)
        self.model = AutoModelForCausalLM.from_pretrained(
            MODEL_ID, torch_dtype=torch.bfloat16, device_map="cuda"
        )
        self.model.eval()
        model_cache.commit()

    def _messages(self, query: str, categories: list[str], services: list[str]):
        catalog = (
            f"CATEGORIES: {json.dumps(categories, ensure_ascii=False)}\n"
            f"SERVICES: {json.dumps(services, ensure_ascii=False)}"
        )
        messages = [{"role": "system", "content": f"{SYSTEM_PROMPT}\n\n{catalog}"}]
        for example_query, example_answer in EXAMPLES:
            messages.append({"role": "user", "content": example_query})
            messages.append(
                {"role": "assistant",
                 "content": json.dumps(example_answer, ensure_ascii=False)}
            )
        messages.append({"role": "user", "content": query})
        return messages

    def _generate(self, messages, max_new_tokens: int = 160) -> str:
        import torch

        inputs = self.tokenizer.apply_chat_template(
            messages, add_generation_prompt=True, return_tensors="pt", return_dict=True
        ).to(self.model.device)
        with torch.inference_mode():
            output = self.model.generate(
                **inputs,
                max_new_tokens=max_new_tokens,
                do_sample=False,
                pad_token_id=self.tokenizer.eos_token_id,
            )
        return self.tokenizer.decode(
            output[0][inputs["input_ids"].shape[-1]:], skip_special_tokens=True
        )

    @modal.fastapi_endpoint(method="POST")
    def parse(self, body: dict):
        from fastapi import HTTPException

        _check_token(body)

        query = str(body.get("query", "")).strip()
        if not query or len(query) > MAX_QUERY_LENGTH:
            raise HTTPException(status_code=400, detail="Invalid query")

        categories = [str(c) for c in body.get("categories", [])][:50]
        services = [str(s) for s in body.get("services", [])][:200]

        raw = _extract_json(self._generate(self._messages(query, categories, services)))
        result = {key: raw.get(key) for key in RESULT_KEYS}
        result["parsed"] = bool(raw)
        return result

    @modal.fastapi_endpoint(method="POST")
    def insights(self, body: dict):
        from fastapi import HTTPException

        _check_token(body)

        language = INSIGHT_LANGUAGES.get(str(body.get("language", "en")))
        facts = body.get("facts")
        if (
            language is None
            or not isinstance(facts, list)
            or not 0 < len(facts) <= MAX_FACTS
            or any(not isinstance(f, str) or len(f) > MAX_FACT_LENGTH for f in facts)
        ):
            raise HTTPException(status_code=400, detail="Invalid facts")

        messages = [
            {"role": "system", "content": INSIGHTS_PROMPT},
            {"role": "user", "content": INSIGHTS_REQUEST.format(
                facts="\n".join(f"- {f}" for f in facts))},
        ]
        summary, suggestions = _parse_insights(self._generate(messages, max_new_tokens=400))
        if summary is None and not suggestions:
            return {"summary": None, "suggestions": [], "parsed": False}

        # Writing in English and then translating is more reliable for a small
        # model than writing directly in French or Tunisian Arabic.
        code = str(body.get("language"))
        if code != "en":
            lines = ([summary] if summary else []) + suggestions
            translated = self._translate(lines, code)
            if translated is None:
                return {"summary": None, "suggestions": [], "parsed": False}
            if summary:
                summary, suggestions = translated[0], translated[1:]
            else:
                suggestions = translated
        return {"summary": summary, "suggestions": suggestions, "parsed": True}

    def _translate(self, lines: list[str], code: str,
                   terms: dict[str, str] | None = None) -> list[str] | None:
        """Translates line by line; returns None if the line count changed."""
        glossary = GLOSSARIES[code]
        if terms:
            glossary += ", " + ", ".join(f'"{en}" = "{local}"' for en, local in terms.items())
        translation = [
            {"role": "system", "content": TRANSLATE_PROMPT.format(
                language=INSIGHT_LANGUAGES[code], glossary=glossary)},
            {"role": "user", "content": "\n".join(lines)},
        ]
        translated = _translated_lines(self._generate(translation, max_new_tokens=600))
        return translated if len(translated) == len(lines) else None

    @modal.fastapi_endpoint(method="POST")
    def support(self, body: dict):
        from fastapi import HTTPException

        _check_token(body)

        code = str(body.get("language", "en"))
        articles = body.get("articles")
        messages = body.get("messages")
        if (
            code not in INSIGHT_LANGUAGES
            or not isinstance(articles, list)
            or not 0 < len(articles) <= MAX_ARTICLES
            or not isinstance(messages, list)
            or not 0 < len(messages) <= MAX_SUPPORT_MESSAGES
        ):
            raise HTTPException(status_code=400, detail="Invalid request")
        try:
            article_text = "\n\n".join(
                f"[{a['id']}] {a['title']}\n{a['body']}" for a in articles)
            chat = [(m["role"], str(m["text"])[:MAX_SUPPORT_MESSAGE_LENGTH])
                    for m in messages if m["role"] in ("user", "assistant")]
        except (KeyError, TypeError):
            raise HTTPException(status_code=400, detail="Invalid request")
        if not chat or chat[-1][0] != "user" or len(article_text) > MAX_ARTICLES_LENGTH:
            raise HTTPException(status_code=400, detail="Invalid request")

        # Earlier turns go in as context inside one message: a small model
        # otherwise copies the plain style of earlier answers and drops the format.
        question = chat[-1][1]
        earlier = "\n".join(
            f"{'User' if role == 'user' else 'Assistant'}: {text}" for role, text in chat[:-1])
        request = (f"CONVERSATION SO FAR (only to understand what the question refers to):\n"
                   f"{earlier}\n\nLATEST QUESTION:\n{question}\n\n"
                   f"Reply in the required format (SOURCES: ... then ANSWER: ...)."
                   if earlier else question)
        prompt = [
            {"role": "system", "content": SUPPORT_PROMPT + "\n\nHELP ARTICLES:\n" + article_text},
            {"role": "user", "content": request},
        ]
        sources, answer = _parse_support(self._generate(prompt, max_new_tokens=350))
        if answer is None:
            return {"answer": None, "sources": [], "answered": False, "parsed": False}
        if not sources:
            return {"answer": None, "sources": [], "answered": False, "parsed": True}

        if code != "en":
            lines = [line for line in answer.splitlines() if line.strip()]
            terms = body.get("terms") if isinstance(body.get("terms"), dict) else {}
            terms = {str(k)[:80]: str(v)[:80] for k, v in list(terms.items())[:MAX_TERMS]}
            translated = self._translate(lines, code, terms)
            if translated is None:
                return {"answer": None, "sources": [], "answered": False, "parsed": False}
            answer = "\n".join(translated)
        return {"answer": answer, "sources": sources, "answered": True, "parsed": True}
