"""30日分のダミーデータを指定セッションに投入するワンショットスクリプト。

使い方: python seed_dummy_30days.py <session_id> [<session_id> ...]
"""
import datetime
import random
import sys

import sqlite3

DB_PATH = "discovery.db"

SIGNAL_DOMAINS = ["tech", "art", "music", "sports", "science", "social", "making", "business"]
ACTION_TYPES = ["search", "view", "save", "share", "create", "like", "comment"]
SOURCES = ["search_history", "browsing_history", "app_usage", "hobby", "conversation"]

CONTENT_TEMPLATES = {
    "tech": ["Pythonの自動化スクリプトを調べた", "ロボット工作の動画を見た", "アプリのUIデザインを保存した"],
    "art": ["水彩画の技法を検索した", "美術館の展示を調べた", "イラストをシェアした"],
    "music": ["新しいバンドの曲を保存した", "作曲アプリを触ってみた", "ライブ映像を見た"],
    "sports": ["バスケの練習動画を見た", "スポーツニュースを検索した"],
    "science": ["宇宙のドキュメンタリーを見た", "実験動画を保存した"],
    "social": ["友達とボランティア活動について話した", "地域イベントを調べた"],
    "making": ["3Dプリンタの作品例を見た", "DIY家具の作り方を検索した"],
    "business": ["起業家のインタビューを見た", "お小遣い管理アプリを調べた"],
}

random.seed(42)


def rand_time_on_day(day: datetime.date) -> datetime.datetime:
    h = random.randint(7, 22)
    m = random.randint(0, 59)
    s = random.randint(0, 59)
    return datetime.datetime.combine(
        day, datetime.time(h, m, s), tzinfo=datetime.timezone.utc
    )


def fmt(dt: datetime.datetime) -> str:
    return dt.strftime("%Y-%m-%d %H:%M:%S.%f")


def seed_session(conn: sqlite3.Connection, session_id: int) -> None:
    cur = conn.cursor()
    now = datetime.datetime.now(datetime.timezone.utc)
    today = now.date()

    # --- 1. InterestSignal: 30日分、tech/artを多め、natureは意図的に0件（矛盾検出用） ---
    domain_weights = {
        "tech": 5, "art": 4, "making": 3, "music": 2,
        "science": 2, "sports": 1, "social": 1, "business": 1,
    }
    weighted_domains = [d for d, w in domain_weights.items() for _ in range(w)]

    signal_rows = []
    for offset in range(29, -1, -1):
        day = today - datetime.timedelta(days=offset)
        n_signals = random.randint(2, 4)
        for _ in range(n_signals):
            domain = random.choice(weighted_domains)
            occurred = rand_time_on_day(day)
            content = random.choice(CONTENT_TEMPLATES[domain])
            signal_rows.append((
                session_id,
                random.choice(ACTION_TYPES),
                domain,
                content,
                random.choice(SOURCES),
                fmt(occurred),
                fmt(occurred),
            ))
    cur.executemany(
        """INSERT INTO interest_signal
           (session_id, action_type, domain, content_summary, source, occurred_at, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?)""",
        signal_rows,
    )

    # --- 2. Experiment + ExperimentResult ---
    experiment_rows = []  # (created_at, domain, planned, status, selected_at, started_at, completed_at, skipped_at, actual_minutes)
    for offset in range(28, -1, -4):
        day = today - datetime.timedelta(days=offset)
        created = rand_time_on_day(day)
        domain = random.choice(["tech", "art", "making", "music", "science"])
        planned = random.choice([5, 10, 15])
        actual = int(planned * random.uniform(0.9, 1.3))
        experiment_rows.append({
            "created_at": created, "domain": domain, "planned": planned,
            "status": "completed", "actual": actual,
            "rating": (random.randint(3, 5), random.randint(3, 5), random.randint(3, 5), round(random.uniform(0.5, 0.85), 2)),
        })

    # tech ドメインを DIVE_CANDIDATE 条件（平均4.0以上・confidence0.70以上・1.5倍超過1件）で満たす
    for i in range(3):
        day = today - datetime.timedelta(days=20 - i * 3)
        created = rand_time_on_day(day)
        planned = 10
        actual = int(planned * (1.6 if i == 0 else 1.1))
        experiment_rows.append({
            "created_at": created, "domain": "tech", "planned": planned,
            "status": "completed", "actual": actual,
            "rating": (5, 5, 4, 0.8),
        })

    # nature ドメイン: 高評価だが signal は一切ないので矛盾（discrepancy）を発火させる
    day = today - datetime.timedelta(days=6)
    created = rand_time_on_day(day)
    experiment_rows.append({
        "created_at": created, "domain": "nature", "planned": 10,
        "status": "completed", "actual": 11,
        "rating": (5, 5, 4, 0.75),
    })

    # スキップされた実験も数件
    for offset in (25, 17, 9):
        day = today - datetime.timedelta(days=offset)
        created = rand_time_on_day(day)
        experiment_rows.append({
            "created_at": created, "domain": random.choice(["sports", "business", "social"]),
            "planned": 10, "status": "skipped", "actual": None, "rating": None,
        })

    for row in experiment_rows:
        created = row["created_at"]
        title = f"{row['domain']}の探索実験"
        description = f"{row['domain']}分野の小さな行動実験"
        selected_at = started_at = completed_at = skipped_at = None
        actual_minutes = row["actual"]

        if row["status"] == "completed":
            selected_at = created + datetime.timedelta(minutes=1)
            started_at = created + datetime.timedelta(minutes=5)
            completed_at = started_at + datetime.timedelta(minutes=actual_minutes)
        elif row["status"] == "skipped":
            selected_at = created + datetime.timedelta(minutes=1)
            skipped_at = created + datetime.timedelta(minutes=30)

        cur.execute(
            """INSERT INTO experiment
               (session_id, title, description, domain, planned_minutes, status,
                selected_at, started_at, completed_at, skipped_at,
                selection_note, skip_reason, actual_minutes, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
            (
                session_id, title, description, row["domain"], row["planned"], row["status"],
                fmt(selected_at) if selected_at else None,
                fmt(started_at) if started_at else None,
                fmt(completed_at) if completed_at else None,
                fmt(skipped_at) if skipped_at else None,
                "試してみたい" if row["status"] == "completed" else None,
                "時間がなかった" if row["status"] == "skipped" else None,
                actual_minutes,
                fmt(created),
            ),
        )
        experiment_id = cur.lastrowid

        if row["status"] == "completed" and row["rating"]:
            enjoyment, curiosity, retry_intent, confidence = row["rating"]
            cur.execute(
                """INSERT INTO experiment_result
                   (experiment_id, enjoyment, curiosity, retry_intent, confidence, reflection, created_at)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                (
                    experiment_id, enjoyment, curiosity, retry_intent, confidence,
                    "楽しかった、また試したい",
                    fmt(completed_at),
                ),
            )

    # --- 3. Evidence: 週ごとにドメイン別サマリー（4週分） ---
    for week in range(4):
        day = today - datetime.timedelta(days=28 - week * 7)
        created = rand_time_on_day(day)
        for domain, categories in [
            ("tech", {"EXPLORE": 0.6, "ANALYZE": 0.4}),
            ("art", {"CREATE": 0.7, "PRACTICE": 0.3}),
            ("making", {"CREATE": 0.5, "IMPROVE": 0.5}),
        ]:
            cur.execute(
                """INSERT INTO evidence
                   (session_id, domain, signal_count, summary_text, behavior_categories, created_at)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                (
                    session_id, domain, random.randint(3, 8),
                    f"{domain}分野への関心が継続的に見られる（{week + 1}週目）",
                    __import__("json").dumps(categories),
                    fmt(created),
                ),
            )

    # --- 4. UserReflection: 隔日程度 ---
    for offset in range(28, -1, -3):
        day = today - datetime.timedelta(days=offset)
        created = rand_time_on_day(day)
        cur.execute(
            """INSERT INTO user_reflection (session_id, content, mood, created_at)
               VALUES (?, ?, ?, ?)""",
            (
                session_id,
                random.choice([
                    "今日は新しいことに挑戦できて楽しかった",
                    "少し疲れたけど、興味のあることを調べられた",
                    "友達と話して新しい発見があった",
                ]),
                random.randint(3, 5),
                fmt(created),
            ),
        )

    conn.commit()
    print(f"session_id={session_id}: signals={len(signal_rows)}, experiments={len(experiment_rows)} 件投入完了")


def main() -> None:
    if len(sys.argv) < 2:
        print("usage: python seed_dummy_30days.py <session_id> [<session_id> ...]")
        sys.exit(1)

    session_ids = [int(x) for x in sys.argv[1:]]
    conn = sqlite3.connect(DB_PATH)
    try:
        for sid in session_ids:
            seed_session(conn, sid)
    finally:
        conn.close()


if __name__ == "__main__":
    main()
