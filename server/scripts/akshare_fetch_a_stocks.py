import contextlib
import io
import json
import re
import sys

import akshare as ak


def normalize_code(value):
    if value is None:
        return None
    text = str(value).strip()
    match = re.search(r"(\d{6})$", text)
    return match.group(1) if match else None


def detect_exchange(code):
    if not code:
        return "UNKNOWN", "UNKNOWN"
    if code.startswith("6") or code.startswith("688"):
        return "SSE", "SH"
    if code.startswith("0") or code.startswith("3"):
        return "SZSE", "SZ"
    if code.startswith("4") or code.startswith("8") or code.startswith("92"):
        return "BSE", "BJ"
    return "UNKNOWN", "UNKNOWN"


def main():
    with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
        base_df = ak.stock_info_a_code_name()
        spot_df = ak.stock_zh_a_spot()

    name_by_code = {}
    for _, row in base_df.iterrows():
        code = normalize_code(row.get("code"))
        name = str(row.get("name")).strip() if row.get("name") is not None else ""
        if code and name:
            name_by_code[code] = name

    quotes_by_code = {}
    for _, row in spot_df.iterrows():
        code = normalize_code(row.get("代码"))
        if not code:
            continue
        quotes_by_code[code] = {
            "latestPrice": None if row.get("最新价") is None else float(row.get("最新价")),
            "changePercent": None if row.get("涨跌幅") is None else float(row.get("涨跌幅")),
        }

    result = []
    for code, name in name_by_code.items():
        exchange_code, market = detect_exchange(code)
        quote = quotes_by_code.get(code, {})
        result.append(
            {
                "code": code,
                "name": name,
                "exchangeCode": exchange_code,
                "market": market,
                "industry": None,
                "listDate": None,
                "latestPrice": quote.get("latestPrice"),
                "changePercent": quote.get("changePercent"),
            }
        )

    sys.stdout.write(json.dumps(result, ensure_ascii=False))


if __name__ == "__main__":
    main()
