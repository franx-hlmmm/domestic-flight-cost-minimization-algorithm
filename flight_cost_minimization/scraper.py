#!/usr/bin/env python3
"""
Scraper tiket pesawat Traveloka untuk proyek optimasi rute penerbangan.

Versi ini disesuaikan dengan struktur Traveloka terbaru yang memakai:
1. inner scroll container,
2. kartu penerbangan dengan data-testid flight-inventory-card-container-*,
3. harga aktual dengan data-testid label_fl_inventory_price.

Contoh:
    python scraper.py AMQ CGK 2026-07-20 --dry-run
    python scraper.py AMQ CGK 2026-07-20 --headless --max-per-route 3
"""

from __future__ import annotations

import argparse
import datetime as dt
import logging
import os
import re
import sys
import time
from dataclasses import dataclass
from typing import Iterable, List, Optional, Sequence, Set, Tuple
from urllib.parse import urlencode

import mysql.connector
from mysql.connector import Error as MySQLError
import undetected_chromedriver as uc
from selenium.common.exceptions import StaleElementReferenceException, TimeoutException, WebDriverException
from selenium.webdriver.common.by import By
from selenium.webdriver.support.ui import WebDriverWait

LOGGER = logging.getLogger("flight_scraper")

TRAVELOKA_BASE_URL = "https://www.traveloka.com/en-id/flight/fullsearch"
DEFAULT_DOMESTIC_HUBS = ("CGK", "SUB", "UPG", "KNO", "DPS")
DEFAULT_INTERNATIONAL_HUBS = ("KUL", "SIN")

CARD_SELECTOR = "div[data-testid^='flight-inventory-card-container-']"
PRICE_SELECTOR = "[data-testid='label_fl_inventory_price']"

FIND_SCROLL_CONTAINER_JS = r"""
const resultRoot = document.querySelector('#flight-search-result') || document.querySelector('[data-testid="view_flight_section_list"]');

function isScrollable(el) {
  if (!el) return false;
  const style = window.getComputedStyle(el);
  return (style.overflowY === 'auto' || style.overflowY === 'scroll') && el.scrollHeight > el.clientHeight + 40;
}

if (resultRoot) {
  let node = resultRoot;
  while (node && node !== document.body && node !== document.documentElement) {
    if (isScrollable(node)) return node;
    node = node.parentElement;
  }
}

const candidates = Array.from(document.querySelectorAll('div'))
  .filter(isScrollable)
  .filter(el => !el.querySelector('[data-testid="flight-search-sidebar-filter"]'))
  .sort((a, b) => (b.scrollHeight - b.clientHeight) - (a.scrollHeight - a.clientHeight));

return candidates[0] || document.scrollingElement || document.documentElement;
"""

SCROLL_INNER_JS = r"""
const el = arguments[0];
const y = arguments[1];

if (el === document.scrollingElement || el === document.documentElement || el === document.body) {
  window.scrollTo(0, y);
} else {
  el.scrollTop = y;
}

return {
  scrollTop: el.scrollTop || window.scrollY,
  scrollHeight: el.scrollHeight || document.documentElement.scrollHeight,
  clientHeight: el.clientHeight || window.innerHeight
};
"""

GET_SCROLL_STATE_JS = r"""
const el = arguments[0];

return {
  scrollTop: el.scrollTop || window.scrollY,
  scrollHeight: el.scrollHeight || document.documentElement.scrollHeight,
  clientHeight: el.clientHeight || window.innerHeight
};
"""


@dataclass(frozen=True)
class Flight:
    asal: str
    tujuan: str
    tanggal: str
    maskapai: str
    waktu_berangkat: str
    waktu_tiba: str
    harga: int

    def db_tuple(self) -> Tuple[str, str, str, str, str, str, int]:
        return (
            self.asal,
            self.tujuan,
            self.tanggal,
            self.maskapai,
            self.waktu_berangkat,
            self.waktu_tiba,
            self.harga,
        )


def configure_logging(verbose: bool = False) -> None:
    logging.basicConfig(
        level=logging.DEBUG if verbose else logging.INFO,
        format="%(asctime)s | %(levelname)s | %(message)s",
        datefmt="%H:%M:%S",
    )


def validate_airport_code(value: str) -> str:
    code = value.strip().upper()

    if not re.fullmatch(r"[A-Z]{3}", code):
        raise argparse.ArgumentTypeError(
            f"Kode bandara tidak valid: {value}. Gunakan kode IATA 3 huruf, contoh CGK."
        )

    return code


def validate_date(value: str) -> str:
    try:
        dt.datetime.strptime(value, "%Y-%m-%d")
    except ValueError as exc:
        raise argparse.ArgumentTypeError(
            "Tanggal harus berformat YYYY-MM-DD, contoh 2026-07-20."
        ) from exc

    return value


def get_traveloka_url(asal: str, tujuan: str, tanggal: str) -> str:
    date_obj = dt.datetime.strptime(tanggal, "%Y-%m-%d")

    params = {
        "ap": f"{asal}.{tujuan}",
        "dt": f"{date_obj.strftime('%d-%m-%Y')}.NA",
        "ps": "1.0.0",
        "sc": "ECONOMY",
    }

    return f"{TRAVELOKA_BASE_URL}?{urlencode(params)}"


def build_routes(
    asal: str,
    tujuan: str,
    domestic_hubs: Sequence[str] = DEFAULT_DOMESTIC_HUBS,
    international_hubs: Sequence[str] = DEFAULT_INTERNATIONAL_HUBS,
) -> List[Tuple[str, str]]:
    routes: Set[Tuple[str, str]] = {(asal, tujuan)}
    domestic_set = set(domestic_hubs)

    for int_hub in international_hubs:
        routes.add((asal, int_hub))
        routes.add((int_hub, tujuan))

        if asal not in domestic_set:
            for domestic_hub in domestic_hubs:
                routes.add((asal, domestic_hub))
                routes.add((domestic_hub, int_hub))

        if tujuan not in domestic_set:
            for domestic_hub in domestic_hubs:
                routes.add((int_hub, domestic_hub))
                routes.add((domestic_hub, tujuan))

    return sorted((a, b) for a, b in routes if a != b)


def create_driver(headless: bool = False) -> uc.Chrome:
    options = uc.ChromeOptions()
    options.add_argument("--window-size=1536,864")
    options.add_argument("--disable-notifications")
    options.add_argument("--lang=en-ID")
    options.add_argument("--no-first-run")
    options.add_argument("--no-default-browser-check")

    if headless:
        options.add_argument("--headless=new")

    chrome_version = os.getenv("CHROME_VERSION")

    if chrome_version:
        LOGGER.info("Menggunakan Chrome major version dari CHROME_VERSION=%s", chrome_version)
        return uc.Chrome(version_main=int(chrome_version), options=options)

    return uc.Chrome(options=options)


def wait_document_ready(driver: uc.Chrome, timeout: int) -> None:
    WebDriverWait(driver, timeout).until(
        lambda current_driver: current_driver.execute_script("return document.readyState") == "complete"
    )


def close_optional_popups(driver: uc.Chrome) -> None:
    button_xpaths = (
        "//button[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'accept')]",
        "//button[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'terima')]",
        "//button[contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'setuju')]",
        "//*[@role='button' and contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'ok')]",
        "//*[@role='button' and contains(translate(., 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), 'nanti')]",
    )

    for xpath in button_xpaths:
        try:
            for element in driver.find_elements(By.XPATH, xpath)[:3]:
                if element.is_displayed() and element.is_enabled():
                    element.click()
                    time.sleep(0.2)
        except WebDriverException:
            continue


def find_scroll_container(driver: uc.Chrome):
    return driver.execute_script(FIND_SCROLL_CONTAINER_JS)


def scroll_inner(driver: uc.Chrome, container, y: int) -> dict:
    return driver.execute_script(SCROLL_INNER_JS, container, y)


def get_scroll_state(driver: uc.Chrome, container) -> dict:
    return driver.execute_script(GET_SCROLL_STATE_JS, container)


def wait_until_results_stable(driver: uc.Chrome, timeout: int) -> None:
    def condition(current_driver):
        cards = current_driver.find_elements(By.CSS_SELECTOR, CARD_SELECTOR)

        if any(card.is_displayed() for card in cards):
            return True

        body_text = current_driver.find_element(By.TAG_NAME, "body").text.lower()

        empty_keywords = (
            "no matching flights",
            "no flight matches",
            "tidak ada penerbangan",
            "tidak ditemukan",
            "sold out",
        )

        return any(keyword in body_text for keyword in empty_keywords)

    WebDriverWait(driver, timeout).until(condition)


def normalize_time(value: str) -> str:
    return value.replace(".", ":")


def parse_price(text: str) -> Optional[int]:
    match = re.search(r"(?:Rp|IDR)\s*([0-9][0-9.,]*)", text, flags=re.IGNORECASE)

    if not match:
        return None

    digits = re.sub(r"\D", "", match.group(1))

    return int(digits) if digits else None


def parse_airline(card_text: str, asal: str, tujuan: str) -> str:
    ignore_keywords = (
        "kg",
        "rp",
        "idr",
        "pax",
        "choose",
        "flight details",
        "fare",
        "benefits",
        "refund",
        "reschedule",
        "promos",
        "direct",
        "stop",
        "transit",
        asal.lower(),
        tujuan.lower(),
    )

    lines = [line.strip() for line in card_text.splitlines() if line.strip()]

    for line in lines[:12]:
        lower_line = line.lower()

        if any(keyword in lower_line for keyword in ignore_keywords):
            continue

        if re.search(r"\b(?:[01][0-9]|2[0-3])[:.][0-5][0-9]\b", line):
            continue

        if re.fullmatch(r"[A-Z]{3}", line):
            continue

        if re.fullmatch(r"\+?\d+", line):
            continue

        if 2 <= len(line) <= 60:
            return line[:60]

    return "UNKNOWN"


def parse_flight_card(card, asal: str, tujuan: str, tanggal: str) -> Optional[Flight]:
    try:
        card_text = card.text

        if not card_text.strip():
            return None

        price_nodes = card.find_elements(By.CSS_SELECTOR, PRICE_SELECTOR)

        if not price_nodes:
            LOGGER.debug(
                "Kartu dilewati karena tidak memiliki label harga aktual: %s",
                card_text[:120].replace("\n", " | "),
            )
            return None

        price_text = price_nodes[0].text
        harga = parse_price(price_text)
        times = re.findall(r"\b(?:[01][0-9]|2[0-3])[:.][0-5][0-9]\b", card_text)

        if harga is None or len(times) < 2:
            LOGGER.debug(
                "Kartu dilewati karena data harga atau waktu tidak lengkap: %s",
                card_text[:160].replace("\n", " | "),
            )
            return None

        return Flight(
            asal=asal,
            tujuan=tujuan,
            tanggal=tanggal,
            maskapai=parse_airline(card_text, asal, tujuan),
            waktu_berangkat=normalize_time(times[0]),
            waktu_tiba=normalize_time(times[1]),
            harga=harga,
        )

    except StaleElementReferenceException:
        return None
    except WebDriverException as exc:
        LOGGER.debug("Kartu dilewati karena error Selenium: %s", exc)
        return None


def collect_visible_flights(driver: uc.Chrome, asal: str, tujuan: str, tanggal: str) -> List[Flight]:
    flights: List[Flight] = []

    for card in driver.find_elements(By.CSS_SELECTOR, CARD_SELECTOR):
        try:
            if not card.is_displayed():
                continue
        except StaleElementReferenceException:
            continue

        flight = parse_flight_card(card, asal, tujuan, tanggal)

        if flight:
            flights.append(flight)

    return flights


def crawl_traveloka_flight(
    driver: uc.Chrome,
    asal: str,
    tujuan: str,
    tanggal: str,
    max_per_route: int = 3,
    timeout: int = 45,
    scroll_rounds: int = 6,
) -> List[Flight]:
    url = get_traveloka_url(asal, tujuan, tanggal)

    LOGGER.info("Membuka rute %s -> %s", asal, tujuan)

    try:
        driver.get(url)
        wait_document_ready(driver, timeout)
        close_optional_popups(driver)
        wait_until_results_stable(driver, timeout)
    except TimeoutException:
        LOGGER.warning("Data tiket rute %s -> %s belum muncul sampai timeout.", asal, tujuan)
        return []
    except WebDriverException as exc:
        LOGGER.warning("Gagal membuka rute %s -> %s: %s", asal, tujuan, exc)
        return []

    flights: List[Flight] = []
    seen: Set[Tuple[str, str, str, str, str, int]] = set()

    try:
        container = find_scroll_container(driver)
        scroll_inner(driver, container, 0)
        time.sleep(0.5)
    except WebDriverException:
        container = None

    for round_index in range(scroll_rounds):
        for flight in collect_visible_flights(driver, asal, tujuan, tanggal):
            key = (
                flight.asal,
                flight.tujuan,
                flight.maskapai,
                flight.waktu_berangkat,
                flight.waktu_tiba,
                flight.harga,
            )

            if key not in seen:
                seen.add(key)
                flights.append(flight)

                if len(flights) >= max_per_route:
                    LOGGER.info(
                        "Ditemukan %s jadwal valid untuk rute %s -> %s",
                        len(flights),
                        asal,
                        tujuan,
                    )
                    return flights

        if not container:
            break

        try:
            state = get_scroll_state(driver, container)
            next_y = int(state["scrollTop"] + max(300, state["clientHeight"] * 0.75))
            max_y = int(state["scrollHeight"] - state["clientHeight"])

            if next_y > max_y:
                break

            scroll_inner(driver, container, next_y)
            time.sleep(1.0 if round_index == 0 else 0.6)

        except WebDriverException:
            break

    LOGGER.info("Ditemukan %s jadwal valid untuk rute %s -> %s", len(flights), asal, tujuan)

    return flights[:max_per_route]


def get_db_config() -> dict:
    return {
        "host": os.getenv("DB_HOST", "localhost"),
        "port": int(os.getenv("DB_PORT", "3306")),
        "user": os.getenv("DB_USER", "root"),
        "password": os.getenv("DB_PASSWORD", ""),
        "database": os.getenv("DB_NAME", "db_penerbangan_asa"),
    }


def save_to_mysql(flights: Sequence[Flight]) -> int:
    if not flights:
        return 0

    insert_query = """
        INSERT INTO penerbangan
            (asal, tujuan, tanggal_penerbangan, maskapai, waktu_berangkat, waktu_tiba, harga)
        VALUES (%s, %s, %s, %s, %s, %s, %s)
    """

    delete_query = """
        DELETE FROM penerbangan
        WHERE asal = %s AND tujuan = %s AND tanggal_penerbangan = %s
    """

    asal = flights[0].asal
    tujuan = flights[0].tujuan
    tanggal = flights[0].tanggal

    conn = None
    cursor = None

    try:
        conn = mysql.connector.connect(**get_db_config())
        cursor = conn.cursor()

        cursor.execute(delete_query, (asal, tujuan, tanggal))
        cursor.executemany(insert_query, [flight.db_tuple() for flight in flights])

        conn.commit()

        return len(flights)

    except MySQLError as exc:
        if conn:
            conn.rollback()

        LOGGER.error("Gagal menyimpan rute %s -> %s ke MySQL: %s", asal, tujuan, exc)

        return 0

    finally:
        if cursor:
            cursor.close()

        if conn and conn.is_connected():
            conn.close()


def print_dry_run(flights: Iterable[Flight]) -> None:
    for flight in flights:
        price = f"Rp {flight.harga:,}".replace(",", ".")

        print(
            f"{flight.asal}->{flight.tujuan} | "
            f"{flight.tanggal} | "
            f"{flight.maskapai} | "
            f"{flight.waktu_berangkat}-{flight.waktu_tiba} | "
            f"{price}"
        )


def parse_args(argv: Optional[Sequence[str]] = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Scraper data penerbangan untuk aplikasi optimasi rute."
    )

    parser.add_argument("asal", type=validate_airport_code, help="Kode IATA bandara asal, contoh AMQ.")
    parser.add_argument("tujuan", type=validate_airport_code, help="Kode IATA bandara tujuan, contoh CGK.")
    parser.add_argument("tanggal", type=validate_date, help="Tanggal penerbangan format YYYY-MM-DD.")

    parser.add_argument("--max-per-route", type=int, default=3, help="Jumlah maksimum jadwal yang disimpan per rute.")
    parser.add_argument("--timeout", type=int, default=45, help="Batas tunggu halaman Selenium dalam detik.")
    parser.add_argument("--delay", type=float, default=2.0, help="Jeda antar rute dalam detik.")
    parser.add_argument("--scroll-rounds", type=int, default=6, help="Jumlah percobaan scroll inner container per rute.")
    parser.add_argument("--headless", action="store_true", help="Jalankan Chrome tanpa tampilan GUI.")
    parser.add_argument("--dry-run", action="store_true", help="Tampilkan hasil tanpa menyimpan ke database.")
    parser.add_argument("--verbose", action="store_true", help="Tampilkan log debug.")

    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv)
    configure_logging(args.verbose)

    routes = build_routes(args.asal, args.tujuan)

    LOGGER.info("Menjalankan scraping untuk %s kombinasi rute.", len(routes))

    driver = None
    total_found = 0
    total_saved = 0

    try:
        driver = create_driver(headless=args.headless)

        for index, (asal, tujuan) in enumerate(routes, start=1):
            LOGGER.info("[%s/%s] Proses rute %s -> %s", index, len(routes), asal, tujuan)

            flights = crawl_traveloka_flight(
                driver=driver,
                asal=asal,
                tujuan=tujuan,
                tanggal=args.tanggal,
                max_per_route=args.max_per_route,
                timeout=args.timeout,
                scroll_rounds=args.scroll_rounds,
            )

            total_found += len(flights)

            if args.dry_run:
                print_dry_run(flights)
            else:
                saved = save_to_mysql(flights)
                total_saved += saved
                LOGGER.info("Tersimpan %s data untuk rute %s -> %s", saved, asal, tujuan)

            if index < len(routes):
                time.sleep(args.delay)

    except KeyboardInterrupt:
        LOGGER.warning("Proses dihentikan oleh pengguna.")
        return 130

    except Exception as exc:
        LOGGER.exception("Terjadi error tidak terduga: %s", exc)
        return 1

    finally:
        if driver:
            LOGGER.info("Menutup browser.")
            driver.quit()

    LOGGER.info("Selesai. Total ditemukan: %s. Total tersimpan: %s.", total_found, total_saved)

    return 0


if __name__ == "__main__":
    sys.exit(main())